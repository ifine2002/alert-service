package com.techasians.alertservice.service.impl;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class WordMobileFeedbackReaderTest {
    static final String SHEET = "PL02-So lieu TN&XL";
    static final LocalDate WEEK = LocalDate.of(2026, 9, 1);
    private final WordMobileFeedbackReader reader = new WordMobileFeedbackReader();

    @Test
    void readsWeekColumnsAndSelectsLargestIncreaseOnlyAmongSummaryGroups() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = table(book, WEEK, 3);
            // Các dịch vụ chi tiết bắt đầu lại STT 1, dù tỷ lệ cao hơn cũng không được chọn.
            service(sheet, 6, 3, 1, "Chi tiết không được chọn", 500, 499, 499);
            List<String> warnings = new ArrayList<>();
            WordMobileFeedbackReader.Result result = reader.read(book, SHEET, WEEK, warnings);
            assertEquals("1.722", result.getAverage());
            assertEquals("tăng 13,4%", result.getChange());
            assertEquals(" (+204PA/ngày)", result.getChangeDetails());
            assertEquals("Lỗi Kết nối", result.getTypicalService());
            assertEquals("tăng 6,8 lần", result.getTypicalChange());
            assertTrue(warnings.isEmpty(), warnings.toString());
        }
    }

    @Test
    void decreasingTotalSelectsLargestPercentageDecreaseNotLargestAbsoluteChangeOrIncrease() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = table(book, WEEK, 0);
            metric(sheet.getRow(2), 0, 120, -30, -0.2);
            service(sheet, 3, 0, 1, "Giảm nhiều PA", 200, -100, -0.333333);
            service(sheet, 4, 0, 2, "Lỗi Giảm nhiều phần trăm", 0, -5, -1);
            service(sheet, 5, 0, 3, "Tăng nhiều", 1000, 999, 999);
            WordMobileFeedbackReader.Result result = reader.read(book, SHEET, WEEK, new ArrayList<>());
            assertEquals("giảm 20%", result.getChange());
            assertEquals(" (-30PA/ngày)", result.getChangeDetails());
            assertEquals("giảm", result.getDirection());
            assertEquals("Lỗi Giảm nhiều phần trăm", result.getTypicalService());
            assertEquals("giảm 100%", result.getTypicalChange());
        }
    }

    @Test
    void tiesUseFirstGroupAndDirectionMustMatchTotal() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = table(book, WEEK, 0);
            service(sheet, 3, 0, 1, "Nhóm trước", 200, 100, 1);
            service(sheet, 4, 0, 2, "Nhóm sau", 400, 200, 1);
            service(sheet, 5, 0, 3, "Giảm mạnh", 0, -900, -1);
            WordMobileFeedbackReader.Result result = reader.read(book, SHEET, WEEK, new ArrayList<>());
            assertEquals("Lỗi Nhóm trước", result.getTypicalService());
            assertEquals("tăng 100%", result.getTypicalChange());
        }
    }

    @Test
    void unchangedTotalDoesNotInventTypicalTrend() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = table(book, WEEK, 0);
            metric(sheet.getRow(2), 0, 100, 0, 0);
            WordMobileFeedbackReader.Result result = reader.read(book, SHEET, WEEK, new ArrayList<>());
            assertTrue(result.isUnchanged());
            assertEquals("không có biến động", result.getChange());
            assertEquals("", result.getChangeDetails());
            assertFalse(result.hasTypicalService());
        }
    }

    @Test
    void usesTextNumbersAndCachedFormulasWithoutRecalculatingWorkbook() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = table(book, WEEK, 0);
            sheet.getRow(2).getCell(3).setCellValue("1.721,7");
            sheet.getRow(2).getCell(5).setCellValue("203,7");
            sheet.getRow(2).getCell(6).setCellValue("13,4%");
            sheet.getRow(4).getCell(6).setCellFormula("6.802");
            book.getCreationHelper().createFormulaEvaluator().evaluateAll();
            WordMobileFeedbackReader.Result result = reader.read(book, SHEET, WEEK, new ArrayList<>());
            assertEquals("1.722", result.getAverage());
            assertEquals("tăng 13,4%", result.getChange());
            assertEquals("tăng 6,8 lần", result.getTypicalChange());
        }
    }

    @Test
    void readsCachedFormulaHeadersServiceNamesAndOrderNumbers() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = table(book, WEEK, 0);
            sheet.getRow(0).getCell(1).setCellFormula("\"DỊCH VỤ\"");
            sheet.getRow(1).getCell(3).setCellFormula("\"TB tuần (01/09-07/09)\"");
            sheet.getRow(2).getCell(1).setCellFormula("\"Phản ánh báo hỏng dịch vụ\"");
            sheet.getRow(4).getCell(0).setCellFormula("1+1");
            sheet.getRow(4).getCell(1).setCellFormula("\"Kết nối\"");
            book.getCreationHelper().createFormulaEvaluator().evaluateAll();
            WordMobileFeedbackReader.Result result = reader.read(book, SHEET, WEEK, new ArrayList<>());
            assertEquals("1.722", result.getAverage());
            assertEquals("Lỗi Kết nối", result.getTypicalService());
            assertEquals("tăng 6,8 lần", result.getTypicalChange());
        }
    }

    @Test
    void skipsInvalidServiceRatesWithWarningsButKeepsTotalAndOtherCandidates() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = table(book, WEEK, 0);
            sheet.getRow(4).getCell(6).setCellErrorValue(FormulaError.DIV0.getCode());
            List<String> warnings = new ArrayList<>();
            WordMobileFeedbackReader.Result result = reader.read(book, SHEET, WEEK, warnings);
            assertEquals("1.722", result.getAverage());
            assertEquals("Lỗi Data", result.getTypicalService());
            assertEquals(1, warnings.size());
            sheet.getRow(3).getCell(6).setBlank();
            result = reader.read(book, SHEET, WEEK, new ArrayList<>());
            assertFalse(result.hasTypicalService());
        }
    }

    @Test
    void zeroChangeServiceWithUndefinedPercentageIsNotACandidateOrAnError() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = table(book, WEEK, 0);
            metric(sheet.getRow(3), 0, 0, 0, 0);
            sheet.getRow(3).getCell(6).setCellErrorValue(FormulaError.DIV0.getCode());
            List<String> warnings = new ArrayList<>();
            assertEquals("Lỗi Kết nối", reader.read(book, SHEET, WEEK, warnings).getTypicalService());
            assertTrue(warnings.isEmpty());
        }
    }

    @Test
    void rejectsMissingOrWrongPeriodDuplicateHeadersAndMissingSummary() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            assertMissing(book, WEEK);
            Sheet sheet = table(book, WEEK, 0);
            assertMissing(book, WEEK.plusWeeks(1));
            sheet.getRow(1).getCell(3).setCellValue("TB tuần (01/09/2025-07/09/2025)");
            assertMissing(book, WEEK);
            sheet.getRow(1).getCell(3).setCellValue("TB tuần (01/09-07/09)");
            sheet.getRow(1).createCell(7).setCellValue("TB tuần (01/09-07/09)");
            assertMissing(book, WEEK);
            sheet.getRow(1).getCell(7).setBlank();
            sheet.getRow(2).getCell(1).setCellValue("Bảng khác");
            assertMissing(book, WEEK);
            sheet.getRow(2).getCell(1).setCellValue("Phản ánh báo hỏng dịch vụ");
            sheet.getRow(1).getCell(5).setBlank();
            assertMissing(book, WEEK);
        }
    }

    @Test
    void rejectsBadSummaryNumbersInsteadOfFillingZero() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = table(book, WEEK, 0);
            sheet.getRow(2).getCell(3).setCellValue(-1); assertMissing(book, WEEK);
            sheet.getRow(2).getCell(3).setCellValue("NaN"); assertMissing(book, WEEK);
            sheet.getRow(2).getCell(3).setCellValue(100);
            sheet.getRow(2).getCell(6).setCellValue(-0.2); assertMissing(book, WEEK);
            sheet.getRow(2).getCell(6).setBlank(); assertMissing(book, WEEK);
        }
    }

    @Test
    void matchesExplicitPeriodAcrossNewYear() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            LocalDate week = LocalDate.of(2026, 12, 30);
            Sheet sheet = table(book, week, 0);
            sheet.getRow(1).getCell(3).setCellValue("TB tuần (30/12/2026-05/01/2027)");
            assertEquals("Lỗi Kết nối", reader.read(book, SHEET, week, new ArrayList<>()).getTypicalService());
        }
    }

    @Test
    void readsActualXlsmCachedValues() throws Exception {
        Path path = fixture();
        assumeTrue(Files.isRegularFile(path));
        try (InputStream input = Files.newInputStream(path); Workbook book = WorkbookFactory.create(input)) {
            List<String> warnings = new ArrayList<>();
            WordMobileFeedbackReader.Result result = reader.read(book, SHEET, WEEK, warnings);
            assertEquals("1.722", result.getAverage());
            assertEquals("tăng 13,4%", result.getChange());
            assertEquals("Lỗi Kết nối", result.getTypicalService());
            assertEquals("tăng 6,8 lần", result.getTypicalChange());
            assertTrue(warnings.isEmpty(), warnings.toString());
        }
    }

    private void assertMissing(Workbook book, LocalDate week) {
        assertThrows(WordMobileFeedbackReader.MissingMobileDataException.class,
                () -> reader.read(book, SHEET, week, new ArrayList<>()));
    }

    static Path fixture() {
        return Paths.get("documents", "File 1", "2. Báo cáo PAKH dịch vụ Di động tuần_thanhnt1",
                "PHU_LUC_BAO_CAO_TUAN_DICH_VU_DI_DONG_07092026.xlsm");
    }

    static Sheet table(Workbook book, LocalDate week, int shift) {
        Sheet sheet = book.createSheet(SHEET);
        Row top = sheet.createRow(0), header = sheet.createRow(1), total = sheet.createRow(2);
        top.createCell(shift).setCellValue("STT");
        top.createCell(shift + 1).setCellValue("DỊCH VỤ");
        sheet.addMergedRegion(new CellRangeAddress(0, 1, shift, shift));
        sheet.addMergedRegion(new CellRangeAddress(0, 1, shift + 1, shift + 1));
        DateTimeFormatter date = DateTimeFormatter.ofPattern("dd/MM");
        header.createCell(shift + 3).setCellValue("TB tuần (" + week.format(date) + "-" + week.plusDays(6).format(date) + ")");
        header.createCell(shift + 4).setCellValue("TB tuần trước");
        header.createCell(shift + 5).setCellValue("Tăng giảm");
        header.createCell(shift + 6).setCellValue("Tỷ lệ");
        total.createCell(shift + 1).setCellValue("Phản ánh báo hỏng dịch vụ");
        metric(total, shift, 1721.7142857142858, 203.71428571428578, 0.13419913419913423);
        service(sheet, 3, shift, 1, "Data", 291.1, 22.6, 0.084);
        service(sheet, 4, shift, 2, "Kết nối", 750.1, 654, 6.802);
        service(sheet, 5, shift, 3, "SMS", 43, -10, -0.189);
        return sheet;
    }

    static void service(Sheet sheet, int index, int shift, int order, String name, double average, double change, double rate) {
        Row row = sheet.getRow(index);
        if (row == null) row = sheet.createRow(index);
        row.createCell(shift).setCellValue(order);
        row.createCell(shift + 1).setCellValue(name);
        metric(row, shift, average, change, rate);
    }

    static void metric(Row row, int shift, double average, double change, double rate) {
        row.createCell(shift + 3).setCellValue(average);
        row.createCell(shift + 5).setCellValue(change);
        Cell percent = row.createCell(shift + 6); percent.setCellValue(rate);
        CellStyle style = row.getSheet().getWorkbook().createCellStyle();
        style.setDataFormat((short) 10); percent.setCellStyle(style);
    }
}
