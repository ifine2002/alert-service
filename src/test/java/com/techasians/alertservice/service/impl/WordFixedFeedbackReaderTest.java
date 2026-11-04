package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.service.impl.WordSourceDocuments.ExcelSheet;
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

class WordFixedFeedbackReaderTest {
    static final LocalDate WEEK = LocalDate.of(2026, 9, 1);

    @Test
    void readsActualWorkbookWithoutTplaSheet() throws Exception {
        assumeTrue(Files.isRegularFile(fixture()));
        try (InputStream input = Files.newInputStream(fixture()); Workbook book = WorkbookFactory.create(input)) {
            book.removeSheetAt(book.getSheetIndex("TPLA tuần N"));
            List<String> warnings = new ArrayList<>();
            WordFixedFeedbackReader.Result result = read(book, WEEK, warnings);
            assertTrue(warnings.isEmpty(), warnings.toString());
            assertExpected(result);
        }
    }

    @Test
    void findsShiftedRowsColumnsAndIgnoresRepeatedHistoryDates() throws Exception {
        for (int shift : new int[] {0, 6}) {
            try (Workbook book = workbook(shift)) {
                List<String> warnings = new ArrayList<>();
                WordFixedFeedbackReader.Result result = read(book, WEEK, warnings);
                assertTrue(warnings.isEmpty(), warnings.toString());
                assertExpected(result);
            }
        }
    }

    @Test
    void tplaValuesCannotAffectAnyMetric() throws Exception {
        try (Workbook book = workbook(0)) {
            Sheet unrelated = book.createSheet("TPLA tuần N");
            put(unrelated, 0, 0, "Tổng DVCĐ");
            put(unrelated, 0, 1, 999999);
            put(unrelated, 1, 0, "FTTH - GPON");
            put(unrelated, 1, 1, 999999);
            assertExpected(read(book, WEEK, new ArrayList<>()));
        }
    }

    @Test
    void kpiComesFromThAndAssessmentIncludesBoundary() throws Exception {
        try (Workbook book = workbook(0)) {
            put(book.getSheet("TH"), 7, 6, 8.19);
            put(book.getSheet("TH"), 6, 6, 1.66);
            WordFixedFeedbackReader.Result result = read(book, WEEK, new ArrayList<>());
            assertService(result.getGpon(), "8,19", "8,19", "Đạt");
            assertService(result.getTelevision(), "1,67", "1,66", "Không đạt");
        }
    }

    @Test
    void wrongWeekNeverReadsTheFirstAvailableWeek() throws Exception {
        try (Workbook book = workbook(0)) {
            List<String> warnings = new ArrayList<>();
            WordFixedFeedbackReader.Result result = read(book, WEEK.plusWeeks(1), warnings);
            assertFalse(result.hasReceived());
            assertNull(result.getTechnicalDaily());
            assertNull(result.getRate());
            assertNull(result.getGpon());
            assertTrue(warnings.stream().anyMatch(text -> text.contains("đúng kỳ")), warnings.toString());
        }
    }

    @Test
    void missingPreviousWeekKeepsCurrentValuesWithoutInventingComparison() throws Exception {
        try (Workbook book = workbook(0)) {
            put(book.getSheet("N.Xet tuan"), 2, 3, period(WEEK.minusWeeks(2)));
            List<String> warnings = new ArrayList<>();
            WordFixedFeedbackReader.Result result = read(book, WEEK, warnings);
            assertEquals("7,1", result.getRate());
            assertEquals("8.231", result.getTechnicalDaily());
            assertNotNull(result.getGpon());
            assertNull(result.getTechnicalChange());
            assertNull(result.getRateChange());
            assertTrue(warnings.stream().anyMatch(text -> text.contains("cột tuần trước")), warnings.toString());
        }
    }

    @Test
    void zeroPreviousScDoesNotCreateInfinitePercent() throws Exception {
        try (Workbook book = workbook(0)) {
            put(book.getSheet("N.Xet tuan"), 6, 3, 0);
            WordFixedFeedbackReader.Result result = read(book, WEEK, new ArrayList<>());
            assertNull(result.getTechnicalChange());
            assertEquals("giảm 1,35PA", result.getRateChange());
        }
    }

    @Test
    void trendsUseMatchingRowsAndWeekValuesRatherThanComparisonColumn() throws Exception {
        try (Workbook book = workbook(0)) {
            Sheet notes = book.getSheet("N.Xet tuan");
            put(notes, 6, 3, 57618);
            put(notes, 7, 3, 7.10);
            put(notes, 7, 4, -999); // Cột biến động có sẵn không phải nguồn tính chênh lệch.
            WordFixedFeedbackReader.Result same = read(book, WEEK, new ArrayList<>());
            assertEquals("không đổi", same.getTechnicalChange());
            assertEquals("không đổi", same.getRateChange());
            put(notes, 6, 3, 28809);
            put(notes, 7, 3, 6.10);
            WordFixedFeedbackReader.Result increased = read(book, WEEK, new ArrayList<>());
            assertEquals("tăng 100%", increased.getTechnicalChange());
            assertEquals("tăng 1PA", increased.getRateChange());
        }
    }

    @Test
    void missingKpiGettersAreNullSafeAndOtherServicesRemainUsable() throws Exception {
        try (Workbook book = workbook(0)) {
            book.getSheet("TH").getRow(7).getCell(6).setCellErrorValue(FormulaError.NA.getCode());
            List<String> warnings = new ArrayList<>();
            WordFixedFeedbackReader.Result result = read(book, WEEK, warnings);
            assertTrue(result.hasReceived());
            assertEquals("7,1", result.getRate());
            assertFalse(result.getGpon().hasKpi());
            assertNull(result.getGpon().getKpi());
            assertNull(result.getGpon().getAssessment());
            assertService(result.getTelevision(), "1,67", "5", "Đạt");
            assertEquals(1, warnings.size(), warnings.toString());
        }
    }

    @Test
    void missingTechnicalRowDoesNotFallBackToTotalOrEraseRates() throws Exception {
        try (Workbook book = workbook(0)) {
            put(book.getSheet("N.Xet tuan"), 6, 1, "Tổng DVCĐ");
            List<String> warnings = new ArrayList<>();
            WordFixedFeedbackReader.Result result = read(book, WEEK, warnings);
            assertNull(result.getTechnicalDaily());
            assertNull(result.getTechnicalChange());
            assertEquals("7,1", result.getRate());
            assertEquals("giảm 1,35PA", result.getRateChange());
            assertEquals("13.134", result.getReceivedDaily());
            assertNotNull(result.getGpon());
            assertTrue(warnings.stream().anyMatch(text -> text.contains("SỰ CỐ")), warnings.toString());
        }
    }

    @Test
    void missingRateOrInvalidServiceDoesNotClearOtherValidMetrics() throws Exception {
        try (Workbook book = workbook(0)) {
            book.getSheet("N.Xet tuan").getRow(7).getCell(2).setBlank();
            put(book.getSheet("N.Xet tuan"), 9, 2, -1);
            List<String> warnings = new ArrayList<>();
            WordFixedFeedbackReader.Result result = read(book, WEEK, warnings);
            assertNull(result.getRate());
            assertNull(result.getRateChange());
            assertNull(result.getTelevision());
            assertEquals("8.231", result.getTechnicalDaily());
            assertEquals("giảm 16%", result.getTechnicalChange());
            assertNotNull(result.getGpon());
            assertFalse(warnings.isEmpty());
        }
    }

    @Test
    void duplicateLabelInSelectedTableIsReported() throws Exception {
        try (Workbook book = workbook(0)) {
            put(book.getSheet("N.Xet tuan"), 10, 1, "TLPA/10K");
            List<String> warnings = new ArrayList<>();
            WordFixedFeedbackReader.Result result = read(book, WEEK, warnings);
            assertNull(result.getRate());
            assertEquals("8.231", result.getTechnicalDaily());
            assertTrue(warnings.stream().anyMatch(text -> text.contains("Trùng nhãn")), warnings.toString());
        }
    }

    @Test
    void duplicateLabelsInOtherTablesDoNotMatchTheSelectedTable() throws Exception {
        try (Workbook book = workbook(0)) {
            Sheet notes = book.getSheet("N.Xet tuan");
            put(notes, 12, 3, "Cùng kỳ tuần trước");
            put(notes, 13, 2, period(WEEK.minusWeeks(2)));
            put(notes, 14, 1, "HTDV + SỰ CỐ tiếp nhận chuyển BO");
            put(notes, 15, 1, "TLPA/10K");
            put(notes, 14, 2, 999999);
            put(notes, 15, 2, 999999);
            Sheet kpi = book.getSheet("TH");
            put(kpi, 12, 4, "Chỉ tiêu");
            put(kpi, 12, 5, "Ghi chú");
            put(kpi, 14, 4, "FTTH_Gpon");
            List<String> warnings = new ArrayList<>();
            assertExpected(read(book, WEEK, warnings));
            assertTrue(warnings.isEmpty(), warnings.toString());
        }
    }

    @Test
    void ambiguousCurrentWeekTablesAreNotSelectedArbitrarily() throws Exception {
        try (Workbook book = workbook(0)) {
            Sheet notes = book.getSheet("N.Xet tuan");
            put(notes, 12, 3, "Cùng kỳ tuần trước");
            put(notes, 13, 2, period(WEEK));
            put(notes, 14, 1, "TLPA/10K");
            List<String> warnings = new ArrayList<>();
            WordFixedFeedbackReader.Result result = read(book, WEEK, warnings);
            assertNull(result.getRate());
            assertFalse(result.hasReceived());
            assertTrue(warnings.stream().anyMatch(text -> text.contains("nhiều bảng")), warnings.toString());
        }
    }

    @Test
    void recalculatesUsedInternalFormulasWithoutMutatingStaleCache() throws Exception {
        try (Workbook book = workbook(0)) {
            Cell pa = book.getSheet("N.Xet tuan").getRow(6).getCell(2);
            pa.setCellFormula("57618");
            pa.setCellValue(1);
            Cell rate = book.getSheet("N.Xet tuan").getRow(7).getCell(2);
            rate.setCellFormula("71/10");
            rate.setCellValue(99);
            Cell kpi = book.getSheet("TH").getRow(7).getCell(6);
            kpi.setCellFormula("6+6");
            kpi.setCellValue(999);
            List<String> warnings = new ArrayList<>();
            assertExpected(read(book, WEEK, warnings));
            assertTrue(warnings.isEmpty(), warnings.toString());
            assertEquals(1, pa.getNumericCellValue(), "Source cache must not be rewritten");
            assertEquals(99, rate.getNumericCellValue());
            assertEquals(999, kpi.getNumericCellValue());
        }
    }

    @Test
    void unsupportedFormulaFallsBackToCacheWithExplicitWarning() throws Exception {
        try (Workbook book = workbook(0)) {
            Cell kpi = book.getSheet("TH").getRow(7).getCell(6);
            kpi.setCellFormula("CUBEVALUE(\"unavailable\",\"member\")");
            kpi.setCellValue(12);
            List<String> warnings = new ArrayList<>();
            assertEquals("12", read(book, WEEK, warnings).getGpon().getKpi());
            assertEquals(1, warnings.stream().filter(text -> text.contains("cache Excel")).count(), warnings.toString());
        }
    }

    static Workbook workbook(int shift) {
        Workbook book = new XSSFWorkbook();
        Sheet notes = book.createSheet("N.Xet tuan");
        put(notes, shift + 1, shift + 2, "Tuần");
        put(notes, shift + 1, shift + 3, "Cùng kỳ tuần trước");
        put(notes, shift + 2, shift + 2, period(WEEK));
        put(notes, shift + 2, shift + 3, period(WEEK.minusWeeks(1)));
        // Cột lịch sử cùng tuần ở bên phải phải được bỏ qua.
        put(notes, shift + 1, shift + 6, "Tuần");
        put(notes, shift + 2, shift + 6, period(WEEK));
        put(notes, shift + 6, shift, "THS"); // Nhãn của phần diễn giải bên trái bảng số liệu.
        String[] labels = {"HTDV + SỰ CỐ tiếp nhận chuyển BO", "HTDV + SỰ CỐ tiếp nhận chuyển KT",
                "SỰ CỐ tiếp nhận chuyển KT", "TLPA/10K", "FTTH GPON", "THS"};
        double[][] values = {{2307, 3714}, {89633, 111581}, {57618, 68591}, {7.1, 8.45}, {8.19, 9.85}, {1.67, 1.63}};
        for (int index = 0; index < labels.length; index++) {
            put(notes, shift + 4 + index, shift + 1, labels[index]);
            put(notes, shift + 4 + index, shift + 2, values[index][0]);
            put(notes, shift + 4 + index, shift + 3, values[index][1]);
        }
        put(notes, shift + 4, shift + 2, "2.307");
        put(notes, shift + 4, shift + 6, 999999);
        put(notes, shift + 10, shift + 1, "Tỷ lệ TĐXL VIP");
        put(notes, shift + 11, shift + 1, "THS");
        put(notes, shift + 11, shift + 2, 414);
        put(notes, shift + 12, shift + 1, "FTTH_Gpon");
        put(notes, shift + 12, shift + 2, 53669);
        Sheet kpi = book.createSheet("TH");
        put(kpi, shift + 3, shift + 4, "Chỉ tiêu");
        put(kpi, shift + 3, shift + 5, "KPI");
        kpi.addMergedRegion(new CellRangeAddress(shift + 3, shift + 3, shift + 5, shift + 6));
        put(kpi, shift + 6, shift + 4, "Truyền hình số");
        put(kpi, shift + 6, shift + 5, "≤");
        put(kpi, shift + 6, shift + 6, 5);
        put(kpi, shift + 7, shift + 4, "FTTH_Gpon");
        put(kpi, shift + 7, shift + 5, "≤");
        put(kpi, shift + 7, shift + 6, 12);
        return book;
    }

    static void put(Sheet sheet, int rowIndex, int col, Object value) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) row = sheet.createRow(rowIndex);
        Cell cell = row.getCell(col);
        if (cell == null) cell = row.createCell(col);
        if (value instanceof Number) cell.setCellValue(((Number) value).doubleValue());
        else cell.setCellValue(value.toString());
    }
    static WordFixedFeedbackReader.Result read(Workbook book, LocalDate week, List<String> warnings) {
        return new WordFixedFeedbackReader().read(new ExcelSheet("N.Xet tuan", book.getSheet("N.Xet tuan")),
                new ExcelSheet("TH", book.getSheet("TH")), week, warnings);
    }
    private void assertExpected(WordFixedFeedbackReader.Result result) {
        assertEquals("13.134", result.getReceivedDaily());
        assertEquals("8.231", result.getTechnicalDaily());
        assertEquals("giảm 16%", result.getTechnicalChange());
        assertEquals("7,1", result.getRate());
        assertEquals("giảm 1,35PA", result.getRateChange());
        assertService(result.getGpon(), "8,19", "12", "Đạt");
        assertService(result.getTelevision(), "1,67", "5", "Đạt");
    }
    private void assertService(WordFixedFeedbackReader.Service value, String rate, String kpi, String assessment) {
        assertNotNull(value); assertEquals(rate, value.getRate());
        assertEquals(kpi, value.getKpi()); assertEquals(assessment, value.getAssessment());
    }
    static String period(LocalDate week) {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return week.format(format) + "-" + week.plusDays(6).format(format);
    }
    static Path fixture() {
        return Paths.get("documents", "File 2", "5. Báo cáo PAKH dịch vụ cố định tuần_phuongpm1", "PL CDBR TUAN 01.09-07.09.xlsx");
    }
}
