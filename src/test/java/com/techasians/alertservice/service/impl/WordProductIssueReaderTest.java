package com.techasians.alertservice.service.impl;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class WordProductIssueReaderTest {
    @Test
    void findsHeaderAndDataAfterMovingTableDownAndRight() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Bất cập DV");
            row(sheet, 0, "Ngày phát hiện", "Tiến độ", "Nội dung", "Kết quả");
            row(sheet, 1, "1/9/2026", "Đã hoàn thành", "Thực trạng: Nội dung", "Giải pháp");
            sheet.shiftRows(0, 1, 75);
            sheet.shiftColumns(0, 3, 6);
            WordProductIssueReader.Result result = new WordProductIssueReader()
                    .read(workbook, "Bất cập DV", LocalDate.of(2026, 9, 1));
            assertEquals(1, result.getIssues().size());
            assertEquals("Giải pháp", result.getIssues().get(0).getResult());
            assertEquals("100", result.getOptimizedRate());
        }
    }

    @Test
    void parsesDisplayedShortYearAsDayMonthYearForNumericAndCachedFormulaCells() throws Exception {
        for (boolean formula : new boolean[] {false, true}) {
            try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("Bất cập DV");
                row(sheet, 0, "Ngày phát hiện", "Nội dung", "Tiến độ");
                row(sheet, 1, "", "Ngày hiển thị 6/5/26", "Đang thực hiện");
                org.apache.poi.ss.usermodel.Cell dateCell = sheet.getRow(1).getCell(0);
                if (formula) dateCell.setCellFormula("46178");
                else dateCell.setCellValue(46178);
                org.apache.poi.ss.usermodel.CellStyle dateStyle = workbook.createCellStyle();
                dateStyle.setDataFormat(workbook.createDataFormat().getFormat("m/d/yy"));
                dateCell.setCellStyle(dateStyle);
                workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();

                WordProductIssueReader reader = new WordProductIssueReader();
                assertEquals(1, reader.read(workbook, "Bất cập DV", LocalDate.of(2026, 5, 6)).getIssues().size());
                assertTrue(reader.read(workbook, "Bất cập DV", LocalDate.of(2026, 5, 7)).getIssues().isEmpty());
                assertTrue(reader.read(workbook, "Bất cập DV", LocalDate.of(2026, 6, 1)).getIssues().isEmpty());
            }
        }
    }

    @Test
    void readsCachedFormulaResultsWithoutRecalculationAndPreservesCellFormatting() throws Exception {
        for (boolean date1904 : new boolean[] {false, true}) {
            try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                workbook.getCTWorkbook().getWorkbookPr().setDate1904(date1904);
                Sheet sheet = workbook.createSheet("Bất cập DV");
                row(sheet, 0, "Ngày phát hiện", "Tiến độ", "Nội dung", "Kết quả", "Tên bất cập/lỗi");
                sheet.getRow(0).getCell(0).setCellFormula("\"Ngày phát hiện\"");
                row(sheet, 1, "", "", "", "", "");
                sheet.getRow(1).createCell(6).setCellValue("Đã hoàn thành");
                double serial = org.apache.poi.ss.usermodel.DateUtil.getExcelDate(LocalDate.of(2026, 9, 1), date1904);
                sheet.getRow(1).getCell(0).setCellFormula(Double.toString(serial));
                org.apache.poi.ss.usermodel.CellStyle dateStyle = workbook.createCellStyle();
                dateStyle.setDataFormat(workbook.createDataFormat().getFormat("d/m/yyyy"));
                sheet.getRow(1).getCell(0).setCellStyle(dateStyle);
                sheet.getRow(1).getCell(1).setCellFormula("G2");
                sheet.getRow(1).getCell(2).setCellFormula("\"Thực trạng: Nội dung trong cache.\"");
                sheet.getRow(1).getCell(3).setCellFormula("1/4");
                org.apache.poi.ss.usermodel.CellStyle percentStyle = workbook.createCellStyle();
                percentStyle.setDataFormat(workbook.createDataFormat().getFormat("0.0%"));
                sheet.getRow(1).getCell(3).setCellStyle(percentStyle);
                row(sheet, 2, "7/9/2026", "Đang thực hiện", "Chi tiết", "", "");
                sheet.getRow(2).getCell(3).setCellFormula("1/0");
                sheet.getRow(2).getCell(4).setCellFormula("TRUE()");
                row(sheet, 3, "", "Đã hoàn thành", "Ngày lỗi", "", "");
                sheet.getRow(3).getCell(0).setCellFormula("1/0");
                row(sheet, 4, "", "Đã hoàn thành", "Ngày boolean", "", "");
                sheet.getRow(4).getCell(0).setCellFormula("TRUE()");
                workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
                sheet.getRow(1).getCell(6).setCellValue("Không khả thi");

                WordProductIssueReader.Result result = new WordProductIssueReader()
                        .read(workbook, "Bất cập DV", LocalDate.of(2026, 9, 1));
                assertEquals(2, result.getIssues().size());
                assertEquals(1, result.getCompleted());
                assertEquals(1, result.getInProgress());
                assertEquals("Thực trạng: Nội dung trong cache.", result.getIssues().get(0).getContent());
                assertEquals("25,0%", result.getIssues().get(0).getResult());
                assertEquals("TRUE", result.getIssues().get(1).getTitle());
                assertEquals("#DIV/0!", result.getIssues().get(1).getResult());
                assertTrue(result.getWarnings().isEmpty());
                assertEquals(org.apache.poi.ss.usermodel.CellType.FORMULA, sheet.getRow(1).getCell(1).getCellType());
            }
        }
    }

    @Test
    void filtersInclusiveWeekAndUsesProgressInsteadOfResult() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Bất cập DV");
            row(sheet, 0, "Tên bất cập/lỗi", "Ngày phát hiện", "Tiến độ", "Nội dung", "Kết quả");
            row(sheet, 1, "Đầu tuần", "1/9/2026", "Đã hoàn thành", "Chi tiết", "Đang đánh giá");
            row(sheet, 2, "Cuối tuần", "7/9/2026", "Đang thực hiện", "Chi tiết", "Đã hoàn thành");
            row(sheet, 3, "Ngoài tuần", "8/9/2026", "Đang đánh giá", "Chi tiết", "");
            row(sheet, 4, "Chưa phân loại", "3/9/2026", "Không khả thi", "Chi tiết", "");
            row(sheet, 5, "Trước tuần", "31/8/2026", "Đã hoàn thành", "Chi tiết", "");
            WordProductIssueReader.Result result = new WordProductIssueReader()
                    .read(workbook, "Bất cập DV", LocalDate.of(2026, 9, 1));
            assertEquals(3, result.getIssues().size());
            assertEquals(1, result.getCompleted());
            assertEquals(1, result.getInProgress());
            assertEquals(0, result.getEvaluating());
            assertEquals(1, result.getInfeasible());
            assertEquals(0, result.getUnclassified());
            assertTrue(result.getWarnings().isEmpty());
            assertEquals(2, result.getOptimized());
            assertEquals("66,7", result.getOptimizedRate());
            assertEquals("0", result.getEvaluatingRate());
        }
    }

    @Test
    void invalidDateIsSkippedWithoutExceptionOrWarning() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Bất cập DV");
            row(sheet, 0, "Tên bất cập/lỗi", "Ngày phát hiện", "Tiến độ", "Nội dung");
            row(sheet, 1, "Lỗi", "31/9/2026", "Đang đánh giá", "Chi tiết");
            WordProductIssueReader.Result result = new WordProductIssueReader()
                    .read(workbook, "Bất cập DV", LocalDate.of(2026, 9, 1));
            assertTrue(result.getIssues().isEmpty());
            assertTrue(result.getWarnings().isEmpty());
        }
    }

    @Test
    void countsEveryDatedRowWithoutRequiringTitleOrContentAndUsesConfiguredSheet() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Bất cập DV");
            Sheet sheet = workbook.createSheet("Dữ liệu");
            row(sheet, 2, " Tiến độ\n", "Nội dung", "Ngày phát hiện");
            row(sheet, 3, "ĐÃ HOÀN THÀNH", "", "1/9/2026");
            row(sheet, 4, "Không khả thi", "", "7/9/2026");
            row(sheet, 5, "Đang đánh giá", "", "3/9/2026");
            row(sheet, 6, "Trạng thái khác", "", "3/9/2026");
            row(sheet, 7, "", "", "");
            WordProductIssueReader.Result result = new WordProductIssueReader()
                    .read(workbook, "Dữ liệu", LocalDate.of(2026, 9, 1));
            assertEquals(4, result.getIssues().size());
            assertEquals("25", result.getOptimizedRate());
            assertEquals("25", result.getEvaluatingRate());
            assertEquals(1, result.getInfeasible());
            assertEquals(1, result.getUnclassified());
            assertEquals(1, result.getWarnings().size());
        }
    }

    @Test
    void onlyAcceptsStrictDayMonthYearAndContinuesPastInvalidOrMissingDates() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Bất cập DV");
            row(sheet, 0, "Ngày phát hiện", "Nội dung", "Tiến độ");
            row(sheet, 1, "1/9/2026", "Đầu tuần", "Đã hoàn thành");
            String[] invalidDates = {"2026-09-01", "1.9.2026", "1-9-2026", "31/9/2026", "1/9/026",
                    "1/9/2026 00:00", "", "không phải ngày"};
            for (int index = 0; index < invalidDates.length; index++) {
                row(sheet, index + 2, invalidDates[index], "Bỏ qua", "Đã hoàn thành");
            }
            row(sheet, 10, "", "Thiếu ô ngày", "Đã hoàn thành");
            sheet.getRow(10).removeCell(sheet.getRow(10).getCell(0));
            row(sheet, 11, "", "Serial chưa định dạng", "Đã hoàn thành");
            sheet.getRow(11).getCell(0).setCellValue(46266);
            row(sheet, 12, "07/09/2026", "Cuối tuần", "Đang thực hiện");
            row(sheet, 13, "1/9/26", "Năm hai chữ số", "Đã hoàn thành");
            row(sheet, 14, "31/9/26", "Ngày không hợp lệ, năm hai chữ số", "Đã hoàn thành");
            WordProductIssueReader.Result result = new WordProductIssueReader()
                    .read(workbook, "Bất cập DV", LocalDate.of(2026, 9, 1));
            assertEquals(3, result.getIssues().size());
            assertEquals("Đầu tuần", result.getIssues().get(0).getContent());
            assertEquals("Cuối tuần", result.getIssues().get(1).getContent());
            assertEquals("Năm hai chữ số", result.getIssues().get(2).getContent());
            assertEquals("100", result.getOptimizedRate());
            assertTrue(result.getWarnings().isEmpty());
        }
    }

    @Test
    void extractsOnlyRequestedSectionsAndStopsAtNumberedLabels() {
        WordProductIssueReader.Issue issue = new WordProductIssueReader.Issue(9, "Tên riêng",
                "\n1. Thực trạng: Hiện trạng dòng 1\nChi tiết dòng 2\n"
                        + "2. Bất cập: Không đưa đoạn này vào thực trạng.\n"
                        + "3. Nguyên nhân: Do cấu hình.\n4. Đề xuất: Sửa cấu hình.\n", "", "");
        assertEquals("Hiện trạng dòng 1\nChi tiết dòng 2", issue.getActuality());
        assertEquals("Do cấu hình.", issue.getCause());
        assertEquals("Sửa cấu hình.", issue.getProposal());
        WordProductIssueReader.Issue inline = new WordProductIssueReader.Issue(10, "",
                "* THỰC TRẠNG : Hiện trạng. * Nguyên\u00a0nhân: Nguyên nhân. * Đề xuất: Đề xuất.", "", "");
        assertEquals("Hiện trạng.", inline.getActuality());
        assertEquals("Nguyên nhân.", inline.getCause());
        assertEquals("Đề xuất.", inline.getProposal());
        WordProductIssueReader.Issue missing = new WordProductIssueReader.Issue(11, "",
                "Thực trạng: Chỉ có thực trạng.\nĐề xuất: Giải pháp.", "", "");
        assertEquals("", missing.getCause());
        assertEquals("Chỉ có thực trạng.", missing.getActuality());
    }

    @Test
    void emptyWeekHasZeroRates() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Bất cập DV");
            row(sheet, 0, "Ngày phát hiện", "Nội dung", "Tiến độ");
            WordProductIssueReader.Result result = new WordProductIssueReader()
                    .read(workbook, "Bất cập DV", LocalDate.of(2026, 9, 1));
            assertEquals("0", result.getOptimizedRate());
            assertEquals("0", result.getEvaluatingRate());
        }
    }

    @Test
    void missingWeeklySheetIsRecoverableMissingData() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Bất cập");
            assertThrows(WordProductIssueReader.MissingIssueDataException.class,
                    () -> new WordProductIssueReader().read(workbook, "Bất cập DV", LocalDate.of(2026, 9, 1)));
        }
    }

    private void row(Sheet sheet, int index, String... values) {
        Row row = sheet.createRow(index);
        for (int column = 0; column < values.length; column++) row.createCell(column).setCellValue(values[column]);
    }
}
