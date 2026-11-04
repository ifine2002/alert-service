package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.entity.RptExcelSheetDef;
import com.techasians.alertservice.exception.ReportFileValidationException;
import com.techasians.alertservice.repository.RptExcelSheetDefRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbookType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportValidationServiceImplTest {
    private static final LocalDate WEEK = LocalDate.of(2026, 9, 7);
    private final RptExcelSheetDefRepository sheets = mock(RptExcelSheetDefRepository.class);
    private final ReportValidationServiceImpl service = new ReportValidationServiceImpl(sheets);

    @Test
    void acceptsRequiredSheetsWithEmptyOrUnrelatedContentAndExtraSheets() throws Exception {
        configure("TH", "Bất cập DV");
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            book.createSheet("TH");
            book.createSheet("Bất cập DV").createRow(0).createCell(0).setCellValue("Không có header yêu cầu");
            book.createSheet("Sheet bổ sung");
            assertDoesNotThrow(() -> validate(book, "xlsx", WEEK));
        }
    }

    @Test
    void reportsAllMissingSheetsInConfigurationOrder() throws Exception {
        configure("TH", "Bất cập DV", "Trạm tuần");
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            book.createSheet("TH");
            ReportFileValidationException failure = assertThrows(ReportFileValidationException.class,
                    () -> validate(book, "xlsx", WEEK));
            assertEquals("File Excel thiếu các sheet bắt buộc: Bất cập DV, Trạm tuần", failure.getMessage());
        }
    }

    @Test
    void resolvesCurrentAndPreviousYearsFromEndOfCrossYearWeek() throws Exception {
        configure("Lũy kế lỗi phát sinh năm {yyyy}", "LPS năm {yyyy-1}");
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            book.createSheet("Lũy kế lỗi phát sinh năm 2027");
            book.createSheet("LPS năm 2026");
            assertDoesNotThrow(() -> validate(book, "xlsx", LocalDate.of(2026, 12, 28)));
        }
    }

    @Test
    void rejectsSheetsFromStartYearForCrossYearWeek() throws Exception {
        configure("Lũy kế lỗi phát sinh năm {yyyy}", "LPS năm {yyyy-1}");
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            book.createSheet("Lũy kế lỗi phát sinh năm 2026");
            book.createSheet("LPS năm 2025");
            assertEquals("File Excel thiếu các sheet bắt buộc: Lũy kế lỗi phát sinh năm 2027, LPS năm 2026",
                    assertThrows(ReportFileValidationException.class,
                            () -> validate(book, "xlsx", LocalDate.of(2026, 12, 28))).getMessage());
        }
    }

    @Test
    void rejectsMalformedWorkbookEvenWithoutRequiredSheets() throws Exception {
        configure();
        MockMultipartFile invalid = new MockMultipartFile("file", "data.xlsx", "application/octet-stream", "not Excel".getBytes());
        assertThrows(ReportFileValidationException.class, () -> service.validate(invalid, " XLSX ", 1102L, WEEK));
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            book.createSheet("Any");
            assertDoesNotThrow(() -> validate(book, " XLSX ", WEEK));
        }
    }

    @Test
    void checksRequiredSheetsForMacroEnabledWorkbook() throws Exception {
        configure("TH");
        try (XSSFWorkbook book = new XSSFWorkbook(XSSFWorkbookType.XLSM)) {
            book.createSheet("Other");
            assertThrows(ReportFileValidationException.class, () -> validate(book, "xlsm", WEEK));
            book.createSheet("TH");
            assertDoesNotThrow(() -> validate(book, "xlsm", WEEK));
        }
    }

    @Test
    void rejectsUnknownTokensAndResolvedSheetNameCollisions() throws Exception {
        configure("Năm {unknown}");
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            book.createSheet("TH");
            assertThrows(ReportFileValidationException.class, () -> validate(book, "xlsx", WEEK));
            configure("Năm {yyyy}", "Năm 2026");
            assertTrue(assertThrows(ReportFileValidationException.class, () -> validate(book, "xlsx", WEEK))
                    .getMessage().contains("Cấu hình trùng sheet"));
        }
    }

    private void configure(String... names) {
        List<RptExcelSheetDef> required = new ArrayList<>();
        for (String name : names) required.add(new RptExcelSheetDef().setSheetName(name));
        when(sheets.findByDefinitionIdAndStatusOrderBySortOrderAsc(1102L, 1)).thenReturn(required);
    }

    private void validate(XSSFWorkbook book, String extension, LocalDate week) throws Exception {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            book.write(bytes);
            MockMultipartFile upload = new MockMultipartFile("file", "input." + extension.trim(),
                    "application/octet-stream", bytes.toByteArray());
            service.validate(upload, extension, 1102L, week);
        }
    }
}
