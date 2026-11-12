package com.techasians.alertservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techasians.alertservice.entity.RptDefinition;
import com.techasians.alertservice.entity.RptExcelSheetDef;
import com.techasians.alertservice.enums.RptDirection;
import com.techasians.alertservice.enums.RptNodeType;
import com.techasians.alertservice.enums.RptSourceMode;
import com.techasians.alertservice.exception.GlobalExceptionHandler;
import com.techasians.alertservice.repository.RptDefinitionRepository;
import com.techasians.alertservice.repository.RptExcelSheetDefRepository;
import com.techasians.alertservice.service.*;
import com.techasians.alertservice.service.impl.OutputGenerationCoordinator;
import com.techasians.alertservice.service.impl.ReportImportServiceImpl;
import com.techasians.alertservice.service.impl.ReportImportTransactionService;
import com.techasians.alertservice.service.impl.ReportValidationServiceImpl;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReportImportFlowTest {
    private static final LocalDate WEEK = LocalDate.of(2026, 9, 7);
    private ReportFileService files;
    private ReportImportTransactionService transactions;
    private OutputGenerationCoordinator generation;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        RptDefinitionRepository definitions = mock(RptDefinitionRepository.class);
        RptExcelSheetDefRepository sheets = mock(RptExcelSheetDefRepository.class);
        when(definitions.findById(1010L)).thenReturn(Optional.of(new RptDefinition()
                .setId(1010L).setDirection(RptDirection.INPUT).setNodeType(RptNodeType.FILE)
                .setSourceMode(RptSourceMode.IMPORT).setAllowedExtension("xlsx").setStatus(1)));
        when(sheets.findByDefinitionIdAndStatusOrderBySortOrderAsc(1010L, 1))
                .thenReturn(Collections.singletonList(new RptExcelSheetDef().setSheetName("Dữ liệu bất cập")));
        files = mock(ReportFileService.class);
        transactions = mock(ReportImportTransactionService.class);
        generation = mock(OutputGenerationCoordinator.class);
        ReportImportService service = new ReportImportServiceImpl(definitions,
                new ReportValidationServiceImpl(sheets), files, transactions, generation);
        ReportController controller = new ReportController(mock(ReportQueryService.class), service,
                mock(ReportRejectService.class), mock(ReportDeleteService.class), mock(WordPreviewService.class), generation);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void returnsBase64AnnotatedWorkbookWithoutImportingInvalidRows() throws Exception {
        MvcResult response = mvc.perform(multipart("/api/reports/inputs/1010/import")
                        .file(upload("Ngày phát hiện", "", "31/02/2026", "07/09/2026"))
                        .param("weekStartDate", WEEK.toString()).param("actor", "tester"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Import có lỗi, kiểm tra file"))
                .andExpect(jsonPath("$.data.errorFile").isString())
                .andReturn();

        JsonNode body = new ObjectMapper().readTree(response.getResponse().getContentAsByteArray());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(body.get("data").get("errorFile").binaryValue()))) {
            Sheet sheet = workbook.getSheet("Dữ liệu bất cập");
            assertEquals("Kết quả", sheet.getRow(0).getCell(2).getStringCellValue());
            assertEquals(IndexedColors.RED.getIndex(), workbook.getFontAt(sheet.getRow(0).getCell(2).getCellStyle().getFontIndex()).getColor());
            assertEquals("Ngày phát hiện không được để trống.", sheet.getRow(1).getCell(2).getStringCellValue());
            assertTrue(sheet.getRow(2).getCell(2).getStringCellValue().contains("d/M/uuuu"));
            assertNull(sheet.getRow(3).getCell(2));
        }
        verifyNoInteractions(files, transactions, generation);
    }

    @Test
    void importsValidFileAndReturnsSuccessMessageWithoutErrorFile() throws Exception {
        MockMultipartFile upload = upload("Ngày phát hiện", "07/09/2026", "7/9/26");
        ReportImportTransactionService.ImportAllocation allocation = new ReportImportTransactionService.ImportAllocation(10L, 1);
        StoredReportFile stored = new StoredReportFile("input.xlsx", "report-object", upload.getSize(), "hash");
        when(transactions.prepare(1010L, WEEK, "tester")).thenReturn(allocation);
        when(files.upload(any(), eq(WEEK), eq(1010L), eq(10L), eq(1))).thenReturn(stored);

        mvc.perform(multipart("/api/reports/inputs/1010/import").file(upload)
                        .param("weekStartDate", WEEK.toString()).param("actor", "tester"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Import báo cáo thành công"))
                .andExpect(jsonPath("$.data.errorFile").doesNotExist());

        verify(transactions).commit(allocation, "tester", stored);
        verify(generation).prepareAndPublish(1010L, WEEK, "system");
        verify(files, never()).deleteIfExists(anyString());
    }

    @Test
    void keepsBadRequestForMissingHeaderAndDoesNotImport() throws Exception {
        mvc.perform(multipart("/api/reports/inputs/1010/import").file(upload("Sai header", "07/09/2026"))
                        .param("weekStartDate", WEEK.toString()).param("actor", "tester"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Sheet Dữ liệu bất cập thiếu header Ngày phát hiện"));

        verifyNoInteractions(files, transactions, generation);
    }

    private MockMultipartFile upload(String dateHeader, String... dates) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Dữ liệu bất cập");
            sheet.createRow(0).createCell(0).setCellValue(dateHeader);
            sheet.getRow(0).createCell(1).setCellValue("Nội dung");
            for (int index = 0; index < dates.length; index++) {
                sheet.createRow(index + 1).createCell(0).setCellValue(dates[index]);
                sheet.getRow(index + 1).createCell(1).setCellValue("Bất cập " + index);
            }
            workbook.write(output);
            return new MockMultipartFile("file", "input.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray());
        }
    }
}
