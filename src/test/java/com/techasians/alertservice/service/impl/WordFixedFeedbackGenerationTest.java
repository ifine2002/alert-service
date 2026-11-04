package com.techasians.alertservice.service.impl;

import com.aspose.words.*;
import com.techasians.alertservice.entity.*;
import com.techasians.alertservice.enums.*;
import com.techasians.alertservice.service.*;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Color;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

class WordFixedFeedbackGenerationTest {
    private static final String TEMPLATE = "templates/weekly-vcx-word-template.docx";
    private static final String HEADING = "Phản ánh dịch vụ Cố định: ";

    @Test
    void actualWorkbookMatchesOutputSampleAndDownloadsOnceForTwoSheets() throws Exception {
        assumeTrue(Files.isRegularFile(WordFixedFeedbackReaderTest.fixture()));
        Path input = Files.createTempFile("fixed-feedback-", ".xlsx");
        Files.copy(WordFixedFeedbackReaderTest.fixture(), input, StandardCopyOption.REPLACE_EXISTING);
        ReportFileService files = files(input);
        Path output = generator(files).generate(snapshot(source()));
        try {
            Document document = new Document(output.toString());
            assertHeading(document);
            Bookmark bookmark = document.getRange().getBookmarks().get("BM_FIXED");
            assertTrue(bookmark.getText().startsWith("Tiếp nhận TB 13.134PA/ngày. Chuyển kỹ thuật xử lý ~8.231SC/ngày, "
                    + "giảm 16% so với tuần trước. TLPA/10KTB đạt 7,1PA – giảm 1,35PA so với tuần trước."), bookmark.getText());
            assertTrue(bookmark.getText().contains("Dịch vụ FTTH GPON: TLPA/10K thuê bao là 8,19PA – Đạt KPI (12PA)."));
            assertTrue(bookmark.getText().contains("Dịch vụ THS: TLPA/10K thuê bao là 1,67PA – Đạt KPI (5PA)."));
            for (String name : Arrays.asList("BM_FIXED_DAILY", "BM_FIXED_TECH_DAILY", "BM_FIXED_TECH_CHANGE", "BM_FIXED_RATE",
                    "BM_FIXED_RATE_CHANGE", "BM_FIXED_GPON_RATE", "BM_FIXED_GPON_ASSESSMENT", "BM_FIXED_THS_KPI")) {
                Bookmark token = document.getRange().getBookmarks().get(name);
                assertNotNull(token, name);
                for (Node node = token.getBookmarkStart().getNextSibling(); node != token.getBookmarkEnd(); node = node.getNextSibling()) {
                    if (!(node instanceof Run)) continue;
                    Font font = ((Run) node).getFont();
                    assertEquals(14, font.getSize(), name);
                    assertEquals(Color.BLACK, font.getColor(), name);
                    assertTrue(font.getBold(), name);
                }
            }
            assertFalse(document.getText().contains("{fixed"));
            assertFalse(document.getText().contains("FIXED:"), document.getText());
            verify(files, times(1)).downloadToTemporaryFile("fixed-source");
            assertFalse(Files.exists(input));
            Path preview = Paths.get("target", "fixed-feedback", "preview-01-09-2026.docx");
            Files.createDirectories(preview.getParent());
            Files.copy(output, preview, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(output); Files.deleteIfExists(input); }
    }

    @Test
    void missingSourceAndUnconfiguredBlockKeepHeadingAndRemoveEveryFixedToken() throws Exception {
        ReportFileService files = mock(ReportFileService.class);
        for (boolean configured : new boolean[] {true, false}) {
            WordGenerationSnapshot snapshot = configured ? snapshot(null)
                    : new WordGenerationSnapshot(WordFixedFeedbackReaderTest.WEEK, TEMPLATE, Collections.emptyList());
            Path output = generator(files).generate(snapshot);
            try {
                Document document = new Document(output.toString());
                assertHeading(document);
                assertEquals("Chưa có dữ liệu", document.getRange().getBookmarks().get("BM_FIXED").getText());
                assertFalse(document.getText().contains("{fixed"));
                for (Bookmark bookmark : document.getRange().getBookmarks()) assertDoesNotThrow(bookmark::getText);
                if (configured) assertTrue(document.getText().contains("FIXED: Thiếu file nguồn 1106"));
            } finally { Files.deleteIfExists(output); }
        }
        verifyNoInteractions(files);
    }

    @Test
    void missingOneSheetPreservesValidPartsAndRemovesTheirUnavailableTokens() throws Exception {
        for (String missing : Arrays.asList("N.Xet tuan", "TH")) {
            Path input = workbook(missing, false);
            Path output = generator(files(input)).generate(snapshot(source()));
            try {
                Document document = new Document(output.toString());
                assertHeading(document);
                assertFalse(document.getText().contains("{fixed"));
                String text = document.getRange().getBookmarks().get("BM_FIXED").getText();
                if (missing.equals("N.Xet tuan")) {
                    assertTrue(text.contains("Chưa có dữ liệu tiếp nhận."));
                    assertTrue(text.contains("Chưa có dữ liệu sự cố kỹ thuật."), text);
                    assertTrue(text.contains("Chưa có dữ liệu TLPA."), text);
                    assertFalse(text.contains("Đạt KPI"));
                } else {
                    assertTrue(text.contains("8,19PA – Chưa đủ dữ liệu đánh giá KPI."));
                    assertTrue(text.contains("1,67PA – Chưa đủ dữ liệu đánh giá KPI."));
                    assertFalse(text.contains("Đạt KPI"));
                }
                assertTrue(document.getText().contains("FIXED:"));
            } finally { Files.deleteIfExists(output); Files.deleteIfExists(input); }
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {1106L, 9106L})
    void missingPreviousPeriodPreservesCurrentValuesAndDoesNotInventTrend(long definitionId) throws Exception {
        Path input = workbook(null, true);
        WordGenerationSnapshot configured = snapshot(source());
        for (WordGenerationSnapshot.Source source : configured.getBlocks().get(0).getSources()) {
            source.getMapping().setSourceDefinitionId(definitionId);
        }
        Path output = generator(files(input)).generate(configured);
        try {
            Document document = new Document(output.toString());
            String text = document.getRange().getBookmarks().get("BM_FIXED").getText();
            assertTrue(text.contains("Chuyển kỹ thuật xử lý ~8.231SC/ngày. Chưa đủ dữ liệu so sánh với tuần trước."));
            assertTrue(text.contains("TLPA/10KTB đạt 7,1PA. Chưa đủ dữ liệu so sánh với tuần trước."));
            assertTrue(text.contains("Đạt KPI (12PA)"));
            assertFalse(text.contains("giảm 16%"));
            assertFalse(document.getText().contains("{fixed"));
        } finally { Files.deleteIfExists(output); Files.deleteIfExists(input); }
    }

    @Test
    void invalidMappingIsConfigurationErrorBeforeAnyDownload() {
        for (int mode = 0; mode < 4; mode++) {
            WordGenerationSnapshot original = snapshot(source());
            List<WordGenerationSnapshot.Source> sources = new ArrayList<>(original.getBlocks().get(0).getSources());
            RptOutputSourceMap first = sources.get(0).getMapping();
            if (mode == 0) first.setSourceRole(RptSourceRole.TEXT);
            if (mode == 1) first.setSortOrder(2);
            if (mode == 2) sources.remove(1);
            if (mode == 3) sources.set(0, new WordGenerationSnapshot.Source(first, new RptSectionDef(), source(), WordFixedFeedbackReaderTest.WEEK));
            WordGenerationSnapshot snapshot = new WordGenerationSnapshot(WordFixedFeedbackReaderTest.WEEK, TEMPLATE,
                    Collections.singletonList(new WordGenerationSnapshot.Block(original.getBlocks().get(0).getDefinition(), sources)));
            ReportFileService files = mock(ReportFileService.class);
            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> generator(files).generate(snapshot));
            assertTrue(exception.getCause() instanceof IllegalArgumentException);
            verifyNoInteractions(files);
        }
    }

    @Test
    void missingTechnicalOrRatePreservesTheOtherPartOfSummary() throws Exception {
        for (boolean missingTechnical : new boolean[] {true, false}) {
            Path input = Files.createTempFile("fixed-feedback-partial-", ".xlsx");
            try (Workbook book = WordFixedFeedbackReaderTest.workbook(0)) {
                if (missingTechnical) WordFixedFeedbackReaderTest.put(book.getSheet("N.Xet tuan"), 6, 1, "thiếu nhãn");
                else book.getSheet("N.Xet tuan").getRow(7).getCell(2).setBlank();
                try (OutputStream stream = Files.newOutputStream(input)) { book.write(stream); }
            }
            Path output = generator(files(input)).generate(snapshot(source()));
            try {
                Document document = new Document(output.toString());
                String text = document.getRange().getBookmarks().get("BM_FIXED").getText();
                assertTrue(text.contains("Tiếp nhận TB 13.134PA/ngày"), text);
                assertTrue(text.contains("8,19PA – Đạt KPI (12PA)"), text);
                if (missingTechnical) {
                    assertTrue(text.contains("Chưa có dữ liệu sự cố kỹ thuật."), text);
                    assertTrue(text.contains("TLPA/10KTB đạt 7,1PA – giảm 1,35PA"), text);
                } else {
                    assertTrue(text.contains("Chuyển kỹ thuật xử lý ~8.231SC/ngày, giảm 16%"), text);
                    assertTrue(text.contains("Chưa có dữ liệu TLPA."), text);
                }
                assertFalse(text.contains("~Chưa có"), text);
                assertFalse(document.getText().contains("{fixed"));
                assertNull(document.getRange().getBookmarks().get("BM_FIXED_SUMMARY"));
                for (String name : Arrays.asList("BM_FIXED_TECH_SENTENCE", "BM_FIXED_RATE_SENTENCE")) {
                    Bookmark sentence = document.getRange().getBookmarks().get(name);
                    assertNotNull(sentence, name);
                    Node node = sentence.getBookmarkStart().getNextSibling();
                    while (node != sentence.getBookmarkEnd()) {
                        if (node instanceof Run) {
                            assertEquals(14, ((Run) node).getFont().getSize());
                            assertEquals(Color.BLACK, ((Run) node).getFont().getColor());
                        }
                        node = node.getNextSibling();
                    }
                }
                String preservedMetric = missingTechnical ? "BM_FIXED_RATE" : "BM_FIXED_TECH_DAILY";
                Bookmark metric = document.getRange().getBookmarks().get(preservedMetric);
                assertNotNull(metric, preservedMetric);
                for (Node node = metric.getBookmarkStart().getNextSibling(); node != metric.getBookmarkEnd(); node = node.getNextSibling()) {
                    if (node instanceof Run) assertTrue(((Run) node).getFont().getBold(), preservedMetric);
                }
            } finally { Files.deleteIfExists(output); Files.deleteIfExists(input); }
        }
    }

    private Path workbook(String missingSheet, boolean missingPrevious) throws Exception {
        Path input = Files.createTempFile("fixed-feedback-", ".xlsx");
        try (Workbook book = WordFixedFeedbackReaderTest.workbook(0)) {
            if (missingSheet != null) book.removeSheetAt(book.getSheetIndex(missingSheet));
            if (missingPrevious) book.getSheet("N.Xet tuan").getRow(2).getCell(3)
                    .setCellValue(WordFixedFeedbackReaderTest.period(WordFixedFeedbackReaderTest.WEEK.minusWeeks(2)));
            try (OutputStream stream = Files.newOutputStream(input)) { book.write(stream); }
        }
        return input;
    }
    private void assertHeading(Document document) throws Exception {
        Bookmark bookmark = document.getRange().getBookmarks().get("BM_FIXED");
        assertNotNull(bookmark);
        Paragraph paragraph = (Paragraph) bookmark.getBookmarkStart().getParentNode();
        assertTrue(paragraph.getText().startsWith(HEADING), paragraph.getText());
        assertTrue(paragraph.getRuns().get(0).getFont().getBold());
        assertFalse(bookmark.getText().contains(HEADING));
        assertFalse(bookmark.getText().contains("[NỘI DUNG"));
    }
    private ReportFileService files(Path input) {
        ReportFileService files = mock(ReportFileService.class);
        when(files.downloadToTemporaryFile("fixed-source")).thenReturn(new ReportFileService.TemporaryReportFile(input));
        return files;
    }
    private RptFile source() { return new RptFile().setId(1106L).setStorageObjectName("fixed-source"); }
    private WordGenerationSnapshot snapshot(RptFile file) {
        List<WordGenerationSnapshot.Source> sources = new ArrayList<>();
        String[] names = {"N.Xet tuan", "TH"};
        for (int index = 0; index < names.length; index++) {
            sources.add(new WordGenerationSnapshot.Source(new RptOutputSourceMap().setSourceDefinitionId(1106L)
                    .setSourceRole(RptSourceRole.DATA).setSourceSheetName(names[index]).setSortOrder(index + 1),
                    null, file, WordFixedFeedbackReaderTest.WEEK));
        }
        return new WordGenerationSnapshot(WordFixedFeedbackReaderTest.WEEK, TEMPLATE, Collections.singletonList(
                new WordGenerationSnapshot.Block(new RptOutputBlockDef().setBlockCode("FIXED").setTargetName("BM_FIXED")
                        .setRenderType(RptRenderType.CUSTOM_HANDLER), sources)));
    }
    private WeeklyWordOutputGenerator generator(ReportFileService files) {
        return new WeeklyWordOutputGenerator(files, new WordSectionExtractor(), new WordBookmarkRenderer(),
                new WordProductIssueReader(), new WordIncidentChartReader(),
                new WordTrendChartRenderer(), new WordSystemIncidentReader(), new WordMobileFeedbackReader(),
                new WordNetworkQualityReader(), new WordTv360FeedbackReader(), new WordSmeFeedbackReader(), new WordFixedFeedbackReader());
    }
}
