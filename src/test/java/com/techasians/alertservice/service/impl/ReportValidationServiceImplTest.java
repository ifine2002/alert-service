package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.entity.RptExcelSheetDef;
import com.techasians.alertservice.exception.ReportFileValidationException;
import com.techasians.alertservice.repository.RptExcelSheetDefRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbookType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
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
            assertNull(validate(book, "xlsx", WEEK));
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
            assertNull(validate(book, "xlsx", LocalDate.of(2026, 12, 28)));
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
            assertNull(validate(book, " XLSX ", WEEK));
        }
    }

    @Test
    void checksRequiredSheetsForMacroEnabledWorkbook() throws Exception {
        configure("TH");
        try (XSSFWorkbook book = new XSSFWorkbook(XSSFWorkbookType.XLSM)) {
            book.createSheet("Other");
            assertThrows(ReportFileValidationException.class, () -> validate(book, "xlsm", WEEK));
            book.createSheet("TH");
            assertNull(validate(book, "xlsm", WEEK));
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

    @Test
    void annotatesAllMissingMalformedAndImpossibleDiscoveryDatesInRedWithoutChangingSource() throws Exception {
        configureProductIssues();
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            Sheet sheet = book.createSheet("Bất cập DV");
            sheet.createRow(0).createCell(0).setCellValue("Danh sách bất cập");
            sheet.createRow(2).createCell(2).setCellValue("Ngày phát hiện");
            String[] invalidDates = {null, "", "   ", "31/02/2026", "29/02/25", "7.9.2026",
                    "07-09-2026", "7/9/026", "2026-09-07"};
            for (int index = 0; index < invalidDates.length; index++) {
                Row row = sheet.createRow(index + 3);
                row.createCell(0).setCellValue("Bất cập " + index);
                if (invalidDates[index] != null) row.createCell(2).setCellValue(invalidDates[index]);
            }
            sheet.createRow(12).createCell(2).setCellValue("07/09/2026");

            byte[] errorFile = validate(book, "xlsx", 1010L, WEEK);

            assertNotNull(errorFile);
            try (XSSFWorkbook result = new XSSFWorkbook(new ByteArrayInputStream(errorFile))) {
                Sheet annotated = result.getSheet("Bất cập DV");
                Cell warningHeader = annotated.getRow(2).getCell(3);
                assertEquals("Kết quả", warningHeader.getStringCellValue());
                assertEquals(IndexedColors.RED.getIndex(), result.getFontAt(warningHeader.getCellStyle().getFontIndex()).getColor());
                for (int index = 0; index < invalidDates.length; index++) {
                    Cell warning = annotated.getRow(index + 3).getCell(3);
                    assertEquals(index < 3 ? "Ngày phát hiện không được để trống."
                            : "Ngày phát hiện không đúng định dạng d/M/uuuu hoặc d/M/uu, hoặc ngày không hợp lệ.",
                            warning.getStringCellValue());
                    assertEquals(IndexedColors.RED.getIndex(), result.getFontAt(warning.getCellStyle().getFontIndex()).getColor());
                }
                assertNull(annotated.getRow(12).getCell(3));
                assertEquals("07/09/2026", annotated.getRow(12).getCell(2).getStringCellValue());
            }
            assertNull(sheet.getRow(2).getCell(3));
            assertNull(sheet.getRow(3).getCell(2));
        }
    }

    @Test
    void acceptsReaderDateFormatsExcelAndCachedFormulaDatesAndSkipsCompletelyEmptyRows() throws Exception {
        configureProductIssues();
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            Sheet sheet = book.createSheet("Bất cập DV");
            sheet.createRow(0).createCell(1).setCellValue(" Ngày\nphát hiện ");
            sheet.createRow(1).createCell(1).setCellValue("29/02/2024");
            CellStyle dateStyle = book.createCellStyle();
            dateStyle.setDataFormat(book.createDataFormat().getFormat("dd/mm/yyyy"));
            Cell date = sheet.createRow(3).createCell(1);
            date.setCellValue(LocalDate.of(2026, 9, 7));
            date.setCellStyle(dateStyle);
            Cell formulaDate = sheet.createRow(4).createCell(1);
            formulaDate.setCellFormula("DATE(2026,9,8)");
            formulaDate.setCellStyle(dateStyle);
            Cell textFormula = sheet.createRow(5).createCell(1);
            textFormula.setCellFormula("\"09/09/2026\"");
            book.getCreationHelper().createFormulaEvaluator().evaluateAll();
            sheet.createRow(6).createCell(1).setCellStyle(dateStyle);
            sheet.createRow(7).createCell(0).setCellValue("   ");
            sheet.createRow(8);
            String[] readerDates = {"7/9/2026", "07/9/2026", "7/09/2026", "7/9/26", "07/09/26", "29/2/24"};
            for (int index = 0; index < readerDates.length; index++) {
                sheet.createRow(9 + index).createCell(1).setCellValue(readerDates[index]);
            }
            Cell shortDate = sheet.createRow(15).createCell(1);
            shortDate.setCellValue(LocalDate.of(2026, 9, 7));
            CellStyle shortDateStyle = book.createCellStyle();
            shortDateStyle.setDataFormat(book.createDataFormat().getFormat("d/m/yy"));
            shortDate.setCellStyle(shortDateStyle);
            book.createSheet("Sheet khác").createRow(0).createCell(0).setCellValue("Không cần validate ngày");

            assertNull(validate(book, "xlsx", 1010L, WEEK));
        }
    }

    @Test
    void acceptsCachedFormulaHeaderAndIgnoresRowsContainingOnlyEmptyFormulaResults() throws Exception {
        configureProductIssues();
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            Sheet sheet = book.createSheet("Bất cập DV");
            sheet.createRow(0).createCell(2).setCellFormula("\"Ngày phát hiện\"");
            sheet.createRow(1).createCell(2).setCellFormula("\"7/9/26\"");
            sheet.createRow(2).createCell(0).setCellFormula("\"\"");
            book.getCreationHelper().createFormulaEvaluator().evaluateAll();

            assertNull(validate(book, "xlsx", 1010L, WEEK));
        }
    }

    @Test
    void rejectsUnsupportedExcelDateFormatAndNonDateCellTypes() throws Exception {
        configureProductIssues();
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            Sheet sheet = book.createSheet("Bất cập DV");
            sheet.createRow(0).createCell(0).setCellValue("Ngày phát hiện");
            Cell date = sheet.createRow(1).createCell(0);
            date.setCellValue(LocalDate.of(2026, 9, 7));
            CellStyle shortDate = book.createCellStyle();
            shortDate.setDataFormat(book.createDataFormat().getFormat("yyyy-mm-dd"));
            date.setCellStyle(shortDate);
            sheet.createRow(2).createCell(0).setCellValue(12345);
            sheet.createRow(3).createCell(0).setCellValue(true);
            sheet.createRow(4).createCell(0).setCellErrorValue(FormulaError.VALUE.getCode());
            sheet.createRow(5).createCell(0).setCellFormula("1/0");
            book.getCreationHelper().createFormulaEvaluator().evaluateAll();

            byte[] errorFile = validate(book, "xlsx", 1010L, WEEK);
            assertNotNull(errorFile);
            try (XSSFWorkbook result = new XSSFWorkbook(new ByteArrayInputStream(errorFile))) {
                for (int row = 1; row <= 5; row++) {
                    assertTrue(result.getSheet("Bất cập DV").getRow(row).getCell(1).getStringCellValue()
                            .contains("ngày không hợp lệ"));
                }
            }
        }
    }

    @Test
    void rejectsProductIssuesWithoutDiscoveryDateHeader() throws Exception {
        configureProductIssues();
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            book.createSheet("Bất cập DV").createRow(0).createCell(0).setCellValue("Nội dung");
            assertEquals("Sheet Bất cập DV thiếu header Ngày phát hiện",
                    assertThrows(ReportFileValidationException.class,
                            () -> validate(book, "xlsx", 1010L, WEEK)).getMessage());
        }
    }

    @Test
    void rejectsProductIssuesWhenNoActiveSheetsAreConfigured() throws Exception {
        when(sheets.findByDefinitionIdAndStatusOrderBySortOrderAsc(1010L, 1)).thenReturn(Collections.emptyList());
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            book.createSheet("Other");
            assertEquals("Chưa cấu hình sheet bắt buộc cho definition 1010",
                    assertThrows(ReportFileValidationException.class,
                            () -> validate(book, "xlsx", 1010L, WEEK)).getMessage());
        }
    }

    @Test
    void validatesConfiguredProductIssueSheetNameResolvedFromWeekAndIgnoresExtraSheets() throws Exception {
        when(sheets.findByDefinitionIdAndStatusOrderBySortOrderAsc(1010L, 1))
                .thenReturn(Collections.singletonList(new RptExcelSheetDef().setSheetName("Bất cập {yyyy}")));
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            Sheet sheet = book.createSheet("Bất cập 2027");
            sheet.createRow(0).createCell(0).setCellValue("Ngày phát hiện");
            sheet.createRow(1).createCell(0).setCellValue("28/12/2026");
            book.createSheet("Bất cập DV").createRow(0).createCell(0).setCellValue("Không validate sheet ngoài cấu hình");

            assertNull(validate(book, "xlsx", 1010L, LocalDate.of(2026, 12, 28)));
            verify(sheets).findByDefinitionIdAndStatusOrderBySortOrderAsc(1010L, 1);
        }
    }

    @Test
    void aggregatesInvalidDatesAcrossConfiguredSheetsInConfigurationOrder() throws Exception {
        when(sheets.findByDefinitionIdAndStatusOrderBySortOrderAsc(1010L, 1)).thenReturn(Arrays.asList(
                new RptExcelSheetDef().setSheetName("Dữ liệu B"), new RptExcelSheetDef().setSheetName("Dữ liệu A")));
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            Sheet firstInWorkbook = book.createSheet("Dữ liệu A");
            firstInWorkbook.createRow(0).createCell(0).setCellValue("Ngày phát hiện");
            firstInWorkbook.createRow(1).createCell(0).setCellValue("31/02/2026");
            Sheet firstInConfiguration = book.createSheet("Dữ liệu B");
            firstInConfiguration.createRow(2).createCell(2).setCellValue("Ngày phát hiện");
            firstInConfiguration.createRow(3).createCell(0).setCellValue("Thiếu ngày");

            byte[] errorFile = validate(book, "xlsx", 1010L, WEEK);
            assertNotNull(errorFile);
            try (XSSFWorkbook result = new XSSFWorkbook(new ByteArrayInputStream(errorFile))) {
                assertEquals("Ngày phát hiện không được để trống.",
                        result.getSheet("Dữ liệu B").getRow(3).getCell(3).getStringCellValue());
                assertTrue(result.getSheet("Dữ liệu A").getRow(1).getCell(1).getStringCellValue()
                        .contains("không đúng định dạng"));
            }
        }
    }

    @Test
    void reusesWarningColumnAndClearsWarningsForCorrectedOrEmptyRows() throws Exception {
        configureProductIssues();
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            Sheet sheet = book.createSheet("Bất cập DV");
            sheet.createRow(0).createCell(0).setCellValue("Ngày phát hiện");
            sheet.createRow(1).createCell(0).setCellValue("31/02/2026");
            sheet.createRow(2).createCell(0).setCellValue("bad date");
            book.createSheet("Khác").createRow(0).createCell(0).setCellValue("Giữ nguyên");
            byte[] firstErrorFile = validate(book, "xlsx", 1010L, WEEK);
            assertNotNull(firstErrorFile);
            try (XSSFWorkbook corrected = new XSSFWorkbook(new ByteArrayInputStream(firstErrorFile))) {
                corrected.getSheet("Bất cập DV").getRow(1).getCell(0).setCellValue("07/09/2026");
                corrected.getSheet("Bất cập DV").createRow(3).createCell(1).setCellValue("warning cũ");
                byte[] nextErrorFile = validate(corrected, "xlsx", 1010L, WEEK);
                assertNotNull(nextErrorFile);
                try (XSSFWorkbook result = new XSSFWorkbook(new ByteArrayInputStream(nextErrorFile))) {
                    Sheet annotated = result.getSheet("Bất cập DV");
                    assertEquals(2, annotated.getRow(0).getLastCellNum());
                    assertEquals(CellType.BLANK, annotated.getRow(1).getCell(1).getCellType());
                    assertEquals(CellType.BLANK, annotated.getRow(3).getCell(1).getCellType());
                    assertTrue(annotated.getRow(2).getCell(1).getStringCellValue().contains("không đúng định dạng"));
                    assertEquals("Giữ nguyên", result.getSheet("Khác").getRow(0).getCell(0).getStringCellValue());
                    annotated.getRow(2).getCell(0).setCellValue("7/9/26");
                    assertNull(validate(result, "xlsx", 1010L, WEEK));
                }
            }
        }
    }

    @Test
    void appendsWarningAfterExistingColumnsEvenWhenHeaderRowHasFewerCells() throws Exception {
        configureProductIssues();
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            Sheet sheet = book.createSheet("Bất cập DV");
            sheet.createRow(0).createCell(0).setCellValue("Ngày phát hiện");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("bad date");
            row.createCell(5).setCellFormula("1+2");
            byte[] errorFile = validate(book, "xlsx", 1010L, WEEK);
            assertNotNull(errorFile);
            try (XSSFWorkbook result = new XSSFWorkbook(new ByteArrayInputStream(errorFile))) {
                Sheet annotated = result.getSheet("Bất cập DV");
                assertEquals("Kết quả", annotated.getRow(0).getCell(6).getStringCellValue());
                assertEquals("1+2", annotated.getRow(1).getCell(5).getCellFormula());
                assertTrue(annotated.getRow(1).getCell(6).getStringCellValue().contains("không đúng định dạng"));
            }
        }
    }

    @Test
    void copiesPreviousHeaderStylePerSheetWithoutChangingItsFont() throws Exception {
        when(sheets.findByDefinitionIdAndStatusOrderBySortOrderAsc(1010L, 1)).thenReturn(Arrays.asList(
                new RptExcelSheetDef().setSheetName("Bất cập DV"), new RptExcelSheetDef().setSheetName("Khác")));
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            String[] names = {"Bất cập DV", "Khác"};
            for (int index = 0; index < names.length; index++) {
                Sheet sheet = book.createSheet(names[index]);
                Row header = sheet.createRow(0);
                header.createCell(0).setCellValue("Ngày phát hiện");
                Cell previousHeader = header.createCell(1);
                previousHeader.setCellValue("Dịch vụ");
                Font font = book.createFont();
                font.setFontName(index == 0 ? "Times New Roman" : "Arial");
                font.setFontHeight((short) (index == 0 ? 270 : 220));
                font.setBold(index != 0);
                font.setItalic(true);
                font.setUnderline(Font.U_SINGLE);
                font.setColor(IndexedColors.BLUE.getIndex());
                CellStyle sourceStyle = book.createCellStyle();
                sourceStyle.setFont(font);
                sourceStyle.setAlignment(index == 0 ? HorizontalAlignment.CENTER : HorizontalAlignment.RIGHT);
                sourceStyle.setVerticalAlignment(VerticalAlignment.CENTER);
                sourceStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
                sourceStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                sourceStyle.setBorderBottom(BorderStyle.DOUBLE);
                sourceStyle.setBorderLeft(BorderStyle.THIN);
                sourceStyle.setWrapText(true);
                sourceStyle.setRotation((short) 15);
                previousHeader.setCellStyle(sourceStyle);
                sheet.createRow(1).createCell(0).setCellValue("bad date");
            }

            byte[] errorFile = validate(book, "xlsx", 1010L, WEEK);
            assertNotNull(errorFile);
            try (XSSFWorkbook result = new XSSFWorkbook(new ByteArrayInputStream(errorFile))) {
                for (int index = 0; index < names.length; index++) {
                    Row header = result.getSheet(names[index]).getRow(0);
                    CellStyle originalStyle = header.getCell(1).getCellStyle();
                    CellStyle warningStyle = header.getCell(2).getCellStyle();
                    Font warningFont = result.getFontAt(warningStyle.getFontIndex());
                    assertEquals(index == 0 ? "Times New Roman" : "Arial", warningFont.getFontName());
                    assertEquals(index == 0 ? 270 : 220, warningFont.getFontHeight());
                    assertEquals(index != 0, warningFont.getBold());
                    assertTrue(warningFont.getItalic());
                    assertEquals(Font.U_SINGLE, warningFont.getUnderline());
                    assertEquals(IndexedColors.RED.getIndex(), warningFont.getColor());
                    assertEquals(index == 0 ? HorizontalAlignment.CENTER : HorizontalAlignment.RIGHT, warningStyle.getAlignment());
                    assertEquals(VerticalAlignment.CENTER, warningStyle.getVerticalAlignment());
                    assertEquals(IndexedColors.LIGHT_BLUE.getIndex(), warningStyle.getFillForegroundColor());
                    assertEquals(FillPatternType.SOLID_FOREGROUND, warningStyle.getFillPattern());
                    assertEquals(BorderStyle.DOUBLE, warningStyle.getBorderBottom());
                    assertEquals(BorderStyle.THIN, warningStyle.getBorderLeft());
                    assertTrue(warningStyle.getWrapText());
                    assertEquals(15, warningStyle.getRotation());
                    assertNotEquals(originalStyle.getFontIndex(), warningStyle.getFontIndex());
                    assertEquals(IndexedColors.BLUE.getIndex(), result.getFontAt(originalStyle.getFontIndex()).getColor());
                }
            }
        }
    }

    @Test
    void warningRowsInheritDataFontsInsteadOfCalibriOrHeaderFontAndReuseStyles() throws Exception {
        configureProductIssues();
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            Sheet sheet = book.createSheet("Bất cập DV");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Ngày phát hiện");
            header.createCell(1).setCellValue("Nội dung");
            Cell lastHeader = header.createCell(2);
            lastHeader.setCellValue("Thời gian hoàn thành");
            Font headerFont = book.createFont();
            headerFont.setFontName("Arial");
            headerFont.setFontHeightInPoints((short) 18);
            headerFont.setBold(true);
            CellStyle headerStyle = book.createCellStyle();
            headerStyle.setFont(headerFont);
            lastHeader.setCellStyle(headerStyle);

            Font times = book.createFont();
            times.setFontName("Times New Roman");
            times.setFontHeight((short) 250);
            times.setColor(IndexedColors.BLACK.getIndex());
            CellStyle timesStyle = book.createCellStyle();
            timesStyle.setFont(times);
            timesStyle.setAlignment(HorizontalAlignment.LEFT);
            timesStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            timesStyle.setBorderBottom(BorderStyle.THIN);
            Font courier = book.createFont();
            courier.setFontName("Courier New");
            courier.setFontHeightInPoints((short) 10);
            courier.setItalic(true);
            courier.setColor(IndexedColors.BLUE.getIndex());
            CellStyle courierStyle = book.createCellStyle();
            courierStyle.setFont(courier);
            courierStyle.setAlignment(HorizontalAlignment.RIGHT);
            courierStyle.setVerticalAlignment(VerticalAlignment.TOP);
            CellStyle blankCalibriStyle = book.createCellStyle();
            blankCalibriStyle.setBorderBottom(BorderStyle.THIN);
            for (int index = 1; index <= 3; index++) {
                Row row = sheet.createRow(index);
                row.createCell(0).setCellValue(index == 2 ? "" : "bad date");
                Cell content = row.createCell(1);
                content.setCellValue("Nội dung " + index);
                content.setCellStyle(index == 2 ? courierStyle : timesStyle);
                row.createCell(2).setCellStyle(blankCalibriStyle);
            }

            byte[] errorFile = validate(book, "xlsx", 1010L, WEEK);
            assertNotNull(errorFile);
            try (XSSFWorkbook result = new XSSFWorkbook(new ByteArrayInputStream(errorFile))) {
                Sheet annotated = result.getSheet("Bất cập DV");
                for (int index = 1; index <= 3; index++) {
                    CellStyle warningStyle = annotated.getRow(index).getCell(3).getCellStyle();
                    Font warningFont = result.getFontAt(warningStyle.getFontIndex());
                    assertEquals(index == 2 ? "Courier New" : "Times New Roman", warningFont.getFontName());
                    assertEquals(index == 2 ? 200 : 250, warningFont.getFontHeight());
                    assertFalse(warningFont.getBold());
                    assertEquals(index == 2, warningFont.getItalic());
                    assertEquals(IndexedColors.RED.getIndex(), warningFont.getColor());
                    assertEquals(index == 2 ? HorizontalAlignment.RIGHT : HorizontalAlignment.LEFT, warningStyle.getAlignment());
                    assertEquals(index == 2 ? VerticalAlignment.TOP : VerticalAlignment.CENTER, warningStyle.getVerticalAlignment());
                    assertTrue(warningStyle.getWrapText());
                }
                assertEquals(annotated.getRow(1).getCell(3).getCellStyle().getIndex(),
                        annotated.getRow(3).getCell(3).getCellStyle().getIndex());
                assertEquals(BorderStyle.THIN, annotated.getRow(1).getCell(3).getCellStyle().getBorderBottom());
                assertEquals("Arial", result.getFontAt(annotated.getRow(0).getCell(3).getCellStyle().getFontIndex()).getFontName());
                assertEquals(IndexedColors.BLACK.getIndex(), result.getFontAt(annotated.getRow(1).getCell(1).getCellStyle().getFontIndex()).getColor());
                assertEquals(IndexedColors.BLUE.getIndex(), result.getFontAt(annotated.getRow(2).getCell(1).getCellStyle().getFontIndex()).getColor());
            }
        }
    }

    @Test
    void reportsMissingConfiguredProductIssueSheetInsteadOfUsingPreviousSheetName() throws Exception {
        when(sheets.findByDefinitionIdAndStatusOrderBySortOrderAsc(1010L, 1))
                .thenReturn(Collections.singletonList(new RptExcelSheetDef().setSheetName("Dữ liệu bất cập")));
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            book.createSheet("Bất cập DV");
            assertEquals("File Excel thiếu các sheet bắt buộc: Dữ liệu bất cập",
                    assertThrows(ReportFileValidationException.class,
                            () -> validate(book, "xlsx", 1010L, WEEK)).getMessage());
        }
    }

    private void configureProductIssues() {
        when(sheets.findByDefinitionIdAndStatusOrderBySortOrderAsc(1010L, 1))
                .thenReturn(Collections.singletonList(new RptExcelSheetDef().setSheetName("Bất cập DV")));
    }

    private void configure(String... names) {
        List<RptExcelSheetDef> required = new ArrayList<>();
        for (String name : names) required.add(new RptExcelSheetDef().setSheetName(name));
        when(sheets.findByDefinitionIdAndStatusOrderBySortOrderAsc(1102L, 1)).thenReturn(required);
    }

    private byte[] validate(XSSFWorkbook book, String extension, LocalDate week) throws Exception {
        return validate(book, extension, 1102L, week);
    }

    private byte[] validate(XSSFWorkbook book, String extension, Long definitionId, LocalDate week) throws Exception {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            book.write(bytes);
            MockMultipartFile upload = new MockMultipartFile("file", "input." + extension.trim(),
                    "application/octet-stream", bytes.toByteArray());
            return service.validate(upload, extension, definitionId, week);
        }
    }
}
