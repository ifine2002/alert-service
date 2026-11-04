package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.entity.*;
import com.techasians.alertservice.exception.ReportFileValidationException;
import com.techasians.alertservice.repository.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportValidationServiceImplTest {
    private final RptExcelSheetDefRepository sheets = mock(RptExcelSheetDefRepository.class);
    private final RptExcelHeaderDefRepository headers = mock(RptExcelHeaderDefRepository.class);
    private final ReportValidationServiceImpl service = new ReportValidationServiceImpl(sheets, headers);
    private static final LocalDate WEEK = LocalDate.of(2026, 9, 7);

    @Test
    void findsHeadersAfterMovingRowsAndColumnsAndReorderingColumns() throws Exception {
        configure(Collections.singletonList(sheet(1L, "TH")), header(1L, "Ngày phát hiện"), header(1L, "Tiến độ"));
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            Sheet data = book.createSheet("TH");
            data.createRow(75).createCell(19).setCellValue("Tiến độ");
            data.createRow(77).createCell(24).setCellValue("Ngày phát hiện");
            book.createSheet("Sheet thừa");
            assertDoesNotThrow(() -> validate(book, "xlsx", WEEK));
        }
    }

    @Test
    void normalizesWhitespaceUnicodeAndCaseButRequiresWholeCellName() throws Exception {
        configure(Collections.singletonList(sheet(1L, "TH")), header(1L, "Ngày phát hiện"), header(1L, "Kết quả OK/NOK"));
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            Row row = book.createSheet("TH").createRow(0);
            row.createCell(0).setCellValue(Normalizer.normalize(" NGÀY\u00a0PHÁT HIỆN ", Normalizer.Form.NFD));
            row.createCell(1).setCellValue("Kết quả\r\nOK/NOK");
            assertDoesNotThrow(() -> validate(book, "xlsx", WEEK));
            row.getCell(0).setCellValue("Ghi chú về Ngày phát hiện");
            ReportFileValidationException failure = assertThrows(ReportFileValidationException.class, () -> validate(book, "xlsx", WEEK));
            assertTrue(failure.getMessage().contains("Ngày phát hiện"));
        }
    }

    @Test
    void findsMergedAndCachedFormulaHeaders() throws Exception {
        configure(Collections.singletonList(sheet(1L, "TH")), header(1L, "Ngày phát hiện"), header(1L, "Tiến độ"));
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            Sheet data = book.createSheet("TH");
            Row row = data.createRow(5);
            row.createCell(5).setCellValue("Ngày phát hiện");
            data.addMergedRegion(new CellRangeAddress(5, 6, 5, 8));
            row.createCell(10).setCellFormula("\"Tiến độ\"");
            book.getCreationHelper().createFormulaEvaluator().evaluateAll();
            assertDoesNotThrow(() -> validate(book, "xlsx", WEEK));
        }
    }

    @Test
    void reportsAllMissingHeadersAndMissingRequiredSheets() throws Exception {
        configure(Collections.singletonList(sheet(1L, "TH")), header(1L, "Ngày phát hiện"), header(1L, "Tiến độ"));
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            book.createSheet("TH").createRow(0).createCell(0).setCellValue("Không phải header");
            ReportFileValidationException failure = assertThrows(ReportFileValidationException.class, () -> validate(book, "xlsx", WEEK));
            assertTrue(failure.getMessage().contains("Ngày phát hiện, Tiến độ"));
        }
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            book.createSheet("Khác");
            assertTrue(assertThrows(ReportFileValidationException.class, () -> validate(book, "xlsx", WEEK))
                    .getMessage().contains("thiếu các sheet bắt buộc: TH"));
        }
    }

    @Test
    void resolvesCurrentAndPreviousYearUsingEndOfCrossYearWeek() throws Exception {
        configure(Arrays.asList(sheet(1L, "Lũy kế lỗi phát sinh năm {yyyy}"), sheet(2L, "LPS năm {yyyy-1}")),
                header(1L, "Ngày"), header(2L, "Ngày"));
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            book.createSheet("Lũy kế lỗi phát sinh năm 2027").createRow(0).createCell(0).setCellValue("Ngày");
            book.createSheet("LPS năm 2026").createRow(0).createCell(0).setCellValue("Ngày");
            assertDoesNotThrow(() -> validate(book, "xlsx", LocalDate.of(2026, 12, 28)));
        }
    }

    @Test
    void resolvesPreviousYearForWeekWithinSingleYear() throws Exception {
        configure(Collections.singletonList(sheet(1L, "LPS năm {yyyy-1}")), header(1L, "Ngày"));
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            book.createSheet("LPS năm 2025").createRow(0).createCell(0).setCellValue("Ngày");
            assertDoesNotThrow(() -> validate(book, "xlsx", WEEK));
        }
    }

    @Test
    void rejectsUnknownSheetTokensAndResolvedSheetNameCollisions() throws Exception {
        configure(Collections.singletonList(sheet(1L, "Năm {unknown}")));
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            book.createSheet("TH");
            assertThrows(ReportFileValidationException.class, () -> validate(book, "xlsx", WEEK));
            configure(Arrays.asList(sheet(1L, "Năm {yyyy}"), sheet(2L, "Năm 2026")));
            assertTrue(assertThrows(ReportFileValidationException.class, () -> validate(book, "xlsx", WEEK))
                    .getMessage().contains("Cấu hình trùng sheet"));
        }
    }

    @Test
    void rejectsNonExcelEvenWithoutSheetConfigurationOrWithWhitespaceInExtension() throws Exception {
        configure(Collections.emptyList());
        MockMultipartFile invalid = new MockMultipartFile("file", "data.xlsx", "application/octet-stream", "not Excel".getBytes());
        assertThrows(ReportFileValidationException.class, () -> service.validate(invalid, "xlsx", 1102L, WEEK));
        assertThrows(ReportFileValidationException.class, () -> service.validate(invalid, " xlsx ", 1102L, WEEK));
        verifyNoInteractions(headers);
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            book.createSheet("Any");
            assertDoesNotThrow(() -> validate(book, " XLSX ", WEEK));
        }
    }

    @Test
    void stillChecksHeadersWhenExtensionHasWhitespace() throws Exception {
        configure(Collections.singletonList(sheet(1L, "TH")), header(1L, "Ngày phát hiện"));
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            book.createSheet("TH");
            assertThrows(ReportFileValidationException.class, () -> validate(book, " XLSX ", WEEK));
        }
    }

    @Test
    void supportsMacroEnabledExcelWhenDefinitionAllowsXlsm() throws Exception {
        configure(Collections.singletonList(sheet(1L, "TH")), header(1L, "Ngày phát hiện"));
        try (XSSFWorkbook book = new XSSFWorkbook(org.apache.poi.xssf.usermodel.XSSFWorkbookType.XLSM)) {
            book.createSheet("TH").createRow(4).createCell(9).setCellValue("Ngày phát hiện");
            assertDoesNotThrow(() -> validate(book, "xlsm", WEEK));
        }
    }

    @Test
    void deduplicatesSameHeaderNameAndRejectsEmptyConfigurationNames() throws Exception {
        configure(Collections.singletonList(sheet(1L, "TH")), header(1L, "Ngày phát hiện"), header(1L, " NGÀY PHÁT HIỆN "));
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            book.createSheet("TH").createRow(0).createCell(0).setCellValue("Ngày phát hiện");
            assertDoesNotThrow(() -> validate(book, "xlsx", WEEK));
            configure(Collections.singletonList(sheet(1L, "TH")), header(1L, "  "));
            assertThrows(ReportFileValidationException.class, () -> validate(book, "xlsx", WEEK));
        }
    }

    private void configure(List<RptExcelSheetDef> definitions, RptExcelHeaderDef... requiredHeaders) {
        when(sheets.findByDefinitionIdAndStatusOrderBySortOrderAsc(1102L, 1)).thenReturn(definitions);
        when(headers.findBySheetDefIdInAndStatusOrderBySheetDefIdAscIdAsc(anyList(), eq(1)))
                .thenReturn(Arrays.asList(requiredHeaders));
    }

    private RptExcelSheetDef sheet(long id, String name) {
        return new RptExcelSheetDef().setId(id).setDefinitionId(1102L).setSheetName(name);
    }

    private RptExcelHeaderDef header(long sheet, String name) {
        return new RptExcelHeaderDef().setSheetDefId(sheet).setHeaderName(name);
    }

    private void validate(XSSFWorkbook book, String extension, LocalDate week) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        book.write(bytes);
        String filename = "test." + extension.trim().toLowerCase(Locale.ROOT);
        service.validate(new MockMultipartFile("file", filename, "application/octet-stream", bytes.toByteArray()), extension, 1102L, week);
    }
}
