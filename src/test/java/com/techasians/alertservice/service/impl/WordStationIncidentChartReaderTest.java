package com.techasians.alertservice.service.impl;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class WordStationIncidentChartReaderTest {
    private final WordIncidentChartReader reader = new WordIncidentChartReader();
    private final LocalDate week = LocalDate.of(2026, 9, 1);

    private WordSourceDocuments.ExcelSheet yearSheet(Workbook book, LocalDate week, boolean previous) {
        int year = week.plusDays(6).getYear();
        String name = previous ? "Tổng trạm " + (year - 1) : "Lũy kế trạm năm " + year;
        return new WordSourceDocuments.ExcelSheet(name, book.getSheet(name));
    }

    private Double[][] readStation(Workbook book, LocalDate week, List<String> warnings) {
        return reader.readStation(yearSheet(book, week, false), yearSheet(book, week, true), week, warnings);
    }

    private Double[][] readCable(Workbook book, LocalDate week, List<String> warnings) {
        return reader.readCable(yearSheet(book, week, false), yearSheet(book, week, true), week, warnings);
    }

    private String readTopCableProvinces(Workbook book, LocalDate week, List<String> warnings) {
        return reader.readTopCableProvinces(yearSheet(book, week, false), yearSheet(book, week, true), week, warnings);
    }

    @Test
    void aggregatesAllEightWeeksIncludingBothBoundariesWithoutCountingStationCodes() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = sheet(book, "Lũy kế trạm năm 2026", 0);
            LocalDate start = week.minusWeeks(7);
            for (int i = 0; i < 8; i++) {
                data(sheet, 2 + i * 2, 0, start.plusWeeks(i), i + 1, (i + 1) * 10);
                data(sheet, 3 + i * 2, 0, start.plusWeeks(i).plusDays(6), 2, 20);
            }
            sheet.getRow(2).createCell(10).setCellValue("TRAM1, TRAM2, TRAM3");
            data(sheet, 18, 0, start.minusDays(1), 999, 999);
            data(sheet, 19, 0, week.plusDays(7), 999, 999);
            List<String> warnings = new ArrayList<>();
            Double[][] values = readStation(book, week, warnings);
            for (int i = 0; i < 8; i++) assertArrayEquals(new Double[] { 2.0, i + 3.0, (i + 3) * 10.0 }, values[i]);
            assertTrue(warnings.isEmpty(), warnings.toString());
        }
    }

    @Test
    void combinesBothSheetsIntoSameWeekAcrossNewYearAndIgnoresWrongSheetYear() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet current = sheet(book, "Lũy kế trạm năm 2026", 0);
            Sheet previous = sheet(book, "Tổng trạm 2025", 1);
            LocalDate report = LocalDate.of(2026, 1, 5);
            data(previous, 2, 1, LocalDate.of(2025, 12, 31), 10, 100);
            data(current, 2, 0, LocalDate.of(2026, 1, 1), 20, 200);
            data(current, 3, 0, LocalDate.of(2026, 1, 5), 30, 300);
            data(current, 4, 0, LocalDate.of(2025, 12, 31), 999, 999); // Không đếm trùng ở sheet 2026.
            data(previous, 3, 1, report.minusWeeks(7).minusDays(1), 999, 999);
            List<String> warnings = new ArrayList<>();
            Double[][] values = readStation(book, report, warnings);
            assertArrayEquals(new Double[] { 2.0, 30.0, 300.0 }, values[6]);
            assertArrayEquals(new Double[] { 1.0, 30.0, 300.0 }, values[7]);
            assertTrue(warnings.isEmpty());
        }
    }

    @Test
    void readsNextYearWhenReportingWeekItselfCrossesNewYear() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet current = sheet(book, "Tổng trạm 2026", 0);
            Sheet next = sheet(book, "Lũy kế trạm năm 2027", 0);
            data(current, 2, 0, LocalDate.of(2026, 12, 31), 3, 30);
            data(next, 2, 0, LocalDate.of(2027, 1, 1), 4, 40);
            List<String> warnings = new ArrayList<>();
            assertArrayEquals(new Double[] { 2.0, 7.0, 70.0 },
                    readStation(book, LocalDate.of(2026, 12, 30), warnings)[7]);
            assertTrue(warnings.isEmpty());
        }
    }

    @Test
    void countsIncidentDespiteBadTotalsAndSkipsInvalidDatesWithWarnings() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = sheet(book, "Lũy kế trạm năm 2026", 0);
            data(sheet, 2, 0, week, 10, 100);
            sheet.getRow(2).getCell(3).setBlank();
            data(sheet, 3, 0, week.plusDays(1), 20, -1);
            sheet.getRow(3).getCell(3).setCellFormula("10+10");
            data(sheet, 4, 0, week, 999, 999);
            sheet.getRow(4).getCell(1).setCellValue(13);
            data(sheet, 5, 0, week, 999, 999);
            sheet.getRow(5).getCell(2).setCellValue(1.5);
            WordStationIncidentReaderTest.evaluate(book);
            List<String> warnings = new ArrayList<>();
            assertArrayEquals(new Double[] { 2.0, 20.0, 100.0 }, readStation(book, week, warnings)[7]);
            assertEquals(4, warnings.size());
        }
    }

    @Test
    void sumsTextNumbersAndCachedTextFormulasAlongsideNumericCells() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = sheet(book, "Lũy kế trạm năm 2026", 0);
            data(sheet, 2, 0, week, 0, 0);
            sheet.getRow(2).getCell(3).setCellValue("19");
            sheet.getRow(2).getCell(4).setCellValue("59");
            data(sheet, 3, 0, week.plusDays(6), 0, 0);
            sheet.getRow(3).getCell(3).setCellValue("\u00a021\u00a0");
            sheet.getRow(3).getCell(4).setCellValue(" 141 ");
            data(sheet, 4, 0, week.plusDays(1), 0, 0);
            sheet.getRow(4).getCell(3).setCellFormula("\"10\"");
            sheet.getRow(4).getCell(4).setCellFormula("\"300\"");
            data(sheet, 5, 0, week.plusDays(2), 10, 100);
            WordStationIncidentReaderTest.evaluate(book);
            assertEquals(CellType.STRING, sheet.getRow(4).getCell(3).getCachedFormulaResultType());
            List<String> warnings = new ArrayList<>();
            assertArrayEquals(new Double[] {4.0, 60.0, 600.0}, readStation(book, week, warnings)[7]);
            assertTrue(warnings.isEmpty(), warnings.toString());
        }
    }

    @Test
    void stillWarnsForInvalidNegativeOrNonFiniteTextTotalsWithoutLosingIncidentCounts() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = sheet(book, "Lũy kế trạm năm 2026", 0);
            String[] invalid = {"", "không có dữ liệu", "-1", "NaN", "Infinity", "1e309"};
            for (int i = 0; i < invalid.length; i++) {
                data(sheet, i + 2, 0, week, 10, 100);
                sheet.getRow(i + 2).getCell(3).setCellValue(invalid[i]);
                sheet.getRow(i + 2).getCell(4).setCellValue(invalid[i]);
            }
            List<String> warnings = new ArrayList<>();
            assertArrayEquals(new Double[] {6.0, 0.0, 0.0}, readStation(book, week, warnings)[7]);
            assertEquals(12, warnings.size());
        }
    }

    @Test
    void requiresCurrentSheetAndBothTotalHeadersButWarnsForMissingPriorYear() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            assertThrows(WordIncidentChartReader.MissingChartDataException.class,
                    () -> readStation(book, week, new ArrayList<>()));
            Sheet sheet = sheet(book, "Lũy kế trạm năm 2026", 0);
            List<String> warnings = new ArrayList<>();
            readStation(book, LocalDate.of(2026, 1, 5), warnings);
            assertTrue(warnings.get(0).contains("2025"));
            sheet.getRow(1).getCell(4).setBlank();
            assertThrows(WordIncidentChartReader.MissingChartDataException.class,
                    () -> readStation(book, week, new ArrayList<>()));
        }
    }

    @Test
    void returnsZeroForWeeksWithoutIncidents() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            sheet(book, "Lũy kế trạm năm 2026", 0);
            List<String> warnings = new ArrayList<>();
            for (Double[] row : readStation(book, week, warnings)) assertArrayEquals(new Double[] { 0.0, 0.0, 0.0 }, row);
            assertTrue(warnings.isEmpty());
        }
    }

    @Test
    void readsMarchTextTotalsFromActualWorkbookInsteadOfReportingZero() throws Exception {
        Path fixture = Paths.get("documents", "File 1", "1. Báo cáo Lỗi phát sinh tuần_ thuynt14",
                "T36. Bao cao tuan 01.09 -07.09.xlsx");
        assumeTrue(Files.isRegularFile(fixture), "Local workbook is optional outside this workspace");
        try (InputStream input = Files.newInputStream(fixture); Workbook book = WorkbookFactory.create(input)) {
            List<String> warnings = new ArrayList<>();
            Double[][] values = readStation(book, LocalDate.of(2026, 5, 5), warnings);
            assertArrayEquals(new Double[] {366.0, 6906.0, 432037.0}, values[0], "17/03 - 23/03/2026");
            assertArrayEquals(new Double[] {406.0, 7279.0, 656451.0}, values[1], "24/03 - 30/03/2026");
            assertArrayEquals(new Double[] {479.0, 7541.0, 990907.0}, values[2], "31/03 - 06/04/2026");
        }
    }

    static Sheet sheet(Workbook book, String name, int shift) {
        Sheet sheet = book.createSheet(name);
        Row top = sheet.createRow(0), sub = sheet.createRow(1);
        top.createCell(shift).setCellValue("Năm");
        top.createCell(shift + 1).setCellValue(" Tháng\n");
        top.createCell(shift + 2).setCellValue("Ngày");
        sub.createCell(shift + 3).setCellValue("Tổng PA");
        sub.createCell(shift + 4).setCellValue("Tổng ảnh hưởng");
        return sheet;
    }

    @Test
    void cableChartFiltersExactCausesAcrossEightWeeksAndIncludesAllCableSegments() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = cableSheet(book, "Lũy kế trạm năm 2026", 0);
            LocalDate start = week.minusWeeks(7);
            for (int i = 0; i < 8; i++) {
                cableData(sheet, 2 + 3 * i, 0, start.plusWeeks(i), " Cháy\ncáp ", "hcm", 10, 100);
                cableData(sheet, 3 + 3 * i, 0, start.plusWeeks(i).plusDays(6), "ĐỨT CÁP", "HNI", 20, 200);
                sheet.getRow(3 + 3 * i).createCell(7).setCellValue("Cáp thuê bao");
                cableData(sheet, 4 + 3 * i, 0, start.plusWeeks(i), "Đứt cáp khác", "DNI", 999, 999);
            }
            cableData(sheet, 26, 0, start.minusDays(1), "Cháy cáp", "DNI", 999, 999);
            cableData(sheet, 27, 0, week.plusDays(7), "Đứt cáp", "DNI", 999, 999);
            sheet.getRow(2).getCell(3).setCellValue("10");
            sheet.getRow(2).getCell(4).setCellFormula("\"100\"");
            WordStationIncidentReaderTest.evaluate(book);
            List<String> warnings = new ArrayList<>();
            for (Double[] row : readCable(book, week, warnings)) {
                assertArrayEquals(new Double[] {2.0, 30.0, 300.0}, row);
            }
            assertTrue(warnings.isEmpty(), warnings.toString());
        }
    }

    @Test
    void cableChartCombinesPriorYearAndDoesNotCountRowsFromWrongSheetYear() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet current = cableSheet(book, "Lũy kế trạm năm 2026", 0);
            Sheet previous = cableSheet(book, "Tổng trạm 2025", 1);
            LocalDate report = LocalDate.of(2026, 1, 5);
            cableData(previous, 2, 1, LocalDate.of(2025, 12, 31), "Cháy cáp", "HCM", 10, 100);
            cableData(current, 2, 0, LocalDate.of(2026, 1, 1), "Đứt cáp", "HNI", 20, 200);
            cableData(current, 3, 0, report, "Đứt cáp", "DNI", 30, 300);
            cableData(current, 4, 0, LocalDate.of(2025, 12, 31), "Đứt cáp", "HCM", 999, 999);
            List<String> warnings = new ArrayList<>();
            Double[][] values = readCable(book, report, warnings);
            assertArrayEquals(new Double[] {2.0, 30.0, 300.0}, values[6]);
            assertArrayEquals(new Double[] {1.0, 30.0, 300.0}, values[7]);
            assertEquals("DNI – 1SC", readTopCableProvinces(book, report, warnings));
            assertTrue(warnings.isEmpty(), warnings.toString());
        }
    }

    @Test
    void topCableProvincesCountsOnlyReportingWeekGroupsCodesAndBreaksTiesAlphabetically() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = cableSheet(book, "Lũy kế trạm năm 2026", 0);
            String[] provinces = {" hcm\u00a0", "HCM", "HNI", "dni", "DNI", "AGG", ""};
            for (int i = 0; i < provinces.length; i++) {
                cableData(sheet, 2 + i, 0, week.plusDays(i), "Đứt cáp", provinces[i], 1, 10);
            }
            cableData(sheet, 9, 0, week.minusDays(1), "Cháy cáp", "HNI", 999, 999);
            cableData(sheet, 10, 0, week.plusDays(7), "Cháy cáp", "HNI", 999, 999);
            cableData(sheet, 11, 0, week, "Mất điện", "HNI", 999, 999);
            // Thống kê tỉnh không phụ thuộc các cột tổng PA/ảnh hưởng.
            sheet.getRow(1).getCell(3).setBlank(); sheet.getRow(1).getCell(4).setBlank();
            List<String> warnings = new ArrayList<>();
            assertEquals("DNI – 2SC; HCM – 2SC; AGG – 1SC", readTopCableProvinces(book, week, warnings));
            assertEquals(1, warnings.size());
            assertTrue(warnings.get(0).contains("thiếu mã tỉnh"));
        }
    }

    @Test
    void cableReadersRequireCauseAndTopProvinceAndCombineReportingWeekAcrossNewYear() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet current = cableSheet(book, "Tổng trạm 2026", 0);
            Sheet next = cableSheet(book, "Lũy kế trạm năm 2027", 1);
            LocalDate report = LocalDate.of(2026, 12, 30);
            cableData(current, 2, 0, report, "Cháy cáp", "HCM", 3, 30);
            cableData(next, 2, 1, LocalDate.of(2027, 1, 1), "Đứt cáp", "hcm", 4, 40);
            List<String> warnings = new ArrayList<>();
            assertArrayEquals(new Double[] {2.0, 7.0, 70.0}, readCable(book, report, warnings)[7]);
            assertEquals("HCM – 2SC", readTopCableProvinces(book, report, warnings));
            current.getRow(0).getCell(6).setBlank();
            assertThrows(WordIncidentChartReader.MissingChartDataException.class,
                    () -> readTopCableProvinces(book, report, warnings));
            next.getRow(0).getCell(6).setBlank();
            assertThrows(WordIncidentChartReader.MissingChartDataException.class,
                    () -> readCable(book, report, warnings));
        }
    }

    static Sheet cableSheet(Workbook book, String name, int shift) {
        Sheet sheet = sheet(book, name, shift);
        sheet.getRow(0).createCell(shift + 5).setCellValue(" Nguyên nhân cấp 1 ");
        sheet.getRow(0).createCell(shift + 6).setCellValue("Tỉnh");
        return sheet;
    }

    static void cableData(Sheet sheet, int index, int shift, LocalDate date, String cause, String province,
                          double pa, double affected) {
        data(sheet, index, shift, date, pa, affected);
        sheet.getRow(index).createCell(shift + 5).setCellValue(cause);
        sheet.getRow(index).createCell(shift + 6).setCellValue(province);
    }

    static void data(Sheet sheet, int index, int shift, LocalDate date, double pa, double affected) {
        Row row = sheet.createRow(index);
        row.createCell(shift).setCellValue(date.getYear());
        row.createCell(shift + 1).setCellValue(String.format("%02d", date.getMonthValue()));
        row.createCell(shift + 2).setCellValue(date.getDayOfMonth());
        row.createCell(shift + 3).setCellValue(pa);
        row.createCell(shift + 4).setCellValue(affected);
    }
}
