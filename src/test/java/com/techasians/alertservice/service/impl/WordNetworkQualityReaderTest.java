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

class WordNetworkQualityReaderTest {
    static final String SHEET = "Tỷ lệ PA tuần";
    static final LocalDate WEEK = LocalDate.of(2026, 9, 1);
    private final WordNetworkQualityReader reader = new WordNetworkQualityReader();

    @Test
    void readsActualWorkbookIncludingKpiHeadersAndCurrentWeekTopValues() throws Exception {
        assumeTrue(Files.isRegularFile(fixture()));
        try (InputStream in = Files.newInputStream(fixture()); Workbook book = WorkbookFactory.create(in)) {
            List<String> warnings = new ArrayList<>();
            WordNetworkQualityReader.Result result = reader.read(book, SHEET, WEEK, warnings);
            assertOverview(result);
            assertEquals("0,9", result.getVoice().getRate());
            assertEquals("0,59", result.getVoice().getKpi());
            assertEquals("Không đạt", result.getVoice().getAssessment());
            assertEquals("Lào Cai: 1,93PA; Điện Biên: 1,71PA; Tuyên Quang: 1,25PA; Hồ Chí Minh: 0,98PA; Cao Bằng: 0,89PA",
                    result.getVoice().getWarningsText());
            assertEquals("1,77", result.getData().getRate());
            assertEquals("1,97", result.getData().getKpi());
            assertEquals("Đạt", result.getData().getAssessment());
            assertEquals("Hà Nội: 2,97PA; Sơn La: 2,64PA; Điện Biên: 2,11PA; Cần Thơ: 1,63PA; Khánh Hòa: 2,13PA",
                    result.getData().getWarningsText());
            assertTrue(warnings.isEmpty(), warnings.toString());
        }
    }

    @Test
    void readsMovedRowsAndColumnsWithoutUsingC44OrFixedPeriodCoordinates() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = table(book, WEEK, 9, 5);
            put(sheet, 43, 2, 999); // C44 không còn là vị trí KPI.
            List<String> warnings = new ArrayList<>();
            WordNetworkQualityReader.Result result = reader.read(book, SHEET, WEEK, warnings);
            assertOverview(result);
            assertEquals("0,59", result.getVoice().getKpi());
            assertEquals("1,97", result.getData().getKpi());
            assertEquals("5", result.getVoice().getWarningCount());
            assertTrue(warnings.isEmpty(), warnings.toString());
        }
    }

    @Test
    void followsPaAndKqHeadersWhenTheirColumnOrderChanges() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = table(book, WEEK, 0, 0);
            for (int header : new int[] {1, 8, 18}) {
                put(sheet, header + 1, 6, "KQ"); put(sheet, header + 1, 8, "PA");
            }
            for (int total : new int[] {3, 10, 20}) {
                double pa = sheet.getRow(total).getCell(6).getNumericCellValue();
                double rate = sheet.getRow(total).getCell(8).getNumericCellValue();
                put(sheet, total, 6, rate); put(sheet, total, 8, pa);
            }
            WordNetworkQualityReader.Result result = reader.read(book, SHEET, WEEK, new ArrayList<>());
            assertOverview(result);
            assertEquals("0,9", result.getVoice().getRate());
            assertEquals("1,77", result.getData().getRate());
        }
    }

    @Test
    void missingOrDuplicateWeekDoesNotBorrowAnotherWeekOrAnotherServiceTable() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = table(book, WEEK, 0, 0);
            List<String> warnings = new ArrayList<>();
            WordNetworkQualityReader.Result result = reader.read(book, SHEET, WEEK.plusWeeks(1), warnings);
            assertNull(result.getOverview()); assertNull(result.getVoice()); assertNull(result.getData());
            assertEquals(3, warnings.size());
            put(sheet, 1, 3, period(WEEK));
            result = reader.read(book, SHEET, WEEK, new ArrayList<>());
            assertNull(result.getOverview());
            assertNotNull(result.getVoice()); assertNotNull(result.getData());
        }
    }

    @Test
    void missingKpiHeaderOnlyInvalidatesThatService() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = table(book, WEEK, 0, 0);
            put(sheet, 8, 0, "Không phải KPI");
            List<String> warnings = new ArrayList<>();
            WordNetworkQualityReader.Result result = reader.read(book, SHEET, WEEK, warnings);
            assertNotNull(result.getOverview()); assertNull(result.getVoice()); assertNotNull(result.getData());
            assertEquals(1, warnings.size());
            assertTrue(warnings.get(0).contains("header kpis"));
        }
    }

    @Test
    void assessmentComesFromEvaluationHeaderInsteadOfBeingRecomputedFromThreshold() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = table(book, WEEK, 0, 0);
            put(sheet, 10, 10, " ĐẠT KPI ");
            assertEquals("Đạt", reader.read(book, SHEET, WEEK, new ArrayList<>()).getVoice().getAssessment());
            put(sheet, 10, 10, "");
            assertNull(reader.read(book, SHEET, WEEK, new ArrayList<>()).getVoice());
        }
    }

    @Test
    void invalidTotalsAreReportedAndDoNotBecomeZeroOrBorrowProvinceNumbers() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = table(book, WEEK, 0, 0);
            put(sheet, 3, 6, -1);
            WordNetworkQualityReader.Result result = reader.read(book, SHEET, WEEK, new ArrayList<>());
            assertNull(result.getOverview()); assertNotNull(result.getVoice());
            put(sheet, 3, 6, 1026); put(sheet, 3, 7, 0);
            assertNull(reader.read(book, SHEET, WEEK, new ArrayList<>()).getOverview());
            put(sheet, 3, 7, 72273791); put(sheet, 3, 6, 1.5);
            assertNull(reader.read(book, SHEET, WEEK, new ArrayList<>()).getOverview());
            for (int col : new int[] {0, 6, 7, 8, 10}) put(sheet, 10, col, "");
            put(sheet, 11, 1, "Tỉnh Không được mượn");
            put(sheet, 11, 0, 0.23); metric(sheet, 11, 6, 9, 1000, 99);
            assertNull(reader.read(book, SHEET, WEEK, new ArrayList<>()).getVoice());
        }
    }

    @Test
    void missingCurrentTopHeaderDoesNotFallBackToPreviousWeekAndKeepsKpiResult() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = table(book, WEEK, 0, 0);
            put(sheet, 2, 15, "Không phải tuần hiện tại");
            List<String> warnings = new ArrayList<>();
            WordNetworkQualityReader.Result result = reader.read(book, SHEET, WEEK, warnings);
            assertEquals("0,9", result.getVoice().getRate());
            assertFalse(result.getVoice().hasWarnings());
            assertTrue(result.getData().hasWarnings());
            assertEquals(1, warnings.size());
            assertTrue(warnings.get(0).contains("header tuần hiện tại"));
        }
    }

    @Test
    void removesOnlyProvincePrefixesPreservesTableOrderAndReportsPartialTop() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = table(book, WEEK, 0, 0);
            put(sheet, 3, 13, "  THÀNH PHỐ\u00a0Hà Nội ");
            put(sheet, 4, 13, "Tỉnh Bình Tỉnh");
            sheet.getRow(7).getCell(15).setCellErrorValue(FormulaError.DIV0.getCode());
            List<String> warnings = new ArrayList<>();
            WordNetworkQualityReader.ServiceResult voice = reader.read(book, SHEET, WEEK, warnings).getVoice();
            assertEquals("4", voice.getWarningCount());
            assertTrue(voice.getWarningsText().startsWith("Hà Nội: 1,93PA; Bình Tỉnh: 1,71PA;"));
            assertEquals(2, warnings.size());
        }
    }

    @Test
    void readsCachedFormulaHeadersLabelsAndValuesWithoutRecalculation() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = table(book, WEEK, 0, 0);
            sheet.getRow(1).getCell(6).setCellFormula("\"" + period(WEEK) + "\"");
            sheet.getRow(2).getCell(8).setCellFormula("\"KQ\"");
            put(sheet, 3, 6, "1026");
            sheet.getRow(3).getCell(8).setCellFormula("2.028");
            put(sheet, 10, 0, "0,59");
            sheet.getRow(3).getCell(13).setCellFormula("\"Tỉnh Lào Cai\"");
            book.getCreationHelper().createFormulaEvaluator().evaluateAll();
            List<String> warnings = new ArrayList<>();
            WordNetworkQualityReader.Result result = reader.read(book, SHEET, WEEK, warnings);
            assertOverview(result);
            assertEquals("0,59", result.getVoice().getKpi());
            assertTrue(result.getVoice().getWarningsText().startsWith("Lào Cai: 1,93PA"));
            assertTrue(warnings.isEmpty(), warnings.toString());
        }
    }

    @Test
    void handlesExplicitYearsAcrossNewYearAndUnchangedOrIncreasingTrend() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            LocalDate week = LocalDate.of(2026, 12, 30);
            Sheet sheet = table(book, week, 0, 0);
            put(sheet, 1, 6, "Tuần Từ 30.12.2026 đến 5.01.2027");
            WordNetworkQualityReader.Result result = reader.read(book, SHEET, week, new ArrayList<>());
            assertOverview(result);
            put(sheet, 3, 8, 2.24);
            assertEquals("không đổi", reader.read(book, SHEET, week, new ArrayList<>()).getOverview().getTrend());
            put(sheet, 3, 8, 3);
            assertEquals("tăng", reader.read(book, SHEET, week, new ArrayList<>()).getOverview().getTrend());
        }
    }

    @Test
    void requiresConfiguredSheet() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            assertThrows(WordNetworkQualityReader.MissingQualityDataException.class,
                    () -> reader.read(book, SHEET, WEEK, new ArrayList<>()));
        }
    }

    private void assertOverview(WordNetworkQualityReader.Result result) {
        assertNotNull(result.getOverview());
        assertEquals("147", result.getOverview().getDailyPa());
        assertEquals("2,03", result.getOverview().getRate());
        assertEquals("2,24", result.getOverview().getPreviousRate());
        assertEquals("giảm", result.getOverview().getTrend());
    }

    static Path fixture() {
        return Paths.get("documents", "File 2", "3+4. Báo cáo PAKH dịch vụ chất lượng mạng + SME tuần_thuyntg",
                "PL_CLM Toàn qu c tu n 01.09 - 07.09_gui.xlsx");
    }

    static Sheet table(Workbook book, LocalDate week, int rowShift, int colShift) {
        Sheet sheet = book.createSheet(SHEET);
        section(sheet, 1 + rowShift, colShift, week);
        put(sheet, 3 + rowShift, colShift, "Tỷ lệ chung"); put(sheet, 3 + rowShift, colShift + 1, "Tổng");
        metric(sheet, 3 + rowShift, colShift + 3, 1135, 72273791, 2.24);
        metric(sheet, 3 + rowShift, colShift + 6, 1026, 72273791, 2.028);
        put(sheet, 7 + rowShift, colShift, "Chỉ tiêu thoại");
        section(sheet, 8 + rowShift, colShift, week);
        put(sheet, 8 + rowShift, colShift + 10, "Đánh giá");
        put(sheet, 10 + rowShift, colShift, 0.59); put(sheet, 10 + rowShift, colShift + 10, "Không đạt");
        metric(sheet, 10 + rowShift, colShift + 3, 496, 69580728, 1.02);
        metric(sheet, 10 + rowShift, colShift + 6, 440, 69580728, 0.9);
        put(sheet, 17 + rowShift, colShift, "Chỉ tiêu Data");
        section(sheet, 18 + rowShift, colShift, week);
        put(sheet, 18 + rowShift, colShift + 10, "Đánh giá");
        put(sheet, 20 + rowShift, colShift, 1.97); put(sheet, 20 + rowShift, colShift + 10, "Đạt");
        metric(sheet, 20 + rowShift, colShift + 3, 974, 71982682, 1.93);
        metric(sheet, 20 + rowShift, colShift + 6, 894, 71982682, 1.77);
        top(sheet, 1 + rowShift, 12 + colShift, "TOP 5 Tỉnh Thoại tăng cao nhất Tuần",
                new String[] {"Tỉnh Lào Cai", "Tỉnh Điện Biên", "Tỉnh Tuyên Quang", "Thành phố Hồ Chí Minh", "Tỉnh Cao Bằng"},
                new double[] {1.93, 1.71, 1.25, 0.98, 0.89});
        top(sheet, 26 + rowShift, 12 + colShift, "TOP 5 Tỉnh Data tăng cao nhất Tuần",
                new String[] {"Thành phố Hà Nội", "Tỉnh Sơn La", "Tỉnh Điện Biên", "Thành phố Cần Thơ", "Tỉnh Khánh Hòa"},
                new double[] {2.97, 2.64, 2.11, 1.63, 2.13});
        return sheet;
    }

    private static void section(Sheet sheet, int row, int col, LocalDate week) {
        put(sheet, row, col, "KPIs"); put(sheet, row, col + 1, "Nguồn tiếp nhận");
        for (int group = 0; group < 2; group++) {
            int first = col + 3 + group * 3;
            put(sheet, row, first, period(group == 0 ? week.minusWeeks(1) : week));
            sheet.addMergedRegion(new CellRangeAddress(row, row, first, first + 2));
            put(sheet, row + 1, first, "PA"); put(sheet, row + 1, first + 1, "Thuê bao"); put(sheet, row + 1, first + 2, "KQ");
        }
    }

    private static void top(Sheet sheet, int row, int col, String title, String[] names, double[] rates) {
        put(sheet, row, col, title); sheet.addMergedRegion(new CellRangeAddress(row, row, col, col + 3));
        put(sheet, row + 1, col, "TT"); put(sheet, row + 1, col + 1, "Tỉnh");
        put(sheet, row + 1, col + 2, "Tuần trước"); put(sheet, row + 1, col + 3, "Tuần hiện tại");
        for (int i = 0; i < 5; i++) {
            put(sheet, row + 2 + i, col + 1, names[i]); put(sheet, row + 2 + i, col + 2, 99);
            put(sheet, row + 2 + i, col + 3, rates[i]);
        }
    }

    private static String period(LocalDate week) {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("d,MM");
        return "Tuần Từ " + week.format(format) + " đến " + week.plusDays(6).format(format);
    }

    static void metric(Sheet sheet, int row, int first, double pa, double subscribers, double rate) {
        put(sheet, row, first, pa); put(sheet, row, first + 1, subscribers); put(sheet, row, first + 2, rate);
    }

    static void put(Sheet sheet, int index, int column, Object value) {
        Row row = sheet.getRow(index); if (row == null) row = sheet.createRow(index);
        Cell cell = row.getCell(column); if (cell == null) cell = row.createCell(column);
        if (value instanceof Number) cell.setCellValue(((Number)value).doubleValue()); else cell.setCellValue((String)value);
    }
}
