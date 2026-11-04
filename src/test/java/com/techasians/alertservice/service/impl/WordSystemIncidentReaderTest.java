package com.techasians.alertservice.service.impl;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.*;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class WordSystemIncidentReaderTest {
    private final WordSystemIncidentReader reader = new WordSystemIncidentReader();
    private final LocalDate week = LocalDate.of(2026, 9, 1);

    @Test
    void readsOnlyAnchoredTableDespiteIdenticalHeadersBesideAndBelowIt() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = book.createSheet("TH");
            table(sheet, 20, 3, 3, 3, 136, 37);
            table(sheet, 20, 9, 40, 80, 10, 90);
            sheet.getRow(20).getCell(9).setCellValue("So sánh lỗi trạm");
            table(sheet, 28, 3, 40, 80, 10, 90);
            sheet.getRow(28).getCell(3).setCellValue("Sự cố VTT");
            evaluate(book);
            WordSystemIncidentReader.Result result = reader.read(book, "TH", week);
            assertEquals(3, result.getIncidentCount());
            assertEquals(37, result.getComplaintCount());
            assertEquals("không có biến động", result.getIncidentChange());
            assertEquals("PAKH giảm 73% (99PA)", result.getComplaintChange());
        }
    }

    @Test
    void describesIncreaseAndIncidentDecrease() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            table(book.createSheet("TH"), 0, 0, 5, 3, 100, 125);
            evaluate(book);
            WordSystemIncidentReader.Result result = reader.read(book, "TH", week);
            assertEquals("giảm 2 SC", result.getIncidentChange());
            assertEquals("PAKH tăng 25% (25PA)", result.getComplaintChange());
        }
    }

    @Test
    void describesIncidentIncreaseAndUnchangedComplaintsIncludingZeroDenominator() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            table(book.createSheet("TH"), 0, 0, 3, 5, 0, 0);
            evaluate(book);
            WordSystemIncidentReader.Result result = reader.read(book, "TH", week);
            assertEquals("tăng 2 SC", result.getIncidentChange());
            assertEquals("PAKH không có thay đổi", result.getComplaintChange());
        }
    }

    @Test
    void supportsPlainNumberAndTextPercentagesWithoutGuessingTheirUnits() throws Exception {
        for (String representation : new String[] { "numeric", "-73", "-73%", "-72,794%" }) {
            try (Workbook book = new XSSFWorkbook()) {
                Sheet sheet = book.createSheet("TH");
                table(sheet, 0, 0, 3, 3, 136, 37);
                evaluate(book);
                Cell rate = sheet.getRow(2).getCell(4);
                rate.removeFormula();
                rate.setCellStyle(book.getCellStyleAt(0));
                if ("numeric".equals(representation)) rate.setCellValue(-73);
                else rate.setCellValue(representation);
                assertEquals("PAKH giảm 73% (99PA)", reader.read(book, "TH", week).getComplaintChange());
            }
        }
    }

    @Test
    void resolvesDatesAcrossYearsAndRecognizesColumnOrder() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = book.createSheet("TH");
            table(sheet, 0, 0, 3, 3, 136, 37);
            // Đảo vị trí hai cột và dùng kỳ báo cáo vắt năm.
            sheet.getRow(0).getCell(1).setCellValue("30/12/2026 - 05/01/2027");
            sheet.getRow(0).getCell(2).setCellValue("23/12 - 29/12/2026");
            sheet.getRow(2).getCell(1).setCellValue(37);
            sheet.getRow(2).getCell(2).setCellValue(136);
            sheet.getRow(2).getCell(3).setCellFormula("B3-C3");
            sheet.getRow(2).getCell(4).setCellFormula("D3/C3");
            evaluate(book);
            assertEquals(37, reader.read(book, "TH", LocalDate.of(2026, 12, 30)).getComplaintCount());
        }
    }

    @Test
    void supportsMergedTitleOnSeparateRowAndNormalizedHeaders() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = book.createSheet("TH");
            table(sheet, 1, 1, 3, 3, 136, 37);
            sheet.createRow(0).createCell(1).setCellValue("  TỔNG LỖI\nPHÁT SINH\u00a0 ");
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 1, 5));
            sheet.getRow(1).getCell(1).setCellValue("");
            sheet.getRow(1).getCell(2).setCellValue("Tuần trước");
            sheet.getRow(1).getCell(3).setCellValue("Tuần này");
            sheet.getRow(1).getCell(4).setCellValue(" So\n sánh ");
            evaluate(book);
            assertEquals("PAKH giảm 73% (99PA)", reader.read(book, "TH", week).getComplaintChange());
        }
    }

    @Test
    void rejectsDuplicateTablesButSelectsUniqueMatchingPeriod() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = book.createSheet("TH");
            table(sheet, 0, 0, 3, 3, 136, 37);
            table(sheet, 8, 0, 4, 5, 10, 20);
            evaluate(book);
            assertMissing(book);
            sheet.getRow(8).getCell(1).setCellValue("18/08 - 24/08/2026");
            sheet.getRow(8).getCell(2).setCellValue("25/08 - 31/08/2026");
            assertEquals(37, reader.read(book, "TH", week).getComplaintCount());
            assertThrows(WordSystemIncidentReader.MissingIncidentDataException.class,
                    () -> reader.read(book, "TH", week.plusWeeks(2)));
        }
    }

    @Test
    void doesNotBorrowMissingHeadersOrRowsFromAnotherTable() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = book.createSheet("TH");
            table(sheet, 0, 0, 3, 3, 136, 37);
            table(sheet, 0, 6, 10, 20, 30, 40);
            sheet.getRow(0).getCell(6).setCellValue("So sánh lỗi trạm");
            evaluate(book);
            sheet.getRow(0).getCell(4).setCellValue("");
            assertMissing(book);
            sheet.getRow(0).getCell(4).setCellValue("Tỷ lệ tăng giảm");
            sheet.getRow(2).getCell(0).setCellValue("");
            table(sheet, 7, 0, 10, 20, 30, 40);
            sheet.getRow(7).getCell(0).setCellValue("Sự cố VTT");
            assertMissing(book);
        }
    }

    @Test
    void rejectsMissingErroneousOrInconsistentDataInsteadOfAssumingZero() throws Exception {
        try (Workbook book = new XSSFWorkbook()) {
            assertMissing(book);
            Sheet sheet = book.createSheet("TH");
            table(sheet, 0, 0, 3, 3, 136, 37);
            evaluate(book);
            Cell count = sheet.getRow(2).getCell(2);
            count.setBlank();
            assertMissing(book);
            count.setCellValue(37.5);
            assertMissing(book);
            count.setCellValue(37);
            sheet.getRow(2).getCell(3).removeFormula();
            sheet.getRow(2).getCell(3).setCellValue(-98);
            assertMissing(book);
            sheet.getRow(2).getCell(3).setCellValue(-99);
            sheet.getRow(2).getCell(4).removeFormula();
            sheet.getRow(2).getCell(4).setCellValue(0.73);
            assertMissing(book);
            sheet.getRow(2).getCell(4).setCellErrorValue(FormulaError.DIV0.getCode());
            assertMissing(book);
        }
    }

    @Test
    void readsAttachedWorkbookWithCachedFormulaResults() throws Exception {
        Path path = Paths.get("documents", "File 1", "1. Báo cáo Lỗi phát sinh tuần_ thuynt14",
                "T36. Bao cao tuan 01.09 -07.09.xlsx");
        assumeTrue(Files.isRegularFile(path), "Local sample workbook is optional outside this workspace");
        try (InputStream input = Files.newInputStream(path); Workbook book = WorkbookFactory.create(input)) {
            WordSystemIncidentReader.Result result = reader.read(book, "TH", week);
            assertEquals(3, result.getIncidentCount());
            assertEquals(37, result.getComplaintCount());
            assertEquals("không có biến động", result.getIncidentChange());
            assertEquals("PAKH giảm 73% (99PA)", result.getComplaintChange());
        }
    }

    private void assertMissing(Workbook book) {
        assertThrows(WordSystemIncidentReader.MissingIncidentDataException.class,
                () -> reader.read(book, "TH", week));
    }

    private void evaluate(Workbook book) { book.getCreationHelper().createFormulaEvaluator().evaluateAll(); }

    private void table(Sheet sheet, int rowIndex, int column, int oldIncidents, int newIncidents,
                       int oldComplaints, int newComplaints) {
        Row header = row(sheet, rowIndex);
        String[] names = { "Tổng lỗi phát sinh", "25/08 - 31/08/2026", "01/09 - 07/09/2026",
                "So sánh", "Tỷ lệ tăng giảm" };
        for (int i = 0; i < names.length; i++) header.createCell(column + i).setCellValue(names[i]);
        for (int i = 1; i <= 2; i++) {
            Row data = row(sheet, rowIndex + i);
            data.createCell(column).setCellValue(i == 1 ? "Số lượng" : "PA");
            Cell oldValue = data.createCell(column + 1);
            oldValue.setCellValue(i == 1 ? oldIncidents : oldComplaints);
            Cell newValue = data.createCell(column + 2);
            newValue.setCellValue(i == 1 ? newIncidents : newComplaints);
            Cell diff = data.createCell(column + 3);
            diff.setCellFormula(newValue.getAddress() + "-" + oldValue.getAddress());
            Cell rate = data.createCell(column + 4);
            rate.setCellFormula(diff.getAddress() + "/" + oldValue.getAddress());
            CellStyle style = sheet.getWorkbook().createCellStyle();
            style.setDataFormat((short) 9);
            rate.setCellStyle(style);
        }
    }

    private Row row(Sheet sheet, int index) {
        return sheet.getRow(index) == null ? sheet.createRow(index) : sheet.getRow(index);
    }
}
