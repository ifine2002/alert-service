package com.techasians.alertservice.service.impl;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WordIncidentChartReaderTest {

    private final WordIncidentChartReader reader = new WordIncidentChartReader();

    // Tuần báo cáo: 08/09/2026 – 14/09/2026
    // 8 tuần: 21/07/2026 – 14/09/2026
    private final LocalDate weekStartDate = LocalDate.of(2026, 9, 8);
    private static final String CURRENT_SHEET = "Lũy kế lỗi phát sinh năm 2026";

    @Test
    void findsSystemHeaderAndDataAfterMovingTableDownAndRight() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = createCurrentSheet(workbook);
            addCurrentRow(sheet, 1, 2026, 9, 8, "VTT", 24.0);
            sheet.shiftRows(0, 1, 75);
            sheet.shiftColumns(0, 4, 6);
            List<String> warnings = new ArrayList<>();
            assertArrayEquals(new Double[] {1.0, 24.0}, read(workbook, weekStartDate, warnings)[7]);
            assertTrue(warnings.isEmpty(), warnings.toString());
        }
    }

    @Test
    void configuredSystemSheetsUseEndOfReportingWeekYearAndDoNotDoubleCountWrongYear() throws Exception {
        LocalDate week = LocalDate.of(2026, 12, 30);
        try (Workbook book = new XSSFWorkbook()) {
            Sheet current = createCurrentSheet(book), previous = createPrevSheet(book, 2026);
            book.setSheetName(book.getSheetIndex(current), "System current");
            book.setSheetName(book.getSheetIndex(previous), "System archive");
            addCurrentRow(current, 1, 2027, 1, 1, "VTT", 4.0);
            addCurrentRow(current, 2, 2026, 12, 31, "VTT", 999.0);
            addPrevRow(previous, 1, "Tháng 12.2026", 31, "VTT", 3.0);
            List<String> warnings = new ArrayList<>();
            Double[][] values = reader.read(new WordSourceDocuments.ExcelSheet(current.getSheetName(), current),
                    new WordSourceDocuments.ExcelSheet(previous.getSheetName(), previous), week, warnings);
            assertArrayEquals(new Double[] {2.0, 7.0}, values[7]);
            assertTrue(warnings.isEmpty(), warnings.toString());
        }
    }

    private Double[][] read(Workbook workbook, LocalDate week, List<String> warnings) {
        int year = week.plusDays(6).getYear();
        String current = "Lũy kế lỗi phát sinh năm " + year, previous = "LPS năm " + (year - 1);
        return reader.read(new WordSourceDocuments.ExcelSheet(current, workbook.getSheet(current)),
                new WordSourceDocuments.ExcelSheet(previous, workbook.getSheet(previous)), week, warnings);
    }

    // -------------------------------------------------------------------------
    // Helpers tạo workbook giả
    // -------------------------------------------------------------------------

    /** Tạo sheet năm hiện tại với header chuẩn. Trả về index dòng data đầu tiên = 1. */
    private Sheet createCurrentSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet(CURRENT_SHEET);
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Năm");
        header.createCell(1).setCellValue("Tháng");
        header.createCell(2).setCellValue("Ngày");
        header.createCell(3).setCellValue("Chủ dịch vụ");
        header.createCell(4).setCellValue("Số lượng phản ảnh");
        return sheet;
    }

    /** Thêm một dòng dữ liệu vào sheet năm hiện tại. */
    private void addCurrentRow(Sheet sheet, int rowIndex,
                               int year, int month, int day,
                               String owner, Double complaints) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(year);
        row.createCell(1).setCellValue(month);
        row.createCell(2).setCellValue(day);
        row.createCell(3).setCellValue(owner);
        if (complaints != null) {
            row.createCell(4).setCellValue(complaints);
        }
    }

    /** Tạo sheet năm trước với header chuẩn. */
    private Sheet createPrevSheet(Workbook workbook, int prevYear) {
        Sheet sheet = workbook.createSheet("LPS năm " + prevYear);
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Tháng");
        header.createCell(1).setCellValue("Ngày");
        header.createCell(2).setCellValue("Chủ dịch vụ");
        header.createCell(3).setCellValue("Số lượng phản ảnh");
        return sheet;
    }

    /** Thêm một dòng dữ liệu vào sheet năm trước. */
    private void addPrevRow(Sheet sheet, int rowIndex,
                            String monthText, int day,
                            String owner, Double complaints) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(monthText);
        row.createCell(1).setCellValue(day);
        row.createCell(2).setCellValue(owner);
        if (complaints != null) {
            row.createCell(3).setCellValue(complaints);
        }
    }

    // -------------------------------------------------------------------------
    // Test cases
    // -------------------------------------------------------------------------

    @Test
    void countsIncidentsAndSumsPakhForOneWeek() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = createCurrentSheet(workbook);
            // Tuần 7 = 08/09 – 14/09: 2 dòng VTT
            addCurrentRow(sheet, 1, 2026, 9, 8, "VTT", 24.0);
            addCurrentRow(sheet, 2, 2026, 9, 10, "VTT", 6.0);
            // Dòng VNP: không được tính
            addCurrentRow(sheet, 3, 2026, 9, 9, "VNP", 100.0);

            List<String> warnings = new ArrayList<>();
            Double[][] result = read(workbook, weekStartDate, warnings);

            // Tuần index 7 (tuần hiện tại)
            assertEquals(2.0, result[7][0], "Số sự cố tuần hiện tại");
            assertEquals(30.0, result[7][1], "Tổng PAKH tuần hiện tại");
            // Các tuần khác phải là 0
            for (int i = 0; i < 7; i++) {
                assertEquals(0.0, result[i][0], "Sự cố tuần " + i + " phải 0");
                assertEquals(0.0, result[i][1], "PAKH tuần " + i + " phải 0");
            }
            assertTrue(warnings.isEmpty(), "Không có warning khi dữ liệu chuẩn");
        }
    }

    @Test
    void sumsPakhStoredAsTextOrCachedTextFormula() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = createCurrentSheet(workbook);
            addCurrentRow(sheet, 1, 2026, 9, 8, "VTT", null);
            sheet.getRow(1).createCell(4).setCellValue(" 24 ");
            addCurrentRow(sheet, 2, 2026, 9, 10, "VTT", null);
            sheet.getRow(2).createCell(4).setCellFormula("\"6\"");
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            List<String> warnings = new ArrayList<>();
            assertArrayEquals(new Double[] {2.0, 30.0}, read(workbook, weekStartDate, warnings)[7]);
            assertTrue(warnings.isEmpty(), warnings.toString());
        }
    }

    @Test
    void distributesDataAcrossEightWeeks() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = createCurrentSheet(workbook);
            // Tuần 0 = 21/07 – 27/07
            addCurrentRow(sheet, 1, 2026, 7, 21, "VTT", 41.0);
            // Tuần 1 = 28/07 – 03/08
            addCurrentRow(sheet, 2, 2026, 7, 28, "VTT", 272.0);
            // Tuần 7 = 08/09 – 14/09
            addCurrentRow(sheet, 3, 2026, 9, 8, "VTT", 37.0);

            List<String> warnings = new ArrayList<>();
            Double[][] result = read(workbook, weekStartDate, warnings);

            assertEquals(1.0, result[0][0]);
            assertEquals(41.0, result[0][1]);
            assertEquals(1.0, result[1][0]);
            assertEquals(272.0, result[1][1]);
            assertEquals(1.0, result[7][0]);
            assertEquals(37.0, result[7][1]);
        }
    }

    @Test
    void excludesRowsOutsideEightWeekRange() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = createCurrentSheet(workbook);
            // Quá xa về trước (tuần thứ 9)
            addCurrentRow(sheet, 1, 2026, 7, 13, "VTT", 50.0);
            // Quá xa về sau (sau tuần hiện tại)
            addCurrentRow(sheet, 2, 2026, 9, 16, "VTT", 50.0);

            List<String> warnings = new ArrayList<>();
            Double[][] result = read(workbook, weekStartDate, warnings);

            for (int i = 0; i < 8; i++) {
                assertEquals(0.0, result[i][0], "Sự cố tuần " + i + " phải 0");
            }
        }
    }

    @Test
    void skipsRowWhenCurrentYearDateCannotBeParsed() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = createCurrentSheet(workbook);
            addCurrentRow(sheet, 1, 2026, 13, 8, "VTT", 10.0);
            addCurrentRow(sheet, 2, 2026, 9, 8, "VTT", 20.0);

            List<String> warnings = new ArrayList<>();
            Double[][] result = read(workbook, weekStartDate, warnings);

            assertEquals(1.0, result[7][0], "Chỉ dòng có ngày hợp lệ được tính");
            assertEquals(20.0, result[7][1], "PAKH của dòng ngày không hợp lệ không được cộng");
            assertFalse(warnings.isEmpty(), "Phải có cảnh báo cho ngày không hợp lệ");
        }
    }

    @Test
    void countIncidentEvenWhenPakhMissing() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = createCurrentSheet(workbook);
            // Dòng không có PAKH
            addCurrentRow(sheet, 1, 2026, 9, 8, "VTT", null);

            List<String> warnings = new ArrayList<>();
            Double[][] result = read(workbook, weekStartDate, warnings);

            // Vẫn đếm 1 sự cố
            assertEquals(1.0, result[7][0], "Sự cố vẫn đếm dù thiếu PAKH");
            // PAKH không cộng
            assertEquals(0.0, result[7][1], "PAKH phải 0 khi thiếu");
            // Phải có warning
            assertFalse(warnings.isEmpty(), "Phải có warning khi thiếu PAKH");
        }
    }

    @Test
    void throwsWhenCurrentSheetMissing() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("SheetKhac");
            List<String> warnings = new ArrayList<>();
            assertThrows(WordIncidentChartReader.MissingChartDataException.class,
                    () -> read(workbook, weekStartDate, warnings));
        }
    }

    @Test
    void acceptsVttOwnerIgnoringCaseAndWhitespace() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = createCurrentSheet(workbook);
            addCurrentRow(sheet, 1, 2026, 9, 8, "vtt", 10.0);
            addCurrentRow(sheet, 2, 2026, 9, 8, " VTT", 20.0);
            addCurrentRow(sheet, 3, 2026, 9, 8, "VTT ", 30.0);
            addCurrentRow(sheet, 4, 2026, 9, 8, "VNP", 40.0);

            List<String> warnings = new ArrayList<>();
            Double[][] result = read(workbook, weekStartDate, warnings);

            assertEquals(3.0, result[7][0], "Ba biến thể VTT phải được tính");
            assertEquals(60.0, result[7][1], "Không cộng dữ liệu của VNP");
        }
    }

    // -------------------------------------------------------------------------
    // Test vắt năm
    // -------------------------------------------------------------------------

    @Test
    void readsFromPrevYearSheetWhenSpanningYears() throws Exception {
        // Tuần báo cáo đầu năm 2026; 7 tuần trước vắt sang 2025
        LocalDate earlyWeek = LocalDate.of(2026, 1, 5); // 05/01/2026, chartStart = 17/11/2025
        String earlySheet = "Lũy kế lỗi phát sinh năm 2026";

        try (Workbook workbook = new XSSFWorkbook()) {
            // Sheet năm hiện tại (2026)
            Sheet curr = workbook.createSheet(earlySheet);
            Row h1 = curr.createRow(0);
            h1.createCell(0).setCellValue("Năm");
            h1.createCell(1).setCellValue("Tháng");
            h1.createCell(2).setCellValue("Ngày");
            h1.createCell(3).setCellValue("Chủ dịch vụ");
            h1.createCell(4).setCellValue("Số lượng phản ảnh");
            // Dòng trong tuần 7 (05/01 – 11/01/2026)
            Row r1 = curr.createRow(1);
            r1.createCell(0).setCellValue(2026); r1.createCell(1).setCellValue(1);
            r1.createCell(2).setCellValue(6); r1.createCell(3).setCellValue("VTT");
            r1.createCell(4).setCellValue(15.0);

            // Sheet năm trước (2025)
            Sheet prev = createPrevSheet(workbook, 2025);
            // Dòng trong tuần 0 = 17/11/2025 – 23/11/2025
            addPrevRow(prev, 1, "Tháng 11.2025", 20, "VTT", 41.0);
            // Dòng VNP trong năm trước: bỏ qua
            addPrevRow(prev, 2, "Tháng 11.2025", 21, "VNP", 99.0);

            List<String> warnings = new ArrayList<>();
            Double[][] result = read(workbook, earlyWeek, warnings);

            // Tuần 0 thuộc 17-23/11/2025
            assertEquals(1.0, result[0][0], "Sự cố từ sheet năm trước");
            assertEquals(41.0, result[0][1], "PAKH từ sheet năm trước");
            // Tuần 7 thuộc 05-11/01/2026
            assertEquals(1.0, result[7][0], "Sự cố từ sheet năm hiện tại");
            assertEquals(15.0, result[7][1], "PAKH từ sheet năm hiện tại");
            assertTrue(warnings.isEmpty());
        }
    }

    @Test
    void warnsAndContinuesWhenPrevYearSheetMissing() throws Exception {
        LocalDate earlyWeek = LocalDate.of(2026, 1, 5);
        String earlySheet = "Lũy kế lỗi phát sinh năm 2026";

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet curr = workbook.createSheet(earlySheet);
            Row h1 = curr.createRow(0);
            h1.createCell(0).setCellValue("Năm"); h1.createCell(1).setCellValue("Tháng");
            h1.createCell(2).setCellValue("Ngày"); h1.createCell(3).setCellValue("Chủ dịch vụ");
            h1.createCell(4).setCellValue("Số lượng phản ảnh");
            Row r1 = curr.createRow(1);
            r1.createCell(0).setCellValue(2026); r1.createCell(1).setCellValue(1);
            r1.createCell(2).setCellValue(6); r1.createCell(3).setCellValue("VTT");
            r1.createCell(4).setCellValue(15.0);
            // Không tạo sheet LPS năm 2025

            List<String> warnings = new ArrayList<>();
            // Không được throw; phải có warning
            Double[][] result = read(workbook, earlyWeek, warnings);

            assertFalse(warnings.isEmpty(), "Phải có warning khi thiếu sheet năm trước");
            assertTrue(warnings.get(0).contains("LPS năm 2025"));
            // Tuần 7 vẫn có dữ liệu
            assertEquals(1.0, result[7][0]);
            // Các tuần năm trước phải là 0
            assertEquals(0.0, result[0][0]);
        }
    }

    @Test
    void ignoresRowsFromPrevYearThatDontBelongToChartRange() throws Exception {
        LocalDate earlyWeek = LocalDate.of(2026, 1, 5); // chartStart = 17/11/2025
        String earlySheet = "Lũy kế lỗi phát sinh năm 2026";

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet curr = workbook.createSheet(earlySheet);
            Row h1 = curr.createRow(0);
            h1.createCell(0).setCellValue("Năm"); h1.createCell(1).setCellValue("Tháng");
            h1.createCell(2).setCellValue("Ngày"); h1.createCell(3).setCellValue("Chủ dịch vụ");
            h1.createCell(4).setCellValue("Số lượng phản ảnh");

            Sheet prev = createPrevSheet(workbook, 2025);
            // Dòng trước chartStart (10/11/2025 < 17/11/2025)
            addPrevRow(prev, 1, "Tháng 11.2025", 10, "VTT", 50.0);
            // Dòng đúng range
            addPrevRow(prev, 2, "Tháng 11.2025", 20, "VTT", 30.0);

            List<String> warnings = new ArrayList<>();
            Double[][] result = read(workbook, earlyWeek, warnings);

            // Chỉ có dòng ngày 20/11 được tính
            assertEquals(1.0, result[0][0]);
            assertEquals(30.0, result[0][1]);
        }
    }

    @Test
    void skipsRowsWithInvalidMonthFormatInPrevSheet() throws Exception {
        LocalDate earlyWeek = LocalDate.of(2026, 1, 5);

        try (Workbook workbook = new XSSFWorkbook()) {
            // Sheet năm hiện tại với header đầy đủ (không có data row)
            Sheet curr = workbook.createSheet("Lũy kế lỗi phát sinh năm 2026");
            Row h = curr.createRow(0);
            h.createCell(0).setCellValue("Năm");
            h.createCell(1).setCellValue("Tháng");
            h.createCell(2).setCellValue("Ngày");
            h.createCell(3).setCellValue("Chủ dịch vụ");
            h.createCell(4).setCellValue("Số lượng phản ảnh");

            Sheet prev = createPrevSheet(workbook, 2025);
            // Định dạng tháng sai
            addPrevRow(prev, 1, "12/2025", 20, "VTT", 10.0);
            // Định dạng đúng
            addPrevRow(prev, 2, "Tháng 11.2025", 20, "VTT", 20.0);

            List<String> warnings = new ArrayList<>();
            Double[][] result = read(workbook, earlyWeek, warnings);

            // Dòng format sai bị bỏ qua và có warning
            assertFalse(warnings.isEmpty());
            assertTrue(warnings.stream().anyMatch(w -> w.contains("12/2025")));
            // Dòng đúng vẫn được đếm
            assertEquals(1.0, result[0][0]);
        }
    }
}
