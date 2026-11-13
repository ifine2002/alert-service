package com.techasians.alertservice.service.impl;

import com.aspose.words.*;
import com.techasians.alertservice.entity.*;
import com.techasians.alertservice.enums.*;
import com.techasians.alertservice.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeeklyWordOutputGeneratorTest {
    @Test
    void alignsAnyCopySectionAndPreservesRelativeIndentsAfterSaveAndReload() throws Exception {
        Document sourceDocument = new Document();
        DocumentBuilder builder = new DocumentBuilder(sourceDocument);
        builder.writeln("BẮT ĐẦU");
        builder.getListFormat().applyBulletDefault();
        builder.getListFormat().getListLevel().setNumberFormat("☞");
        builder.getParagraphFormat().setLeftIndent(10);
        builder.getParagraphFormat().setFirstLineIndent(-20);
        builder.getFont().setBold(true);
        builder.getFont().setSize(22);
        builder.writeln("Tiêu đề nguồn cần căn");
        builder.getListFormat().applyBulletDefault();
        builder.getListFormat().getListLevel().setNumberFormat("•");
        builder.getParagraphFormat().setLeftIndent(36);
        builder.getParagraphFormat().setFirstLineIndent(-18);
        builder.getFont().setBold(false);
        builder.getFont().setSize(9);
        builder.writeln("Chi tiết cần giữ lề tương đối");
        builder.getListFormat().removeNumbers();
        builder.writeln("KẾT THÚC");
        Path sourcePath = Files.createTempFile("copy-section-alignment-", ".docx");
        Path outputPath = null;
        try {
            sourceDocument.save(sourcePath.toString());
            ReportFileService files = mock(ReportFileService.class);
            when(files.downloadToTemporaryFile("shared-word-source"))
                    .thenReturn(new ReportFileService.TemporaryReportFile(sourcePath));
            LocalDate week = LocalDate.of(2026, 9, 1);
            List<WordGenerationSnapshot.Block> blocks = new ArrayList<>();
            for (String target : Arrays.asList("BM_INCIDENT", "BM_PROGRAM_EVENT")) {
                RptSectionDef section = new RptSectionDef().setSectionName("ANY_SECTION")
                        .setStartMarker("BẮT ĐẦU").setEndMarker("KẾT THÚC").setIncludeStart(0).setIncludeEnd(0);
                WordGenerationSnapshot.Source source = new WordGenerationSnapshot.Source(
                        new RptOutputSourceMap().setSourceDefinitionId(1101L), section,
                        new RptFile().setId(1101L).setStorageObjectName("shared-word-source"), week);
                blocks.add(new WordGenerationSnapshot.Block(new RptOutputBlockDef().setBlockCode(target)
                        .setTargetName(target).setRenderType(RptRenderType.COPY_SECTION), Collections.singletonList(source)));
            }
            outputPath = generator(files).generate(new WordGenerationSnapshot(week,
                    "templates/weekly-vcx-word-template.docx", blocks));
            Document output = new Document(outputPath.toString());
            int titles = 0;
            int details = 0;
            NodeCollection paragraphs = output.getChildNodes(NodeType.PARAGRAPH, true);
            for (int index = 0; index < paragraphs.getCount(); index++) {
                Paragraph paragraph = (Paragraph) paragraphs.get(index);
                if (paragraph.getText().trim().equals("Tiêu đề nguồn cần căn")) {
                    titles++;
                    assertEquals(20, paragraph.getParagraphFormat().getLeftIndent(), 0.01);
                    assertEquals(-20, paragraph.getParagraphFormat().getFirstLineIndent(), 0.01);
                    assertEquals(-10, paragraph.getParagraphFormat().getRightIndent(), 0.01);
                    assertEquals("☞", paragraph.getListFormat().getListLevel().getNumberFormat());
                    assertTrue(paragraph.getRuns().get(0).getFont().getBold());
                    assertEquals(14, paragraph.getRuns().get(0).getFont().getSize());
                } else if (paragraph.getText().trim().equals("Chi tiết cần giữ lề tương đối")) {
                    details++;
                    assertEquals(46, paragraph.getParagraphFormat().getLeftIndent(), 0.01);
                    assertEquals(-18, paragraph.getParagraphFormat().getFirstLineIndent(), 0.01);
                    assertFalse(paragraph.getRuns().get(0).getFont().getBold());
                    assertEquals(14, paragraph.getRuns().get(0).getFont().getSize());
                }
            }
            assertEquals(2, titles);
            assertEquals(2, details);
            verify(files, times(1)).downloadToTemporaryFile("shared-word-source");
        } finally {
            Files.deleteIfExists(sourcePath);
            if (outputPath != null) Files.deleteIfExists(outputPath);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "2026-09-01, 01/09 - 07/09/2026, 2026",
            "2026-12-28, 28/12 - 03/01/2027, 2027"
    })
    void producesMissingDataDocumentWithoutDownloadingAbsentSources(String weekStart, String expectedPeriod,
                                                                     String expectedYear) throws Exception {
        ReportFileService files = mock(ReportFileService.class);
        RptOutputBlockDef definition = new RptOutputBlockDef().setId(8001L)
                .setBlockCode("MOBILE").setTargetName("BM_MOBILE").setRenderType(RptRenderType.COPY_SECTION);
        RptOutputSourceMap mapping = new RptOutputSourceMap().setSourceDefinitionId(1103L);
        LocalDate week = LocalDate.parse(weekStart);
        WordGenerationSnapshot.Source source = new WordGenerationSnapshot.Source(mapping,
                new RptSectionDef().setStartMarker("Tình hình tiếp nhận chung"), null, week);
        WordGenerationSnapshot snapshot = new WordGenerationSnapshot(week,
                "templates/weekly-vcx-word-template.docx", Collections.singletonList(
                new WordGenerationSnapshot.Block(definition, Collections.singletonList(source))));
        WeeklyWordOutputGenerator generator = new WeeklyWordOutputGenerator(files,
                new WordSectionExtractor(), new WordBookmarkRenderer(), new WordProductIssueReader(),
                new WordIncidentChartReader(), new WordTrendChartRenderer(),
                new WordSystemIncidentReader(), new WordMobileFeedbackReader(), new WordNetworkQualityReader(), new WordTv360FeedbackReader(), new WordSmeFeedbackReader(), new WordFixedFeedbackReader());
        java.nio.file.Path outputPath = generator.generate(snapshot);
        try {
            Document output = new Document(outputPath.toString());
            assertTrue(output.getText().contains("Chưa có dữ liệu"));
            assertTrue(output.getText().contains(expectedPeriod));
            assertTrue(output.getText().contains("năm " + expectedYear));
            assertFalse(output.getText().contains("[yyyy]"));
            for (String name : Arrays.asList("BM_REPORT_PERIOD_TITLE", "BM_REPORT_PERIOD_INTRO",
                    "BM_REPORT_PERIOD_CLOSING", "BM_REPORT_YEAR")) {
                Bookmark period = output.getRange().getBookmarks().get(name);
                assertNotNull(period, name);
                boolean year = name.equals("BM_REPORT_YEAR");
                assertEquals(year ? expectedYear : expectedPeriod, period.getText());
                boolean emphasized = name.equals("BM_REPORT_PERIOD_TITLE") || year;
                int runs = 0;
                for (Node node = period.getBookmarkStart().getNextSibling(); node != period.getBookmarkEnd();
                     node = node.getNextSibling()) {
                    assertNotNull(node, "Date bookmark must stay within its paragraph");
                    if (!(node instanceof Run)) continue;
                    Font font = ((Run) node).getFont();
                    assertEquals(Color.BLACK, font.getColor(), name);
                    assertEquals(14, font.getSize(), name);
                    assertEquals(emphasized, font.getBold(), name);
                    assertEquals(emphasized ? Underline.SINGLE : Underline.NONE, font.getUnderline(), name);
                    runs++;
                }
                assertTrue(runs > 0, name);
            }
            assertFalse(output.getText().contains("[DD]"));
            assertFalse(output.getText().contains("[SỐ]"));
            assertFalse(output.getText().contains("{a}"));
            assertNull(output.getRange().getBookmarks().get("BM_REPORT_DATE"));
            assertNull(output.getRange().getBookmarks().get("BM_REPORT_NUMBER"));
            verifyNoInteractions(files);
        } finally { Files.deleteIfExists(outputPath); }
    }

    @Test
    void fillsStaticTemplateSentenceAndPreservesBoldBookmarks() throws Exception {
        ReportFileService files = mock(ReportFileService.class);
        Path excel = exampleExcel();
        when(files.downloadToTemporaryFile("incident-source"))
                .thenReturn(new ReportFileService.TemporaryReportFile(excel));
        Path outputPath = generator(files).generate(systemIncidentSnapshot(
                new RptFile().setId(1102L).setStorageObjectName("incident-source")));
        try {
            Document output = new Document(outputPath.toString());
            // Bullet tự động của Word nằm ở paragraph, không thuộc nội dung bookmark.
            assertEquals("Trong tuần xảy ra 3SC ~ 37PA từ Khách hàng. Số lượng sự cố "
                            + "không có biến động, PAKH giảm 73% (99PA) so với tuần trước.",
                    output.getRange().getBookmarks().get("BM_SYSTEM_INCIDENT").getText().replaceFirst("^-\\s+", ""));
            assertBold(output, "BM_SYSTEM_INCIDENT_COUNT", "3");
            assertBold(output, "BM_SYSTEM_INCIDENT_PA", "37");
            assertBold(output, "BM_SYSTEM_INCIDENT_SC_CHANGE", "không có biến động");
            assertBold(output, "BM_SYSTEM_INCIDENT_PA_CHANGE", "PAKH giảm 73% (99PA)");
            assertFalse(output.getText().contains("{a}"));
            assertFalse(output.getText().contains("{paChange}"));
            assertFalse(output.getText().contains("SYSTEM_INCIDENT:"));
            verify(files, times(1)).downloadToTemporaryFile("incident-source");
            assertFalse(Files.exists(excel), "Downloaded temporary workbook must be cleaned up");
        } finally {
            Files.deleteIfExists(outputPath);
            Files.deleteIfExists(excel);
        }
    }

    @Test
    void missingIncidentSourceReplacesWholeSentenceAndRemovesNestedTokens() throws Exception {
        ReportFileService files = mock(ReportFileService.class);
        Path outputPath = generator(files).generate(systemIncidentSnapshot(null));
        try {
            Document output = new Document(outputPath.toString());
            assertEquals("Chưa có dữ liệu", output.getRange().getBookmarks().get("BM_SYSTEM_INCIDENT").getText());
            assertTrue(output.getText().contains("SYSTEM_INCIDENT: Thiếu file nguồn 1102"));
            assertFalse(output.getText().contains("{scChange}"));
            assertFalse(output.getText().contains("{a}"));
            verifyNoInteractions(files);
        } finally { Files.deleteIfExists(outputPath); }
    }

    @Test
    void invalidIncidentTableProducesWarningInsteadOfFabricatedSummary() throws Exception {
        ReportFileService files = mock(ReportFileService.class);
        Path excel = exampleExcel();
        try (java.io.InputStream input = Files.newInputStream(excel);
             org.apache.poi.ss.usermodel.Workbook book = org.apache.poi.ss.usermodel.WorkbookFactory.create(input)) {
            book.getSheet("TH").getRow(0).getCell(0).setCellValue("Sự cố VTT");
            try (java.io.OutputStream stream = Files.newOutputStream(excel)) { book.write(stream); }
        }
        when(files.downloadToTemporaryFile("incident-source"))
                .thenReturn(new ReportFileService.TemporaryReportFile(excel));
        Path outputPath = generator(files).generate(systemIncidentSnapshot(
                new RptFile().setId(1102L).setStorageObjectName("incident-source")));
        try {
            Document output = new Document(outputPath.toString());
            assertEquals("Chưa có dữ liệu", output.getRange().getBookmarks().get("BM_SYSTEM_INCIDENT").getText());
            assertTrue(output.getText().contains("SYSTEM_INCIDENT: Không tìm thấy bảng Tổng lỗi phát sinh"));
            assertFalse(output.getText().contains("{paChange}"));
        } finally {
            Files.deleteIfExists(outputPath);
            Files.deleteIfExists(excel);
        }
    }

    private WeeklyWordOutputGenerator generator(ReportFileService files) {
        return new WeeklyWordOutputGenerator(files, new WordSectionExtractor(), new WordBookmarkRenderer(),
                new WordProductIssueReader(), new WordIncidentChartReader(),
                new WordTrendChartRenderer(), new WordSystemIncidentReader(), new WordMobileFeedbackReader(), new WordNetworkQualityReader(), new WordTv360FeedbackReader(), new WordSmeFeedbackReader(), new WordFixedFeedbackReader());
    }

    private WordGenerationSnapshot systemIncidentSnapshot(RptFile file) {
        RptOutputBlockDef definition = new RptOutputBlockDef().setId(8201L).setBlockCode("SYSTEM_INCIDENT")
                .setTargetName("BM_SYSTEM_INCIDENT").setRenderType(RptRenderType.CUSTOM_HANDLER);
        RptOutputSourceMap mapping = new RptOutputSourceMap().setSourceDefinitionId(1102L)
                .setSourceRole(RptSourceRole.DATA).setSourceSheetName("TH");
        LocalDate week = LocalDate.of(2026, 9, 1);
        WordGenerationSnapshot.Source source = new WordGenerationSnapshot.Source(mapping, null, file, week);
        return new WordGenerationSnapshot(week, "templates/weekly-vcx-word-template.docx", Collections.singletonList(
                new WordGenerationSnapshot.Block(definition, Collections.singletonList(source))));
    }

    private Path exampleExcel() throws Exception {
        Path path = Files.createTempFile("system-incident-source-", ".xlsx");
        try (org.apache.poi.ss.usermodel.Workbook book = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet sheet = book.createSheet("TH");
            String[] headers = { "Tổng lỗi phát sinh", "Tuần trước", "Tuần này", "So sánh", "Tỷ lệ tăng giảm" };
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            org.apache.poi.ss.usermodel.Row incidents = sheet.createRow(1);
            incidents.createCell(0).setCellValue("Số lượng");
            incidents.createCell(1).setCellValue(3);
            incidents.createCell(2).setCellValue(3);
            org.apache.poi.ss.usermodel.Row pa = sheet.createRow(2);
            pa.createCell(0).setCellValue("PA");
            pa.createCell(1).setCellValue(136);
            pa.createCell(2).setCellValue(37);
            pa.createCell(3).setCellValue(-99);
            pa.createCell(4).setCellValue(-73);
            try (java.io.OutputStream stream = Files.newOutputStream(path)) { book.write(stream); }
        }
        return path;
    }

    private void assertBold(Document document, String name, String expected) throws Exception {
        Bookmark bookmark = document.getRange().getBookmarks().get(name);
        assertNotNull(bookmark);
        assertEquals(expected, bookmark.getText());
        boolean hasRun = false;
        for (Node node = bookmark.getBookmarkStart().getNextSibling(); node != bookmark.getBookmarkEnd();
             node = node.getNextSibling()) {
            assertNotNull(node, "Bookmark must end in the same paragraph");
            if (node instanceof Run && !((Run) node).getText().isEmpty()) {
                hasRun = true;
                assertTrue(((Run) node).getFont().getBold(), name + " must remain bold");
            }
        }
        assertTrue(hasRun);
    }
}
