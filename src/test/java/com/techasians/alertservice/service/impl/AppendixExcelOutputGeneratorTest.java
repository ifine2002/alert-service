package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.entity.*;
import com.techasians.alertservice.repository.*;
import com.techasians.alertservice.service.ReportFileService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.OutputStream;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppendixExcelOutputGeneratorTest {
    @TempDir Path temporaryDirectory;

    @Test
    void filtersAppendixTwoUsingDisplayedDatesAndCachedFormulaResults() throws Exception {
        for (boolean date1904 : new boolean[] {false, true}) {
            try (XSSFWorkbook sourceWorkbook = new XSSFWorkbook();
                 XSSFWorkbook targetWorkbook = new XSSFWorkbook()) {
                sourceWorkbook.getCTWorkbook().getWorkbookPr().setDate1904(date1904);
                Sheet source = sourceWorkbook.createSheet("Source");
                Sheet target = targetWorkbook.createSheet("Target");
                row(source, 0, 0, "STT", "Ngày phát hiện", "Dịch vụ", "Nội dung");
                row(target, 0, 0, "STT", "Ngày phát hiện", "Dịch vụ", "Nội dung");
                row(source, 1, 0, "1", " 6/5/2026 ", "SME", "Long year");
                row(source, 2, 0, "2", "12/5/26", "SME", "Short year at week end");
                row(source, 3, 0, "3", "", "SME", "Displayed numeric date");
                row(source, 4, 0, "4", "", "SME", "Cached numeric date");
                row(source, 5, 0, "5", "", "SME", "Cached text date");
                row(source, 6, 0, "6", "13/5/26", "SME", "Outside week");
                row(source, 7, 0, "7", "6/5/26", "Other", "Other service");
                row(source, 8, 0, "8", "6/5/26", "", "Missing service");
                CellStyle dateStyle = sourceWorkbook.createCellStyle();
                dateStyle.setDataFormat(sourceWorkbook.createDataFormat().getFormat("m/d/yy"));
                double serial = DateUtil.getExcelDate(LocalDate.of(2026, 6, 5), date1904);
                Cell numeric = source.getRow(3).getCell(1);
                numeric.setCellValue(serial);
                numeric.setCellStyle(dateStyle);
                Cell formula = source.getRow(4).getCell(1);
                source.getRow(4).createCell(4).setCellValue(serial);
                formula.setCellFormula("E5");
                formula.setCellStyle(dateStyle);
                source.getRow(5).getCell(1).setCellFormula("\"6/5/26\"");
                sourceWorkbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
                source.getRow(4).getCell(4).setCellValue(0); // Keep the saved formula result.

                AppendixExcelOutputGenerator generator = new AppendixExcelOutputGenerator(null, null, null, null, null, null);
                ReflectionTestUtils.invokeMethod(generator, "copyFilteredAppendixTwo",
                        source, target, targetWorkbook, LocalDate.of(2026, 5, 6));

                assertEquals(7, target.getLastRowNum());
                for (String included : Arrays.asList("Long year", "Short year at week end",
                        "Displayed numeric date", "Cached numeric date", "Cached text date",
                        "Other service", "Missing service")) {
                    assertNotNull(findCell(target, included), included);
                }
                assertNull(findCell(target, "Outside week"));
            }
        }
    }

    @Test
    void skipsInvalidMissingAndUnformattedDatesWithoutStoppingAppendixFiltering() throws Exception {
        try (XSSFWorkbook sourceWorkbook = new XSSFWorkbook();
             XSSFWorkbook targetWorkbook = new XSSFWorkbook()) {
            Sheet source = sourceWorkbook.createSheet("Source");
            Sheet target = targetWorkbook.createSheet("Target");
            row(source, 0, 0, "STT", "Ngày phát hiện", "Dịch vụ", "Nội dung");
            row(target, 0, 0, "STT", "Ngày phát hiện", "Dịch vụ", "Nội dung");
            String[] dates = {"31/9/2026", "31/9/26", "2026-09-30", "30.9.2026", "30/9/026", ""};
            for (int index = 0; index < dates.length; index++) {
                row(source, index + 1, 0, "", dates[index], "SME", "Invalid");
            }
            row(source, 7, 0, "", "", "SME", "Missing date");
            source.getRow(7).removeCell(source.getRow(7).getCell(1));
            row(source, 8, 0, "", "", "SME", "Unformatted serial");
            source.getRow(8).getCell(1).setCellValue(DateUtil.getExcelDate(LocalDate.of(2026, 9, 30)));
            row(source, 9, 0, "", "", "SME", "Formula error");
            source.getRow(9).getCell(1).setCellFormula("1/0");
            sourceWorkbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            row(source, 10, 0, "", "30/9/26", "SME", "Valid date");

            AppendixExcelOutputGenerator generator = new AppendixExcelOutputGenerator(null, null, null, null, null, null);
            ReflectionTestUtils.invokeMethod(generator, "copyFilteredAppendixTwo",
                    source, target, targetWorkbook, LocalDate.of(2026, 9, 28));

            assertEquals(1, target.getLastRowNum());
            assertNotNull(findCell(target, "Valid date"));
        }
    }

    @Test
    void generatesCrossYearAppendixFromTablesMovedDownAndRight() throws Exception {
        LocalDate week = LocalDate.of(2026, 12, 28);
        Path input = temporaryDirectory.resolve("input.xlsx");
        try (Workbook workbook = new XSSFWorkbook(); OutputStream stream = Files.newOutputStream(input)) {
            Sheet incidents = workbook.createSheet("Lũy kế lỗi phát sinh năm 2027");
            row(incidents, 75, 6, "Chủ dịch vụ", "Tháng", "Ngày", "Tên lỗi");
            row(incidents, 76, 6, "VTT", "1", "1", "Sự cố được chọn");
            row(incidents, 77, 6, "VNP", "1", "2", "Sự cố bị loại");
            Sheet stations = workbook.createSheet("Trạm tuần");
            row(stations, 80, 8, "Tháng", "Ngày", "Mã trạm", "Tỉnh");
            row(stations, 81, 8, "12", "31", "TRAM-001", "Hà Nội");
            workbook.write(stream);
        }

        RptDefinitionRepository definitions = mock(RptDefinitionRepository.class);
        RptOutputBlockDefRepository blocks = mock(RptOutputBlockDefRepository.class);
        RptOutputSourceMapRepository maps = mock(RptOutputSourceMapRepository.class);
        RptInstanceRepository instances = mock(RptInstanceRepository.class);
        RptFileRepository files = mock(RptFileRepository.class);
        ReportFileService downloads = mock(ReportFileService.class);
        when(definitions.findById(2002L)).thenReturn(Optional.of(new RptDefinition()
                .setTemplateRelativePath("templates/phu-luc-1-template.xlsx")));
        when(blocks.findByOutputDefinitionIdAndStatusOrderBySortOrder(2002L, 1)).thenReturn(Arrays.asList(
                new RptOutputBlockDef().setId(1L).setBlockCode("INCIDENT_YEAR").setTargetName("LPS năm {yyyy}"),
                new RptOutputBlockDef().setId(2L).setBlockCode("STATION_WEEK").setTargetName("Lỗi trạm tuần {dd-dd.MM}")));
        when(maps.findByOutputBlockIdAndStatusOrderBySortOrder(1L, 1)).thenReturn(Collections.singletonList(
                new RptOutputSourceMap().setSourceDefinitionId(1102L).setSourceSheetName("Lũy kế lỗi phát sinh năm {yyyy}")));
        when(maps.findByOutputBlockIdAndStatusOrderBySortOrder(2L, 1)).thenReturn(Collections.singletonList(
                new RptOutputSourceMap().setSourceDefinitionId(1102L).setSourceSheetName("Trạm tuần")));
        when(instances.findByDefinitionIdAndWeekStartDate(1102L, week)).thenReturn(Optional.of(
                new RptInstance().setCurrentFileId(10L)));
        when(files.findById(10L)).thenReturn(Optional.of(new RptFile().setStorageObjectName("input")));
        when(downloads.downloadToTemporaryFile("input")).thenAnswer(invocation -> {
            Path copy = Files.createTempFile(temporaryDirectory, "download-", ".xlsx");
            Files.copy(input, copy, StandardCopyOption.REPLACE_EXISTING);
            return new ReportFileService.TemporaryReportFile(copy);
        });

        Path output = new AppendixExcelOutputGenerator(definitions, blocks, maps, instances, files, downloads)
                .generate(2002L, week);
        try (Workbook workbook = new XSSFWorkbook(Files.newInputStream(output))) {
            Sheet incidents = workbook.getSheet("LPS năm 2027");
            assertNotNull(incidents);
            Row incident = findCell(incidents, "Sự cố được chọn").getRow();
            assertEquals(1.0, incident.getCell(0).getNumericCellValue());
            assertEquals("1", incident.getCell(1).getStringCellValue());
            assertNull(findCell(incidents, "Sự cố bị loại"));
            assertFalse(text(incidents).contains("{{YEAR}}"));
            assertTrue(text(incidents).contains("2027"));
            Sheet stations = workbook.getSheet("Lỗi trạm tuần 28-03.01");
            assertNotNull(stations);
            Row station = findCell(stations, "TRAM-001").getRow();
            assertEquals(1.0, station.getCell(0).getNumericCellValue());
            assertEquals("12", station.getCell(1).getStringCellValue());
            assertNotNull(findCell(stations, "Hà Nội"));
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private static void row(Sheet sheet, int index, int firstColumn, String... values) {
        Row row = sheet.createRow(index);
        for (int column = 0; column < values.length; column++) row.createCell(firstColumn + column).setCellValue(values[column]);
    }

    private static Cell findCell(Sheet sheet, String value) {
        DataFormatter formatter = new DataFormatter();
        for (Row row : sheet) for (Cell cell : row) if (value.equals(formatter.formatCellValue(cell))) return cell;
        return null;
    }

    private static String text(Sheet sheet) {
        StringBuilder text = new StringBuilder();
        DataFormatter formatter = new DataFormatter();
        for (Row row : sheet) for (Cell cell : row) text.append(formatter.formatCellValue(cell)).append('\n');
        return text.toString();
    }
}
