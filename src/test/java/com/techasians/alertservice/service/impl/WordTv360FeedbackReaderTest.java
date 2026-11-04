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

class WordTv360FeedbackReaderTest {
    static final LocalDate WEEK = LocalDate.of(2026, 9, 1);
    private final WordTv360FeedbackReader reader = new WordTv360FeedbackReader();

    @Test
    void readsActualWorkbookUsingRawDailyAverageAndSummaryGroupsOnly() throws Exception {
        assumeTrue(Files.isRegularFile(fixture()));
        try (InputStream in = Files.newInputStream(fixture()); Workbook book = WorkbookFactory.create(in)) {
            List<String> warnings = new ArrayList<>();
            WordTv360FeedbackReader.Result result = read(book, WEEK, warnings);
            assertEquals("176", result.getDaily());
            assertEquals("giảm 18%", result.getChange());
            assertEquals("Không xem được tất cả các kênh", result.getTop().get(0).getName());
            assertEquals("57,5%", result.getTop().get(0).getShare());
            assertEquals("Thiết bị remote/box", result.getTop().get(1).getName());
            assertEquals("32,1%", result.getTop().get(1).getShare());
            assertEquals("46,8%", result.getSupportShare());
            assertEquals("20,6%", result.getCustomerShare());
            assertTrue(warnings.isEmpty(), warnings.toString());
        }
    }

    @Test
    void findsMovedTablesAndSubheadersAndExcludesDeviceSubtables() throws Exception {
        try (Workbook book = workbook(WEEK, 5, 3)) {
            List<String> warnings = new ArrayList<>();
            WordTv360FeedbackReader.Result result = read(book, WEEK, warnings);
            assertEquals("176", result.getDaily());
            assertEquals("giảm 18%", result.getChange());
            assertEquals("Không xem được tất cả các kênh", result.getTop().get(0).getName());
            assertEquals("Thiết bị remote/box", result.getTop().get(1).getName());
            assertEquals("46,8%", result.getSupportShare());
            assertEquals("20,6%", result.getCustomerShare());
            assertTrue(warnings.isEmpty(), warnings.toString());
        }
    }

    @Test
    void comparesUnroundedDailyValuesAndHandlesIncreaseAndNoChange() throws Exception {
        try (Workbook book = workbook(WEEK, 0, 0)) {
            Sheet sheet = book.getSheet("NDPA");
            for (double[] example : new double[][] {{100, 80}, {100.4, 100}, {100, 100}, {0, 0}, {0, 100}}) {
                put(sheet, 2, 4, example[0]); put(sheet, 2, 5, example[1]);
                String expected = example[0] == 100.4 ? "tăng 0,4%" : example[0] == example[1] ? "không đổi"
                        : example[0] == 0 ? "giảm 100%" : "tăng 25%";
                assertEquals(expected, read(book, WEEK, new ArrayList<>()).getChange());
            }
        }
    }

    @Test
    void missingPreviousWeekOrZeroBaselineKeepsCurrentTotalTopAndCauses() throws Exception {
        try (Workbook book = workbook(WEEK, 0, 0)) {
            Sheet sheet = book.getSheet("NDPA");
            put(sheet, 2, 5, 0);
            List<String> warnings = new ArrayList<>();
            WordTv360FeedbackReader.Result result = read(book, WEEK, warnings);
            assertEquals("176", result.getDaily());
            assertNull(result.getChange());
            assertEquals(2, result.getTop().size());
            assertTrue(result.hasCauses());
            assertTrue(warnings.get(0).contains("bằng 0"));
            put(sheet, 0, 5, "Không có kỳ trước");
            result = read(book, WEEK, new ArrayList<>());
            assertNull(result.getChange());
            assertEquals("176", result.getDaily());
        }
    }

    @Test
    void readsCachedFormulaHeadersLabelsNumbersAndVietnamesePercentText() throws Exception {
        try (Workbook book = workbook(WEEK, 0, 0)) {
            Sheet sheet = book.getSheet("NDPA");
            sheet.getRow(0).getCell(2).setCellFormula("\"01/09/2026-07/09/2026\"");
            sheet.getRow(1).getCell(4).setCellFormula("\"TB ngày\"");
            sheet.getRow(2).getCell(0).setCellFormula("\"DỊCH VỤ TV360\"");
            sheet.getRow(2).getCell(4).setCellFormula("1232/7");
            sheet.getRow(2).getCell(5).setCellFormula("1503/7");
            sheet.getRow(4).getCell(0).setCellFormula("1+1");
            sheet.getRow(4).getCell(1).setCellFormula("\"Không xem được tất cả các kênh\"");
            put(sheet, 4, 2, "57,55%");
            put(book.getSheet("NNLOI"), 5, 2, "46,8%");
            book.getCreationHelper().createFormulaEvaluator().evaluateAll();
            WordTv360FeedbackReader.Result result = read(book, WEEK, new ArrayList<>());
            assertEquals("176", result.getDaily());
            assertEquals("giảm 18%", result.getChange());
            assertEquals("57,6%", result.getTop().get(0).getShare());
            assertEquals("46,8%", result.getSupportShare());
        }
    }

    @Test
    void tiedSharesKeepSourceOrderAndOnePositiveServiceDoesNotInventSecond() throws Exception {
        try (Workbook book = workbook(WEEK, 0, 0)) {
            Sheet sheet = book.getSheet("NDPA");
            put(sheet, 3, 2, 0.5); put(sheet, 4, 2, 0.5); put(sheet, 5, 2, 0);
            WordTv360FeedbackReader.Result result = read(book, WEEK, new ArrayList<>());
            assertEquals("Chất lượng hình ảnh, âm thanh", result.getTop().get(0).getName());
            put(sheet, 4, 2, 0);
            assertEquals(1, read(book, WEEK, new ArrayList<>()).getTop().size());
        }
    }

    @Test
    void invalidShareDoesNotSilentlyRankIncompleteCandidatesAndMissingCauseIsPartial() throws Exception {
        try (Workbook book = workbook(WEEK, 0, 0)) {
            book.getSheet("NDPA").getRow(4).getCell(2).setCellErrorValue(FormulaError.DIV0.getCode());
            put(book.getSheet("NNLOI"), 5, 1, "Thiếu nhóm hỗ trợ");
            List<String> warnings = new ArrayList<>();
            WordTv360FeedbackReader.Result result = read(book, WEEK, warnings);
            assertEquals("176", result.getDaily());
            assertEquals("giảm 18%", result.getChange());
            assertTrue(result.getTop().isEmpty());
            assertFalse(result.hasCauses());
            assertEquals(2, warnings.size());
        }
    }

    @Test
    void missingErrorSheetKeepsValidNdpaContent() throws Exception {
        try (Workbook book = workbook(WEEK, 0, 0)) {
            book.removeSheetAt(book.getSheetIndex("NNLOI"));
            List<String> warnings = new ArrayList<>();
            WordTv360FeedbackReader.Result result = read(book, WEEK, warnings);
            assertEquals("176", result.getDaily());
            assertEquals(2, result.getTop().size());
            assertFalse(result.hasCauses());
            assertTrue(warnings.get(0).contains("Thiếu sheet NNLOI"));
        }
    }

    @Test
    void wrongWeekYearDuplicateHeaderAndInvalidCurrentDailyNeverBorrowOtherData() throws Exception {
        try (Workbook book = workbook(WEEK, 0, 0)) {
            assertThrows(WordTv360FeedbackReader.MissingTv360DataException.class, () -> read(book, WEEK.plusYears(1), new ArrayList<>()));
            Sheet sheet = book.getSheet("NDPA");
            put(sheet, 0, 5, period(WEEK));
            assertThrows(WordTv360FeedbackReader.MissingTv360DataException.class, () -> read(book, WEEK, new ArrayList<>()));
            put(sheet, 0, 5, period(WEEK.minusWeeks(1)));
            for (String invalid : Arrays.asList("", "-1", "#N/A")) {
                put(sheet, 2, 4, invalid);
                assertThrows(WordTv360FeedbackReader.MissingTv360DataException.class, () -> read(book, WEEK, new ArrayList<>()));
            }
        }
    }

    @Test
    void shortenedYearHeadersMatchWeeksAcrossNewYear() throws Exception {
        LocalDate week = LocalDate.of(2025, 12, 30);
        try (Workbook book = workbook(week, 0, 0)) {
            for (Sheet sheet : book) put(sheet, 0, 2, "30/12-05/01/2026");
            assertEquals("176", read(book, week, new ArrayList<>()).getDaily());
        }
    }

    private WordTv360FeedbackReader.Result read(Workbook book, LocalDate week, List<String> warnings) {
        return reader.read(new WordSourceDocuments.ExcelSheet("NDPA", book.getSheet("NDPA")),
                new WordSourceDocuments.ExcelSheet("NNLOI", book.getSheet("NNLOI")), week, warnings);
    }

    static Path fixture() {
        return Paths.get("documents", "File 2", "5. Báo cáo PAKH dịch vụ cố định tuần_phuongpm1", "PL TV360 TUAN 01.09-07.09.xlsx");
    }

    static Workbook workbook(LocalDate week, int row, int column) {
        Workbook book = new XSSFWorkbook();
        for (String name : Arrays.asList("NDPA", "NNLOI")) {
            Sheet sheet = book.createSheet(name);
            put(sheet, row, column, "STT");
            put(sheet, row, column + 1, name.equals("NDPA") ? "Nội dung phản ánh" : "Nguyên nhân lỗi");
            put(sheet, row, column + 2, period(week));
            put(sheet, row, column + 5, period(week.minusWeeks(1)));
            sheet.addMergedRegion(new CellRangeAddress(row, row, column + 2, column + 4));
            sheet.addMergedRegion(new CellRangeAddress(row, row, column + 5, column + 7));
            for (int index = 0; index < 6; index++) put(sheet, row + 1, column + 2 + index,
                    new String[] {"Tỷ trọng ngày", "Lũy kế tháng", "TB ngày", "TB ngày", "Tỷ trọng ngày", "Lũy kế tháng"}[index]);
            put(sheet, row + 2, column, "DỊCH VỤ TV360");
            sheet.addMergedRegion(new CellRangeAddress(row + 2, row + 2, column, column + 1));
            put(sheet, row + 2, column + 4, 176); put(sheet, row + 2, column + 5, 1503.0 / 7);
            group(sheet, row + 3, column, 1, name.equals("NDPA") ? "Chất lượng hình ảnh, âm thanh" : "Lỗi phía khách hàng", 0.206132879);
            if (name.equals("NDPA")) {
                group(sheet, row + 4, column, 2, "Không xem được tất cả các kênh", 0.5754870129);
                group(sheet, row + 5, column, 3, "Thiết bị remote/box", 0.3214285714);
            } else {
                group(sheet, row + 4, column, "-", "Chi tiết không được chọn", 0.99);
                group(sheet, row + 5, column, 2, "Hỗ trợ khách hàng", 0.468483816);
            }
            group(sheet, row + 6, column, "I", "VIETTELTV BOX", 0.99);
            group(sheet, row + 7, column, 1, name.equals("NDPA") ? "Dịch vụ chi tiết không được chọn" : "Hỗ trợ khách hàng", 0.99);
        }
        return book;
    }

    private static String period(LocalDate week) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return week.format(formatter) + "-" + week.plusDays(6).format(formatter);
    }

    static void group(Sheet sheet, int row, int column, Object order, String name, double share) {
        put(sheet, row, column, order); put(sheet, row, column + 1, name);
        put(sheet, row, column + 2, share); put(sheet, row, column + 4, 999);
    }

    static void put(Sheet sheet, int rowIndex, int column, Object value) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) row = sheet.createRow(rowIndex);
        Cell cell = row.getCell(column);
        if (cell == null) cell = row.createCell(column);
        if (value instanceof Number) cell.setCellValue(((Number)value).doubleValue());
        else cell.setCellValue(value.toString());
    }
}
