package com.techasians.alertservice.service.impl;

import com.aspose.words.*;
import com.techasians.alertservice.entity.*;
import com.techasians.alertservice.enums.*;
import com.techasians.alertservice.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Color;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

class WordSmeFeedbackGenerationTest {
    private static final LocalDate WEEK = LocalDate.of(2026, 9, 1);
    private static final String HEADING = "Phản ánh DV SME: ";

    @Test
    void generatesActualReportAndSavesReviewablePreviewWithTemplateFormatting() throws Exception {
        assumeTrue(Files.isRegularFile(WordSmeFeedbackReaderTest.fixture()));
        Path input = Files.createTempFile("sme-source-", ".docx");
        Files.copy(WordSmeFeedbackReaderTest.fixture(), input, StandardCopyOption.REPLACE_EXISTING);
        ReportFileService files = files(input);
        Path output = generator(files).generate(snapshot(source()));
        try {
            Document document = new Document(output.toString());
            Bookmark bookmark = assertHeading(document);
            assertTrue(bookmark.getText().startsWith("Trong tuần tiếp nhận 1.003PA"), bookmark.getText());
            assertTrue(bookmark.getText().endsWith("Vtracking 14% (138PA); …"), bookmark.getText());
            assertSingleParagraph(bookmark);
            Paragraph paragraph = (Paragraph) bookmark.getBookmarkStart().getParentNode();
            assertEquals(0, paragraph.getChildNodes(NodeType.SHAPE, true).getCount());
            // Word dùng alpha=0 cho màu Automatic; cả Automatic và black đều hiển thị đen trong mẫu này.
            for (Run run : paragraph.getRuns()) {
                assertEquals(0, run.getFont().getColor().getRGB() & 0xFFFFFF, run.getText());
                assertEquals(14, run.getFont().getSize());
            }
            WordSmeFeedbackReaderTest.assertBoldText(paragraph, "1.003PA");
            assertFalse(document.getText().contains("Tình hình tiếp nhận và xử lý PAKH:"));
            assertFalse(document.getText().contains("Kết quả thực hiện các KPI chính:"));
            verify(files, times(1)).downloadToTemporaryFile("sme-source");
            assertFalse(Files.exists(input));
            Path preview = Paths.get("target", "sme-feedback", "preview-01-09-2026.docx");
            Files.createDirectories(preview.getParent());
            Files.copy(output, preview, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(output); Files.deleteIfExists(input); }
    }

    @ParameterizedTest
    @ValueSource(longs = {1108L, 9108L})
    void mergesTwoBulletsWithoutImportingTheirFontsOrNumbering(long definitionId) throws Exception {
        Document source = new Document();
        DocumentBuilder builder = new DocumentBuilder(source);
        builder.writeln(WordSmeFeedbackReaderTest.START);
        builder.getListFormat().applyBulletDefault();
        builder.getFont().setName("Arial");
        builder.getFont().setSize(22);
        builder.write("Trong tuần tiếp nhận ");
        builder.getFont().setBold(true);
        builder.getFont().setColor(Color.RED);
        builder.writeln("1.003PA.");
        builder.getFont().setBold(false);
        builder.getFont().setColor(Color.BLUE);
        builder.writeln("Trong đó: 43% báo lỗi.");
        builder.insertImage(WordSmeFeedbackReaderTest.IMAGE);
        Path input = Files.createTempFile("sme-two-bullets-", ".docx");
        source.save(input.toString());
        WordGenerationSnapshot configured = snapshot(source());
        configured.getBlocks().get(0).getSources().get(0).getMapping().setSourceDefinitionId(definitionId);
        configured.getBlocks().get(0).getSources().get(0).getSection().setSourceDefinitionId(definitionId);
        Path output = generator(files(input)).generate(configured);
        try {
            Document document = new Document(output.toString());
            Bookmark bookmark = assertHeading(document);
            assertEquals("Trong tuần tiếp nhận 1.003PA. Trong đó: 43% báo lỗi.", bookmark.getText());
            assertSingleParagraph(bookmark);
            Paragraph paragraph = (Paragraph) bookmark.getBookmarkStart().getParentNode();
            Bookmark templateBookmark = new Document("src/main/resources/templates/weekly-vcx-word-template.docx")
                    .getRange().getBookmarks().get("BM_SME");
            Paragraph template = (Paragraph) templateBookmark.getBookmarkStart().getParentNode();
            Run templateFont = (Run) templateBookmark.getBookmarkStart().getNextSibling();
            assertEquals(template.isListItem(), paragraph.isListItem());
            assertEquals(template.getParagraphFormat().getLeftIndent(), paragraph.getParagraphFormat().getLeftIndent());
            for (Node node = bookmark.getBookmarkStart().getNextSibling(); node != bookmark.getBookmarkEnd();
                 node = node.getNextSibling()) {
                if (!(node instanceof Run)) continue;
                Run run = (Run) node;
                assertEquals(templateFont.getFont().getName(), run.getFont().getName());
                assertEquals(14, run.getFont().getSize());
            }
            assertTrue(WordSmeFeedbackReaderTest.findRun(paragraph, "1.003PA.").getFont().getBold());
            assertEquals(Color.RED, WordSmeFeedbackReaderTest.findRun(paragraph, "1.003PA.").getFont().getColor());
            assertEquals(Color.BLUE, WordSmeFeedbackReaderTest.findRun(paragraph, "Trong đó:").getFont().getColor());
            assertFalse(WordSmeFeedbackReaderTest.findRun(paragraph, "Trong đó:").getFont().getBold());
        } finally { Files.deleteIfExists(output); Files.deleteIfExists(input); }
    }

    @Test
    void passesConfiguredSectionMarkersAndIncludeFlagsToReader() throws Exception {
        Document source = new Document();
        DocumentBuilder builder = new DocumentBuilder(source);
        builder.writeln("Bắt đầu SME theo DB:");
        builder.writeln("Nội dung được cấu hình.");
        builder.writeln("Kết thúc SME theo DB:");
        builder.writeln("Không lấy phần sau.");
        Path input = Files.createTempFile("sme-configured-markers-", ".docx");
        source.save(input.toString());
        WordGenerationSnapshot configured = snapshot(source());
        configured.getBlocks().get(0).getSources().get(0).getSection()
                .setStartMarker("Bắt đầu SME theo DB:").setEndMarker("Kết thúc SME theo DB:")
                .setIncludeStart(1).setIncludeEnd(1);
        Path output = generator(files(input)).generate(configured);
        try {
            Document document = new Document(output.toString());
            Bookmark bookmark = assertHeading(document);
            assertEquals("Bắt đầu SME theo DB: Nội dung được cấu hình. Kết thúc SME theo DB:", bookmark.getText());
            assertSingleParagraph(bookmark);
        } finally { Files.deleteIfExists(output); Files.deleteIfExists(input); }
    }

    @Test
    void missingSourceAndUnconfiguredBlockKeepHeadingAndRemovePlaceholder() throws Exception {
        ReportFileService files = mock(ReportFileService.class);
        for (boolean configured : new boolean[] {true, false}) {
            WordGenerationSnapshot snapshot = configured ? snapshot(null) : new WordGenerationSnapshot(WEEK,
                    "templates/weekly-vcx-word-template.docx", Collections.emptyList());
            Path output = generator(files).generate(snapshot);
            try {
                Document document = new Document(output.toString());
                Bookmark bookmark = assertHeading(document);
                assertEquals("Chưa có dữ liệu", bookmark.getText());
                assertSingleParagraph(bookmark);
                if (configured) assertTrue(document.getText().contains("SME: Thiếu file nguồn 1108"));
            } finally { Files.deleteIfExists(output); }
        }
        verifyNoInteractions(files);
    }

    @Test
    void missingBoundaryReportsMissingSectionInsteadOfTakingRestOfDocument() throws Exception {
        Document source = new Document();
        DocumentBuilder builder = new DocumentBuilder(source);
        builder.writeln(WordSmeFeedbackReaderTest.START);
        builder.writeln("Không được lấy lan nội dung này.");
        Path input = Files.createTempFile("sme-no-end-", ".docx");
        source.save(input.toString());
        Path output = generator(files(input)).generate(snapshot(source()));
        try {
            Document document = new Document(output.toString());
            assertEquals("Chưa có dữ liệu", assertHeading(document).getText());
            assertTrue(document.getText().contains("SME: Thiếu ảnh và marker kết thúc"));
            assertFalse(document.getText().contains("Không được lấy lan"));
        } finally { Files.deleteIfExists(output); Files.deleteIfExists(input); }
    }

    @Test
    void invalidMappingFailsWithoutDownloadingSource() {
        for (int mode = 0; mode < 12; mode++) {
            RptOutputSourceMap mapping = mapping();
            RptSectionDef section = WordSmeFeedbackReaderTest.section();
            if (mode == 0) mapping.setSourceRole(RptSourceRole.DATA);
            if (mode == 1) mapping.setSourceSheetName("Sheet1");
            if (mode == 2) mapping.setSourceSectionDefId(null);
            if (mode == 4) section = null;
            if (mode == 5) section.setId(9999L);
            if (mode == 6) section.setSourceDefinitionId(9999L);
            if (mode == 7) section.setStatus(0);
            if (mode == 8) section.setStartMarker(" ");
            if (mode == 9) section.setEndMarker(null);
            if (mode == 10) section.setExtractMode(RptExtractMode.PARAGRAPH_MARKER);
            if (mode == 11) section.setIncludeEnd(2);
            WordGenerationSnapshot.Source source = new WordGenerationSnapshot.Source(mapping, section, source(), WEEK);
            List<WordGenerationSnapshot.Source> sources = mode == 3 ? Arrays.asList(source, source) : Collections.singletonList(source);
            WordGenerationSnapshot snapshot = new WordGenerationSnapshot(WEEK, "templates/weekly-vcx-word-template.docx",
                    Collections.singletonList(new WordGenerationSnapshot.Block(block(), sources)));
            ReportFileService files = mock(ReportFileService.class);
            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> generator(files).generate(snapshot));
            assertTrue(exception.getCause() instanceof IllegalArgumentException);
            verifyNoInteractions(files);
        }
    }

    private Bookmark assertHeading(Document document) throws Exception {
        Bookmark bookmark = document.getRange().getBookmarks().get("BM_SME");
        assertNotNull(bookmark);
        Paragraph paragraph = (Paragraph) bookmark.getBookmarkStart().getParentNode();
        assertTrue(paragraph.getText().startsWith(HEADING), paragraph.getText());
        assertTrue(paragraph.getRuns().get(0).getFont().getBold());
        assertFalse(bookmark.getText().contains(HEADING));
        assertFalse(paragraph.getText().contains("[NỘI DUNG PHẢN ÁNH VỀ DỊCH VỤ]"));
        return bookmark;
    }

    private void assertSingleParagraph(Bookmark bookmark) throws Exception {
        assertSame(bookmark.getBookmarkStart().getParentNode(), bookmark.getBookmarkEnd().getParentNode());
        assertFalse(bookmark.getText().matches("(?s).*[\\r\\n\\u000b].*"), bookmark.getText());
    }

    private ReportFileService files(Path input) {
        ReportFileService files = mock(ReportFileService.class);
        when(files.downloadToTemporaryFile("sme-source")).thenReturn(new ReportFileService.TemporaryReportFile(input));
        return files;
    }

    private RptFile source() { return new RptFile().setId(1108L).setStorageObjectName("sme-source"); }
    private RptOutputSourceMap mapping() {
        return new RptOutputSourceMap().setSourceDefinitionId(1108L).setSourceRole(RptSourceRole.TEXT)
                .setSourceSectionDefId(8110L).setSortOrder(1);
    }
    private RptOutputBlockDef block() {
        return new RptOutputBlockDef().setBlockCode("SME").setTargetName("BM_SME").setRenderType(RptRenderType.CUSTOM_HANDLER);
    }
    private WordGenerationSnapshot snapshot(RptFile file) {
        return new WordGenerationSnapshot(WEEK, "templates/weekly-vcx-word-template.docx", Collections.singletonList(
                new WordGenerationSnapshot.Block(block(), Collections.singletonList(
                        new WordGenerationSnapshot.Source(mapping(), WordSmeFeedbackReaderTest.section(), file, WEEK)))));
    }
    private WeeklyWordOutputGenerator generator(ReportFileService files) {
        return new WeeklyWordOutputGenerator(files, new WordSectionExtractor(), new WordBookmarkRenderer(),
                new WordProductIssueReader(), new WordIncidentChartReader(),
                new WordTrendChartRenderer(), new WordSystemIncidentReader(), new WordMobileFeedbackReader(),
                new WordNetworkQualityReader(), new WordTv360FeedbackReader(), new WordSmeFeedbackReader(), new WordFixedFeedbackReader());
    }
}
