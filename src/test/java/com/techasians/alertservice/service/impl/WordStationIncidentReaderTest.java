package com.techasians.alertservice.service.impl;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class WordStationIncidentReaderTest {
    private final WordSystemIncidentReader reader = new WordSystemIncidentReader();
    private final LocalDate week = LocalDate.of(2026, 9, 1);

    @Test
    void readsThreeMetricsAndDuplicateRateHeadersOnlyInsideStationTable() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = book.createSheet("TH");
            table(sheet, 7, 6);
            // Bảng cạnh bên có cùng nhãn dòng nhưng không được dùng.
            table(sheet, 7, 0);
            sheet.getRow(7).getCell(0).setCellValue("Tổng lỗi phát sinh");
            sheet.getRow(8).getCell(2).setCellValue(999);
            evaluate(book);
            WordSystemIncidentReader.StationResult result = reader.readStation(book, "TH", week);
            assertEquals("318", result.getIncidentCount());
            assertEquals("5.301", result.getComplaintCount());
            assertEquals("143.987", result.getAffectedCount());
            assertEquals("giảm 12% (-45SC)", result.getIncidentChange());
            assertEquals("giảm 27% (-1.919PA)", result.getComplaintChange());
            assertEquals("giảm 57% (-187.154TB)", result.getAffectedChange());
        }
    }

    @Test
    void supportsMergedTitleAndNormalizedHeaders() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = book.createSheet("TH");
            table(sheet, 1, 0);
            sheet.createRow(0).createCell(0).setCellValue(" SO SÁNH\nLỖI TRẠM\u00a0 ");
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));
            sheet.getRow(1).getCell(0).setBlank();
            sheet.getRow(1).getCell(3).setCellValue("So sánh");
            evaluate(book);
            assertEquals("5.301", reader.readStation(book, "TH", week).getComplaintCount());
        }
    }

    @Test
    void selectsUniqueReportingPeriodAndRejectsDuplicates() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = book.createSheet("TH");
            table(sheet, 0, 0);
            table(sheet, 8, 0);
            evaluate(book);
            assertMissing(book, week);
            sheet.getRow(8).getCell(1).setCellValue("18/08 - 24/08/2026");
            sheet.getRow(8).getCell(2).setCellValue("25/08 - 31/08/2026");
            assertEquals("318", reader.readStation(book, "TH", week).getIncidentCount());
            assertMissing(book, week.plusWeeks(1));
        }
    }

    @Test
    void handlesZeroDenominatorWithoutInventingPercentage() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = book.createSheet("TH");
            table(sheet, 0, 0);
            sheet.getRow(1).getCell(1).setCellValue(0);
            sheet.getRow(1).getCell(2).setCellValue(5);
            sheet.getRow(2).getCell(1).setCellValue(0);
            sheet.getRow(2).getCell(2).setCellValue(0);
            evaluate(book);
            WordSystemIncidentReader.StationResult result = reader.readStation(book, "TH", week);
            assertEquals("tăng 5SC (tuần trước không phát sinh)", result.getIncidentChange());
            assertEquals("không có biến động", result.getComplaintChange());
        }
    }

    @Test
    void rejectsMissingRowsBorrowedHeadersAndInvalidOrInconsistentNumbers() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            assertMissing(book, week);
            Sheet sheet = book.createSheet("TH");
            table(sheet, 0, 0);
            evaluate(book);
            Cell count = sheet.getRow(1).getCell(2);
            count.setCellValue(-1); assertMissing(book, week);
            count.setCellValue(318.5); assertMissing(book, week);
            count.setBlank(); assertMissing(book, week);
            count.setCellValue(318);
            Cell difference = sheet.getRow(1).getCell(3);
            difference.removeFormula(); difference.setCellValue(-44); assertMissing(book, week);
            difference.setCellValue(-45);
            Cell rate = sheet.getRow(1).getCell(4);
            rate.removeFormula(); rate.setCellValue(0.12); assertMissing(book, week);
            rate.setCellValue(-0.12);
            sheet.getRow(3).getCell(0).setBlank(); assertMissing(book, week);
            sheet.getRow(3).getCell(0).setCellValue("TB ảnh hưởng");
            sheet.getRow(0).getCell(4).setBlank();
            table(sheet, 0, 6);
            sheet.getRow(0).getCell(6).setCellValue("Bảng khác");
            assertMissing(book, week);
        }
    }

    @Test
    void recognizesReversedWeekColumnsAcrossNewYear() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = book.createSheet("TH");
            table(sheet, 0, 0);
            sheet.getRow(0).getCell(1).setCellValue("30/12/2026 - 05/01/2027");
            sheet.getRow(0).getCell(2).setCellValue("23/12 - 29/12/2026");
            for (int i = 1; i <= 3; i++) {
                Row row = sheet.getRow(i);
                double previous = row.getCell(1).getNumericCellValue();
                row.getCell(1).setCellValue(row.getCell(2).getNumericCellValue());
                row.getCell(2).setCellValue(previous);
                row.getCell(3).setCellFormula("B" + (i + 1) + "-C" + (i + 1));
                row.getCell(4).setCellFormula("D" + (i + 1) + "/C" + (i + 1));
            }
            evaluate(book);
            assertEquals("giảm 12% (-45SC)", reader.readStation(book, "TH", LocalDate.of(2026, 12, 30)).getIncidentChange());
        }
    }

    private void assertMissing(Workbook book, LocalDate week) {
        assertThrows(WordSystemIncidentReader.MissingIncidentDataException.class, () -> reader.readStation(book, "TH", week));
    }

    @Test
    void readsCableComparisonIndependentlyOfAdjacentStationAndSystemTables() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = book.createSheet("TH");
            table(sheet, 0, 0);
            cableTable(sheet, 0, 6);
            evaluate(book);
            WordSystemIncidentReader.StationResult result = reader.readCable(book, "TH", week);
            assertEquals("147", result.getIncidentCount());
            assertEquals("3.013", result.getComplaintCount());
            assertEquals("17.172", result.getAffectedCount());
            assertEquals("giảm 23% (-44SC)", result.getIncidentChange());
            assertEquals("giảm 31% (-1.364PA)", result.getComplaintChange());
            assertEquals("giảm 35% (-9.253TB)", result.getAffectedChange());
            assertEquals("318", reader.readStation(book, "TH", week).getIncidentCount());
            sheet.getRow(3).getCell(6).setBlank();
            assertThrows(WordSystemIncidentReader.MissingIncidentDataException.class,
                    () -> reader.readCable(book, "TH", week));
        }
    }

    static void cableTable(Sheet sheet, int firstRow, int firstColumn) {
        table(sheet, firstRow, firstColumn);
        sheet.getRow(firstRow).getCell(firstColumn).setCellValue("So sánh Sự cố đứt cáp, cháy cáp trục, cáp nhánh");
        long[] previous = {191, 4377, 26425}, current = {147, 3013, 17172};
        for (int i = 0; i < 3; i++) {
            sheet.getRow(firstRow + 1 + i).getCell(firstColumn + 1).setCellValue(previous[i]);
            sheet.getRow(firstRow + 1 + i).getCell(firstColumn + 2).setCellValue(current[i]);
        }
    }

    static void evaluate(Workbook book) { book.getCreationHelper().createFormulaEvaluator().evaluateAll(); }

    static void table(Sheet sheet, int firstRow, int firstColumn) {
        String[] labels = { "So sánh lỗi trạm", "25/08 - 31/08/2026", "01/09 - 07/09/2026",
                "Tỷ lệ tăng giảm", "Tỷ lệ tăng giảm" };
        Row header = sheet.getRow(firstRow);
        if (header == null) header = sheet.createRow(firstRow);
        for (int i = 0; i < labels.length; i++) header.createCell(firstColumn + i).setCellValue(labels[i]);
        String[] rows = { "Số lượng", "PA", "TB ảnh hưởng" };
        long[] previous = { 363, 7220, 331141 }, current = { 318, 5301, 143987 };
        CellStyle percent = sheet.getWorkbook().createCellStyle();
        percent.setDataFormat((short) 9);
        for (int i = 0; i < rows.length; i++) {
            Row row = sheet.getRow(firstRow + i + 1);
            if (row == null) row = sheet.createRow(firstRow + i + 1);
            row.createCell(firstColumn).setCellValue(rows[i]);
            Cell old = row.createCell(firstColumn + 1); old.setCellValue(previous[i]);
            Cell now = row.createCell(firstColumn + 2); now.setCellValue(current[i]);
            Cell delta = row.createCell(firstColumn + 3); delta.setCellFormula(now.getAddress() + "-" + old.getAddress());
            Cell rate = row.createCell(firstColumn + 4); rate.setCellFormula(delta.getAddress() + "/" + old.getAddress());
            rate.setCellStyle(percent);
        }
    }
}
