package com.techasians.alertservice.service.impl;

import com.aspose.words.*;
import com.aspose.words.Shape;
import com.techasians.alertservice.entity.*;
import com.techasians.alertservice.enums.*;
import com.techasians.alertservice.service.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

class WordStationOverviewGenerationTest {
    private final LocalDate week = LocalDate.of(2026, 9, 1);

    @ParameterizedTest
    @ValueSource(longs = {1102L, 9102L})
    void configuredYearSheetsDriveChartsAndCableTopWhenReportingWeekCrossesNewYear(long definitionId) throws Exception {
        LocalDate report = LocalDate.of(2026, 12, 30);
        Path input = Files.createTempFile("configured-years-", ".xlsx");
        try (Workbook book = new XSSFWorkbook()) {
            Sheet current = WordStationIncidentChartReaderTest.cableSheet(book, "Current 2027", 0);
            Sheet previous = WordStationIncidentChartReaderTest.cableSheet(book, "Archive 2026", 1);
            WordStationIncidentChartReaderTest.cableData(current, 2, 0, LocalDate.of(2027, 1, 1), "Đứt cáp", "HCM", 4, 40);
            WordStationIncidentChartReaderTest.cableData(previous, 2, 1, LocalDate.of(2026, 12, 31), "Cháy cáp", "HCM", 3, 30);
            WordStationIncidentChartReaderTest.cableData(current, 3, 0, LocalDate.of(2026, 12, 31), "Đứt cáp", "HNI", 999, 999);
            Sheet summary = book.createSheet("Summary");
            WordStationIncidentReaderTest.cableTable(summary, 0, 0);
            summary.getRow(0).getCell(1).setCellValue("23/12/2026 - 29/12/2026");
            summary.getRow(0).getCell(2).setCellValue("30/12/2026 - 05/01/2027");
            WordStationIncidentReaderTest.evaluate(book);
            try (OutputStream out = Files.newOutputStream(input)) { book.write(out); }
        }
        RptFile file = new RptFile().setId(1102L).setStorageObjectName("station-source");
        java.util.List<WordGenerationSnapshot.Block> blocks = new ArrayList<>();
        for (String code : Arrays.asList("STATION_INCIDENT_CHART", "CABLE_INCIDENT_CHART")) blocks.add(configuredBlock(code,
                Arrays.asList(mappedSource(2, "Archive {yyyy-1}", RptSourceRole.CHART_ACCUMULATED, file, report),
                        mappedSource(1, "Current {yyyy}", RptSourceRole.CHART_ACCUMULATED, file, report))));
        blocks.add(configuredBlock("STATION_INCIDENT", Arrays.asList(
                mappedSource(3, "Archive {yyyy-1}", RptSourceRole.DATA, file, report),
                mappedSource(2, "Current {yyyy}", RptSourceRole.DATA, file, report),
                mappedSource(1, "Summary", RptSourceRole.DATA, file, report))));
        for (WordGenerationSnapshot.Block block : blocks) {
            for (WordGenerationSnapshot.Source source : block.getSources()) {
                source.getMapping().setSourceDefinitionId(definitionId);
            }
        }
        ReportFileService files = mock(ReportFileService.class);
        when(files.downloadToTemporaryFile("station-source")).thenReturn(new ReportFileService.TemporaryReportFile(input));
        WordTrendChartRenderer renderer = spy(new WordTrendChartRenderer());
        Path output = generator(files, renderer).generate(new WordGenerationSnapshot(report, "templates/weekly-vcx-word-template.docx", blocks));
        try {
            ArgumentCaptor<Double[][]> station = ArgumentCaptor.forClass(Double[][].class), cable = ArgumentCaptor.forClass(Double[][].class);
            verify(renderer).renderStation(eq(report), station.capture());
            verify(renderer).renderCable(eq(report), cable.capture());
            assertArrayEquals(new Double[] {2.0, 7.0, 70.0}, station.getValue()[7]);
            assertArrayEquals(new Double[] {2.0, 7.0, 70.0}, cable.getValue()[7]);
            Document document = new Document(output.toString());
            assertEquals("HCM – 2SC", document.getRange().getBookmarks().get("BM_CABLE_TOP_PROVINCES").getText());
            assertFalse(document.getText().contains("dữ liệu năm 2026 chưa đầy đủ"));
            verify(files, times(1)).downloadToTemporaryFile("station-source");
        } finally { Files.deleteIfExists(output); Files.deleteIfExists(input); }
    }

    @Test
    void currentYearOnlyDoesNotDownloadOrResolveUnusedPreviousSource() throws Exception {
        Path input = exampleExcel();
        ReportFileService files = mock(ReportFileService.class);
        when(files.downloadToTemporaryFile("station-source")).thenReturn(new ReportFileService.TemporaryReportFile(input));
        RptFile current = new RptFile().setId(1102L).setStorageObjectName("station-source");
        RptFile previous = new RptFile().setId(999L).setStorageObjectName("unused-previous");
        WordGenerationSnapshot.Block block = configuredBlock("STATION_INCIDENT_CHART", Arrays.asList(
                mappedSource(2, "Archive {yyyy-1}", RptSourceRole.CHART_ACCUMULATED, previous, week),
                mappedSource(1, "Lũy kế trạm năm {yyyy}", RptSourceRole.CHART_ACCUMULATED, current, week)));
        Path output = generator(files).generate(new WordGenerationSnapshot(week, "templates/weekly-vcx-word-template.docx", Collections.singletonList(block)));
        try {
            chart(new Document(output.toString()));
            verify(files, times(1)).downloadToTemporaryFile("station-source");
            verify(files, never()).downloadToTemporaryFile("unused-previous");
        } finally { Files.deleteIfExists(output); Files.deleteIfExists(input); }
    }

    @Test
    void missingConfiguredArchiveWarnsWithActualNameAndKeepsCurrentChart() throws Exception {
        LocalDate report = LocalDate.of(2026, 1, 5);
        Path input = Files.createTempFile("missing-archive-", ".xlsx");
        try (Workbook book = new XSSFWorkbook()) {
            Sheet current = WordStationIncidentChartReaderTest.sheet(book, "Current 2026", 0);
            WordStationIncidentChartReaderTest.data(current, 2, 0, report, 7, 70);
            try (OutputStream out = Files.newOutputStream(input)) { book.write(out); }
        }
        ReportFileService files = mock(ReportFileService.class);
        when(files.downloadToTemporaryFile("station-source")).thenReturn(new ReportFileService.TemporaryReportFile(input));
        RptFile file = new RptFile().setId(1102L).setStorageObjectName("station-source");
        WordGenerationSnapshot.Block block = configuredBlock("STATION_INCIDENT_CHART", Arrays.asList(
                mappedSource(1, "Current {yyyy}", RptSourceRole.CHART_ACCUMULATED, file, report),
                mappedSource(2, "Missing archive {yyyy-1}", RptSourceRole.CHART_ACCUMULATED, file, report)));
        Path output = generator(files).generate(new WordGenerationSnapshot(report, "templates/weekly-vcx-word-template.docx", Collections.singletonList(block)));
        try {
            Document document = new Document(output.toString());
            chart(document);
            assertTrue(document.getText().contains("Thiếu sheet Missing archive 2025; dữ liệu năm 2025 chưa đầy đủ"));
            verify(files, times(1)).downloadToTemporaryFile("station-source");
        } finally { Files.deleteIfExists(output); Files.deleteIfExists(input); }
    }

    private WordGenerationSnapshot.Source mappedSource(int order, String sheet, RptSourceRole role, RptFile file, LocalDate week) {
        return new WordGenerationSnapshot.Source(new RptOutputSourceMap().setSourceDefinitionId(1102L)
                .setSourceRole(role).setSourceSheetName(sheet).setSortOrder(order), null, file, week);
    }

    private WordGenerationSnapshot.Block configuredBlock(String code, java.util.List<WordGenerationSnapshot.Source> sources) {
        return new WordGenerationSnapshot.Block(new RptOutputBlockDef().setBlockCode(code).setTargetName("BM_" + code)
                .setRenderType(RptRenderType.CUSTOM_HANDLER), sources);
    }

    @Test
    void sharedReadersAndRendererKeepSystemAndStationDataSeparateInOneReport() throws Exception {
        Path excel = Files.createTempFile("combined-incident-source-", ".xlsx");
        try (Workbook book = new XSSFWorkbook()) {
            Sheet th = book.createSheet("TH");
            String[] headers = {"Tổng lỗi phát sinh", "Tuần trước", "Tuần này", "So sánh", "Tỷ lệ tăng giảm"};
            Row header = th.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            Row sc = th.createRow(1), pa = th.createRow(2);
            sc.createCell(0).setCellValue("Số lượng");
            sc.createCell(1).setCellValue(3); sc.createCell(2).setCellValue(3);
            pa.createCell(0).setCellValue("PA"); pa.createCell(1).setCellValue(136);
            pa.createCell(2).setCellValue(37); pa.createCell(3).setCellValue(-99); pa.createCell(4).setCellValue(-73);
            WordStationIncidentReaderTest.table(th, 0, 6);
            WordStationIncidentReaderTest.evaluate(book);
            Sheet system = book.createSheet("Lũy kế lỗi phát sinh năm 2026");
            Row systemHeader = system.createRow(0), systemRow = system.createRow(1);
            String[] columns = {"Năm", "Tháng", "Ngày", "Chủ dịch vụ", "Số lượng phản ảnh"};
            for (int i = 0; i < columns.length; i++) systemHeader.createCell(i).setCellValue(columns[i]);
            systemRow.createCell(0).setCellValue(2026); systemRow.createCell(1).setCellValue(9);
            systemRow.createCell(2).setCellValue(1); systemRow.createCell(3).setCellValue("VTT");
            systemRow.createCell(4).setCellValue(37);
            Sheet station = WordStationIncidentChartReaderTest.sheet(book, "Lũy kế trạm năm 2026", 0);
            WordStationIncidentChartReaderTest.data(station, 2, 0, week, 5301, 143987);
            try (OutputStream out = Files.newOutputStream(excel)) { book.write(out); }
        }
        ReportFileService files = mock(ReportFileService.class);
        when(files.downloadToTemporaryFile("station-source")).thenReturn(new ReportFileService.TemporaryReportFile(excel));
        RptFile file = new RptFile().setId(1102L).setStorageObjectName("station-source");
        java.util.List<WordGenerationSnapshot.Block> blocks = new ArrayList<>();
        for (String code : Arrays.asList("SYSTEM_INCIDENT", "SYSTEM_INCIDENT_CHART")) {
            RptOutputSourceMap mapping = new RptOutputSourceMap().setSourceDefinitionId(1102L)
                    .setSourceRole(code.equals("SYSTEM_INCIDENT") ? RptSourceRole.DATA : RptSourceRole.CHART_ACCUMULATED)
                    .setSourceSheetName(code.equals("SYSTEM_INCIDENT") ? "TH" : "Lũy kế lỗi phát sinh năm {yyyy}").setSortOrder(1);
            blocks.add(new WordGenerationSnapshot.Block(new RptOutputBlockDef().setBlockCode(code)
                    .setTargetName("BM_" + code).setRenderType(RptRenderType.CUSTOM_HANDLER), Collections.singletonList(
                    new WordGenerationSnapshot.Source(mapping, null, file, week))));
        }
        blocks.addAll(snapshot(file).getBlocks());
        Path output = generator(files).generate(new WordGenerationSnapshot(week, "templates/weekly-vcx-word-template.docx", blocks));
        try {
            Document document = new Document(output.toString());
            assertOverview(document);
            assertTrue(document.getRange().getBookmarks().get("BM_SYSTEM_INCIDENT").getText().contains("3SC ~ 37PA"));
            assertTrue(document.getRange().getBookmarks().get("BM_SYSTEM_INCIDENT").getText().contains("PAKH giảm 73% (99PA)"));
            Paragraph systemChart = (Paragraph) document.getRange().getBookmarks().get("BM_SYSTEM_INCIDENT_CHART")
                    .getBookmarkStart().getParentNode();
            Shape systemImage = (Shape) systemChart.getChildNodes(NodeType.SHAPE, true).get(0);
            assertEquals(1500, ImageIO.read(new ByteArrayInputStream(systemImage.getImageData().getImageBytes())).getWidth());
            assertEquals(1700, ImageIO.read(new ByteArrayInputStream(chart(document).getImageData().getImageBytes())).getWidth());
            assertFalse(document.getText().contains("SYSTEM_INCIDENT_CHART:"));
            assertFalse(document.getText().contains("STATION_INCIDENT_CHART:"));
            verify(files, times(1)).downloadToTemporaryFile("station-source");
        } finally { Files.deleteIfExists(output); Files.deleteIfExists(excel); }
    }

    @ParameterizedTest
    @ValueSource(longs = {1102L, 9102L})
    void fillsStaticSentencesAndInsertsChartWithOneSharedWorkbookDownload(long definitionId) throws Exception {
        Path excel = exampleExcel();
        ReportFileService files = mock(ReportFileService.class);
        when(files.downloadToTemporaryFile("station-source")).thenReturn(new ReportFileService.TemporaryReportFile(excel));
        WordGenerationSnapshot configured = snapshot(new RptFile().setId(1102L).setStorageObjectName("station-source"));
        for (WordGenerationSnapshot.Block block : configured.getBlocks()) {
            for (WordGenerationSnapshot.Source source : block.getSources()) {
                source.getMapping().setSourceDefinitionId(definitionId);
            }
        }
        Path output = generator(files).generate(configured);
        try {
            Document document = new Document(output.toString());
            assertOverview(document);
            chart(document);
            assertTrue(document.getRange().getBookmarks().get("BM_STATION_INCIDENT_CHART").getText().trim().isEmpty());
            assertFalse(document.getText().contains("STATION_OVERVIEW:"));
            assertFalse(document.getText().contains("STATION_INCIDENT_CHART:"));
            verify(files, times(1)).downloadToTemporaryFile("station-source");
            assertFalse(Files.exists(excel));
        } finally { Files.deleteIfExists(output); Files.deleteIfExists(excel); }
    }

    @Test
    void missingSourceRemovesAllNestedTokensFromBothSentences() throws Exception {
        ReportFileService files = mock(ReportFileService.class);
        Path output = generator(files).generate(snapshot(null));
        try {
            Document document = new Document(output.toString());
            assertEquals("Chưa có dữ liệu", document.getRange().getBookmarks().get("BM_STATION_OVERVIEW").getText());
            assertFalse(document.getText().contains("{station"));
            assertTrue(document.getText().contains("STATION_OVERVIEW: Thiếu file nguồn 1102"));
            assertTrue(document.getText().contains("STATION_INCIDENT_CHART: Thiếu file nguồn 1102"));
            verifyNoInteractions(files);
        } finally { Files.deleteIfExists(output); }
    }

    @Test
    void malformedSummaryStillAllowsChartAndProducesWarning() throws Exception {
        Path excel = exampleExcel();
        try (InputStream input = Files.newInputStream(excel); Workbook book = WorkbookFactory.create(input)) {
            book.getSheet("TH").getRow(3).getCell(0).setBlank();
            try (OutputStream out = Files.newOutputStream(excel)) { book.write(out); }
        }
        ReportFileService files = mock(ReportFileService.class);
        when(files.downloadToTemporaryFile("station-source")).thenReturn(new ReportFileService.TemporaryReportFile(excel));
        Path output = generator(files).generate(snapshot(new RptFile().setId(1102L).setStorageObjectName("station-source")));
        try {
            Document document = new Document(output.toString());
            assertEquals("Chưa có dữ liệu", document.getRange().getBookmarks().get("BM_STATION_OVERVIEW").getText());
            assertFalse(document.getText().contains("{station"));
            chart(document);
            assertTrue(document.getText().contains("STATION_OVERVIEW:"));
        } finally { Files.deleteIfExists(output); Files.deleteIfExists(excel); }
    }

    @Test
    void keepsOnlyCableSectionFromWordAlongsideNewExcelOverview() throws Exception {
        Path excel = exampleExcel(), word = Files.createTempFile("station-cable-", ".docx");
        Document source = new Document();
        DocumentBuilder builder = new DocumentBuilder(source);
        builder.writeln("SỰ CỐ TRẠM CĐBR");
        builder.writeln("Tình hình chung cũ không được copy");
        builder.writeln("Sự cố đứt cáp, cháy cáp trục, cáp nhánh");
        builder.writeln("Chi tiết sự cố cáp cần giữ");
        source.save(word.toString());
        ReportFileService files = mock(ReportFileService.class);
        when(files.downloadToTemporaryFile("station-source")).thenReturn(new ReportFileService.TemporaryReportFile(excel));
        when(files.downloadToTemporaryFile("cable-source")).thenReturn(new ReportFileService.TemporaryReportFile(word));
        WordGenerationSnapshot snapshot = snapshot(new RptFile().setId(1102L).setStorageObjectName("station-source"));
        java.util.List<WordGenerationSnapshot.Block> blocks = new ArrayList<>(snapshot.getBlocks());
        RptSectionDef section = new RptSectionDef().setExtractMode(RptExtractMode.TEXT_RANGE)
                .setStartMarker("Sự cố đứt cáp, cháy cáp trục, cáp nhánh").setIncludeStart(1).setIncludeEnd(0);
        blocks.add(new WordGenerationSnapshot.Block(new RptOutputBlockDef().setBlockCode("STATION_INCIDENT")
                .setTargetName("BM_STATION_INCIDENT").setRenderType(RptRenderType.COPY_SECTION), Collections.singletonList(
                new WordGenerationSnapshot.Source(new RptOutputSourceMap().setSourceDefinitionId(1101L), section,
                        new RptFile().setId(1101L).setStorageObjectName("cable-source"), week))));
        Path output = generator(files).generate(new WordGenerationSnapshot(week, snapshot.getTemplatePath(), blocks));
        try {
            Document document = new Document(output.toString());
            assertOverview(document);
            assertTrue(document.getText().contains("Chi tiết sự cố cáp cần giữ"));
            assertFalse(document.getText().contains("Tình hình chung cũ"));
        } finally { Files.deleteIfExists(output); Files.deleteIfExists(excel); Files.deleteIfExists(word); }
    }

    @Test
    void readsActualWorkbookAndKeepsReviewablePreview() throws Exception {
        Path fixture = Paths.get("documents", "File 1", "1. Báo cáo Lỗi phát sinh tuần_ thuynt14",
                "T36. Bao cao tuan 01.09 -07.09.xlsx");
        assumeTrue(Files.isRegularFile(fixture), "Local workbook is optional outside this workspace");
        Path download = Files.createTempFile("station-fixture-", ".xlsx");
        Files.copy(fixture, download, StandardCopyOption.REPLACE_EXISTING);
        ReportFileService files = mock(ReportFileService.class);
        when(files.downloadToTemporaryFile("station-source")).thenReturn(new ReportFileService.TemporaryReportFile(download));
        Path output = generator(files).generate(snapshot(new RptFile().setId(1102L).setStorageObjectName("station-source")));
        try {
            Document document = new Document(output.toString());
            assertOverview(document);
            Shape chart = chart(document);
            // File mẫu có ô Tổng ảnh hưởng trống: vẫn vẽ và giữ cảnh báo dữ liệu trong báo cáo.
            Path directory = Paths.get("target", "station-overview");
            Files.createDirectories(directory);
            Files.copy(output, directory.resolve("preview-01-09-2026.docx"), StandardCopyOption.REPLACE_EXISTING);
            Files.write(directory.resolve("chart-01-09-2026.png"), chart.getImageData().getImageBytes());
        } finally { Files.deleteIfExists(output); Files.deleteIfExists(download); }
    }

    @Test
    void rendersZeroSeriesAndRejectsInvalidChartData() throws Exception {
        WordTrendChartRenderer renderer = new WordTrendChartRenderer();
        Double[][] zeros = new Double[8][3];
        for (Double[] row : zeros) Arrays.fill(row, 0.0);
        assertNotNull(ImageIO.read(new ByteArrayInputStream(renderer.renderStation(week, zeros))));
        assertThrows(IllegalArgumentException.class, () -> renderer.renderStation(week, new Double[7][3]));
        zeros[0][2] = -1.0;
        assertThrows(IllegalArgumentException.class, () -> renderer.renderStation(week, zeros));
    }

    @Test
    void stationChartPlacesAffectedZeroTwoGridLinesAboveBaselineAndWhitePaLabelsInsideRedBars() throws Exception {
        Double[][] values = new Double[8][3];
        for (int i = 0; i < 8; i++) values[i] = new Double[] {471.0, 6655.0, 0.0};
        java.awt.image.BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                new WordTrendChartRenderer().renderStation(week, values)));
        assertEquals(1700, image.getWidth());
        assertEquals(600, image.getHeight());
        // Vùng vẽ cao 330px, mỗi khoảng lưới 66px: mốc 0 của đường nằm trên đáy hai khoảng.
        for (int i = 0; i < 8; i++) assertEquals(new java.awt.Color(64, 64, 64).getRGB(), image.getRGB(184 + i * 178, 303));
        assertEquals(java.awt.Color.RED.getRGB(), image.getRGB(218, 400));
        assertEquals(java.awt.Color.BLACK.getRGB(), image.getRGB(150, 432));
        int whiteLabelPixels = 0;
        for (int x = 190; x < 247; x++) for (int y = 309; y < 333; y++) {
            if (image.getRGB(x, y) == java.awt.Color.WHITE.getRGB()) whiteLabelPixels++;
        }
        assertTrue(whiteLabelPixels > 20, "PA label must be white and inside the red column");
    }

    private void assertOverview(Document document) throws Exception {
        String text = document.getRange().getBookmarks().get("BM_STATION_OVERVIEW").getText();
        assertTrue(text.contains("Trong tuần xảy ra 318 sự cố/kế hoạch với 5.301PA/143.987TB ảnh hưởng."), text);
        assertTrue(text.contains("So với tuần trước: Số lượng sự cố giảm 12% (-45SC), PA KH giảm 27% (-1.919PA), "
                + "TB ảnh hưởng giảm 57% (-187.154TB)."), text);
        assertFalse(document.getText().contains("{station"));
        for (String name : Arrays.asList("BM_STATION_COUNT", "BM_STATION_PA", "BM_STATION_AFFECTED",
                "BM_STATION_SC_CHANGE", "BM_STATION_PA_CHANGE", "BM_STATION_AFFECTED_CHANGE")) {
            Bookmark bookmark = document.getRange().getBookmarks().get(name);
            assertNotNull(bookmark);
            assertTrue(((Run) bookmark.getBookmarkStart().getNextSibling()).getFont().getBold(), name);
        }
    }

    @Test
    void fillsCableSummaryTopThreeAndOwnChartFromOneWorkbook() throws Exception {
        Path excel = cableExcel(false, false);
        ReportFileService files = mock(ReportFileService.class);
        when(files.downloadToTemporaryFile("station-source")).thenReturn(new ReportFileService.TemporaryReportFile(excel));
        Path output = generator(files).generate(cableSnapshot(new RptFile().setId(1102L).setStorageObjectName("station-source")));
        try {
            Document document = new Document(output.toString());
            assertOverview(document);
            String text = document.getRange().getBookmarks().get("BM_STATION_INCIDENT").getText();
            assertTrue(text.contains("Trong tuần xảy ra 147 sự cố với 3.013PA/17.172 lượt KH ảnh hưởng."), text);
            assertTrue(text.contains("Số lượng sự cố giảm 23% (-44SC), PAKH giảm 31% (-1.364PA), "
                    + "TB ảnh hưởng giảm 35% (-9.253TB)."), text);
            assertTrue(text.contains("Tỉnh xảy ra sự cố nhiều nhất: HCM – 2SC; HNI – 1SC"), text);
            assertEquals("HCM – 2SC; HNI – 1SC",
                    document.getRange().getBookmarks().get("BM_CABLE_TOP_PROVINCES").getText());
            assertFalse(document.getText().contains("{cable"));
            assertFalse(document.getText().contains("CABLE_INCIDENT_CHART:"));
            Paragraph paragraph = (Paragraph) document.getRange().getBookmarks().get("BM_CABLE_INCIDENT_CHART")
                    .getBookmarkStart().getParentNode();
            assertEquals(1, paragraph.getChildNodes(NodeType.SHAPE, true).getCount());
            Shape image = (Shape) paragraph.getChildNodes(NodeType.SHAPE, true).get(0);
            assertEquals(1700, ImageIO.read(new ByteArrayInputStream(image.getImageData().getImageBytes())).getWidth());
            assertFalse(Arrays.equals(chart(document).getImageData().getImageBytes(), image.getImageData().getImageBytes()));
            verify(files, times(1)).downloadToTemporaryFile("station-source");
            Path directory = Paths.get("target", "station-overview");
            Files.createDirectories(directory);
            Files.copy(output, directory.resolve("preview-cable-synthetic.docx"), StandardCopyOption.REPLACE_EXISTING);
            Files.write(directory.resolve("chart-cable-synthetic.png"), image.getImageData().getImageBytes());
        } finally { Files.deleteIfExists(output); Files.deleteIfExists(excel); }
    }

    @Test
    void unavailableCableProvincesKeepsValidSummaryAndIndependentChart() throws Exception {
        Path excel = cableExcel(false, true);
        ReportFileService files = mock(ReportFileService.class);
        when(files.downloadToTemporaryFile("station-source")).thenReturn(new ReportFileService.TemporaryReportFile(excel));
        Path output = generator(files).generate(cableSnapshot(new RptFile().setId(1102L).setStorageObjectName("station-source")));
        try {
            Document document = new Document(output.toString());
            assertEquals("147", document.getRange().getBookmarks().get("BM_CABLE_COUNT").getText());
            assertEquals("Chưa có dữ liệu", document.getRange().getBookmarks().get("BM_CABLE_TOP_PROVINCES").getText());
            assertTrue(document.getText().contains("STATION_INCIDENT:"));
            assertFalse(document.getText().contains("{cable"));
            Paragraph paragraph = (Paragraph) document.getRange().getBookmarks().get("BM_CABLE_INCIDENT_CHART")
                    .getBookmarkStart().getParentNode();
            assertEquals(1, paragraph.getChildNodes(NodeType.SHAPE, true).getCount());
        } finally { Files.deleteIfExists(output); Files.deleteIfExists(excel); }
    }

    @Test
    void invalidCableSummaryDoesNotClearChartAndMissingSourceClearsNestedTokens() throws Exception {
        Path excel = cableExcel(true, false);
        ReportFileService files = mock(ReportFileService.class);
        when(files.downloadToTemporaryFile("station-source")).thenReturn(new ReportFileService.TemporaryReportFile(excel));
        Path output = generator(files).generate(cableSnapshot(new RptFile().setId(1102L).setStorageObjectName("station-source")));
        try {
            Document document = new Document(output.toString());
            assertEquals("Chưa có dữ liệu", document.getRange().getBookmarks().get("BM_STATION_INCIDENT").getText());
            Paragraph paragraph = (Paragraph) document.getRange().getBookmarks().get("BM_CABLE_INCIDENT_CHART")
                    .getBookmarkStart().getParentNode();
            assertEquals(1, paragraph.getChildNodes(NodeType.SHAPE, true).getCount());
            assertFalse(document.getText().contains("{cable"));
        } finally { Files.deleteIfExists(output); Files.deleteIfExists(excel); }
        output = generator(files).generate(cableSnapshot(null));
        try {
            Document document = new Document(output.toString());
            assertFalse(document.getText().contains("{cable"));
            assertTrue(document.getText().contains("CABLE_INCIDENT_CHART: Thiếu file nguồn 1102"));
            assertEquals("Chưa đủ dữ liệu để tạo biểu đồ", document.getRange().getBookmarks().get("BM_CABLE_INCIDENT_CHART").getText());
        } finally { Files.deleteIfExists(output); }
    }

    private Path cableExcel(boolean invalidSummary, boolean missingProvince) throws Exception {
        Path path = Files.createTempFile("cable-source-", ".xlsx");
        try (Workbook book = new XSSFWorkbook()) {
            Sheet th = book.createSheet("TH");
            WordStationIncidentReaderTest.table(th, 0, 0);
            WordStationIncidentReaderTest.cableTable(th, 0, 6);
            if (invalidSummary) th.getRow(3).getCell(6).setBlank();
            WordStationIncidentReaderTest.evaluate(book);
            Sheet station = WordStationIncidentChartReaderTest.cableSheet(book, "Lũy kế trạm năm 2026", 0);
            WordStationIncidentChartReaderTest.cableData(station, 2, 0, week, "Cháy cáp", "hcm", 10, 100);
            WordStationIncidentChartReaderTest.cableData(station, 3, 0, week.plusDays(6), "Đứt cáp", "HCM", 20, 200);
            WordStationIncidentChartReaderTest.cableData(station, 4, 0, week, "Đứt cáp", "HNI", 30, 300);
            if (missingProvince) station.getRow(0).getCell(6).setBlank();
            try (OutputStream out = Files.newOutputStream(path)) { book.write(out); }
        }
        return path;
    }

    private WordGenerationSnapshot cableSnapshot(RptFile file) {
        java.util.List<WordGenerationSnapshot.Block> blocks = new ArrayList<>(snapshot(file).getBlocks());
        for (String code : Arrays.asList("STATION_INCIDENT", "CABLE_INCIDENT_CHART")) {
            boolean summary = code.equals("STATION_INCIDENT");
            RptOutputSourceMap mapping = new RptOutputSourceMap().setSourceDefinitionId(1102L)
                    .setSourceRole(summary ? RptSourceRole.DATA : RptSourceRole.CHART_ACCUMULATED)
                    .setSourceSheetName(summary ? "TH" : "Lũy kế trạm năm {yyyy}").setSortOrder(1);
            java.util.List<WordGenerationSnapshot.Source> sources = new ArrayList<>();
            sources.add(new WordGenerationSnapshot.Source(mapping, null, file, week));
            if (summary) sources.add(new WordGenerationSnapshot.Source(new RptOutputSourceMap()
                    .setSourceDefinitionId(1102L).setSourceRole(RptSourceRole.DATA).setSortOrder(2)
                    .setSourceSheetName("Lũy kế trạm năm {yyyy}"), null, file, week));
            blocks.add(new WordGenerationSnapshot.Block(new RptOutputBlockDef().setBlockCode(code)
                    .setTargetName("BM_" + code).setRenderType(RptRenderType.CUSTOM_HANDLER), sources));
        }
        return new WordGenerationSnapshot(week, "templates/weekly-vcx-word-template.docx", blocks);
    }

    private Shape chart(Document document) throws Exception {
        Bookmark bookmark = document.getRange().getBookmarks().get("BM_STATION_INCIDENT_CHART");
        Paragraph paragraph = (Paragraph) bookmark.getBookmarkStart().getParentNode();
        NodeCollection shapes = paragraph.getChildNodes(NodeType.SHAPE, true);
        assertEquals(1, shapes.getCount(), "Chart bookmark must contain exactly one image");
        Shape shape = (Shape) shapes.get(0);
        assertTrue(shape.getImageData().getImageBytes().length > 0);
        return shape;
    }

    private WordGenerationSnapshot snapshot(RptFile file) {
        java.util.List<WordGenerationSnapshot.Block> blocks = new ArrayList<>();
        String[] codes = { "STATION_OVERVIEW", "STATION_INCIDENT_CHART" };
        for (String code : codes) {
            RptOutputSourceMap mapping = new RptOutputSourceMap().setSourceDefinitionId(1102L)
                    .setSourceRole(code.equals("STATION_OVERVIEW") ? RptSourceRole.DATA : RptSourceRole.CHART_ACCUMULATED)
                    .setSourceSheetName(code.equals("STATION_OVERVIEW") ? "TH" : "Lũy kế trạm năm {yyyy}").setSortOrder(1);
            blocks.add(new WordGenerationSnapshot.Block(new RptOutputBlockDef().setBlockCode(code)
                    .setTargetName("BM_" + code).setRenderType(RptRenderType.CUSTOM_HANDLER), Collections.singletonList(
                    new WordGenerationSnapshot.Source(mapping, null, file, week))));
        }
        return new WordGenerationSnapshot(week, "templates/weekly-vcx-word-template.docx", blocks);
    }

    private Path exampleExcel() throws Exception {
        Path path = Files.createTempFile("station-source-", ".xlsx");
        try (Workbook book = new XSSFWorkbook()) {
            WordStationIncidentReaderTest.table(book.createSheet("TH"), 0, 0);
            WordStationIncidentReaderTest.evaluate(book);
            Sheet sheet = WordStationIncidentChartReaderTest.sheet(book, "Lũy kế trạm năm 2026", 0);
            WordStationIncidentChartReaderTest.data(sheet, 2, 0, week, 5301, 143987);
            try (OutputStream out = Files.newOutputStream(path)) { book.write(out); }
        }
        return path;
    }

    private WeeklyWordOutputGenerator generator(ReportFileService files) {
        return generator(files, new WordTrendChartRenderer());
    }

    private WeeklyWordOutputGenerator generator(ReportFileService files, WordTrendChartRenderer renderer) {
        return new WeeklyWordOutputGenerator(files, new WordSectionExtractor(), new WordBookmarkRenderer(),
                new WordProductIssueReader(), new WordIncidentChartReader(),
                renderer, new WordSystemIncidentReader(), new WordMobileFeedbackReader(), new WordNetworkQualityReader(), new WordTv360FeedbackReader(), new WordSmeFeedbackReader(), new WordFixedFeedbackReader());
    }
}
