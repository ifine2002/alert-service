package com.techasians.alertservice.service.impl;

import com.aspose.words.*;
import com.techasians.alertservice.entity.*;
import com.techasians.alertservice.enums.*;
import com.techasians.alertservice.service.*;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.OutputStream;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

class WordTv360FeedbackGenerationTest {
    @ParameterizedTest
    @ValueSource(longs = {1112L, 9112L})
    void usesBothConfiguredSheetNamesEvenWhenSourcesAreInReverseOrder(long definitionId) throws Exception {
        Path input = excel(5);
        ReportFileService files = files(input);
        WordGenerationSnapshot configured = snapshot(source());
        for (WordGenerationSnapshot.Source source : configured.getBlocks().get(0).getSources()) {
            source.getMapping().setSourceDefinitionId(definitionId);
            source.getMapping().setSourceSheetName(source.getMapping().getSortOrder() == 1 ? "Tiếp nhận mới" : "Nguyên nhân mới");
        }
        Path output = generator(files).generate(configured);
        try {
            Document document = new Document(output.toString());
            assertEquals("176", document.getRange().getBookmarks().get("BM_TV360_DAILY").getText());
            assertEquals("46,8%", document.getRange().getBookmarks().get("BM_TV360_SUPPORT_SHARE").getText());
            assertFalse(document.getText().contains("TV360: Nguyên nhân lỗi:"));
            verify(files, times(1)).downloadToTemporaryFile("tv360-source");
        } finally { Files.deleteIfExists(output); Files.deleteIfExists(input); }
    }

    @Test
    void missingCauseFileKeepsMainDataAndDoesNotBorrowWorkbookFromOtherMapping() throws Exception {
        Path input = excel(0);
        WordGenerationSnapshot configured = snapshot(source());
        WordGenerationSnapshot.Block original = configured.getBlocks().get(0);
        java.util.List<WordGenerationSnapshot.Source> sources = new ArrayList<>();
        for (WordGenerationSnapshot.Source source : original.getSources()) sources.add(new WordGenerationSnapshot.Source(
                source.getMapping(), null, source.getMapping().getSortOrder() == 2 ? null : source.getFile(), source.getWeekStartDate()));
        WordGenerationSnapshot snapshot = new WordGenerationSnapshot(configured.getWeekStartDate(), configured.getTemplatePath(),
                Collections.singletonList(new WordGenerationSnapshot.Block(original.getDefinition(), sources)));
        Path output = generator(files(input)).generate(snapshot);
        try {
            Document document = new Document(output.toString());
            assertTrue(body(document).startsWith("Tiếp nhận TB 176SC/ngày, giảm 18%"));
            assertTrue(body(document).endsWith("Chưa đủ dữ liệu nguyên nhân lỗi."));
            assertTrue(document.getText().contains("TV360: Nguyên nhân lỗi: Thiếu file nguồn 1112"));
        } finally { Files.deleteIfExists(output); Files.deleteIfExists(input); }
    }

    @Test
    void rejectsMissingDuplicateAndWrongRoleMappingsBeforeDownloading() {
        ReportFileService files = mock(ReportFileService.class);
        for (int mode = 0; mode < 3; mode++) {
            WordGenerationSnapshot configured = snapshot(source());
            WordGenerationSnapshot.Block original = configured.getBlocks().get(0);
            java.util.List<WordGenerationSnapshot.Source> sources = new ArrayList<>(original.getSources());
            if (mode == 0) sources.remove(0); // Thiếu sort_order=2.
            if (mode == 1) sources.get(0).getMapping().setSortOrder(1);
            if (mode == 2) sources.get(0).getMapping().setSourceRole(RptSourceRole.TEXT);
            WordGenerationSnapshot snapshot = new WordGenerationSnapshot(configured.getWeekStartDate(), configured.getTemplatePath(),
                    Collections.singletonList(new WordGenerationSnapshot.Block(original.getDefinition(), sources)));
            assertInstanceOf(IllegalArgumentException.class,
                    assertThrows(IllegalStateException.class, () -> generator(files).generate(snapshot)).getCause());
        }
        verifyNoInteractions(files);
    }

    @Test
    void actualWorkbookFillsTv360PreservesFormattingAndCreatesPreview() throws Exception {
        assumeTrue(Files.isRegularFile(WordTv360FeedbackReaderTest.fixture()));
        Path input = Files.createTempFile("tv360-", ".xlsx");
        Files.copy(WordTv360FeedbackReaderTest.fixture(), input, StandardCopyOption.REPLACE_EXISTING);
        ReportFileService files = files(input);
        Path output = generator(files).generate(snapshot(source()));
        try {
            Document document = new Document(output.toString());
            assertHeading(document);
            String text = body(document);
            assertEquals("Tiếp nhận TB 176SC/ngày, giảm 18% so với cùng kỳ tuần trước."
                    + " PAKH tập trung lỗi Không xem được tất cả các kênh 57,5% và Thiết bị remote/box 32,1%."
                    + " Nguyên nhân lỗi: 46,8% Hỗ trợ KH; 20,6% do lỗi từ phía KH.", text);
            assertFalse(document.getText().contains("{tv360"));
            assertFalse(document.getText().contains("TV360: So sánh tuần trước:"));
            for (String name : Arrays.asList("BM_TV360_DAILY", "BM_TV360_CHANGE", "BM_TV360_SHARE_1", "BM_TV360_SUPPORT_SHARE")) {
                Bookmark bookmark = document.getRange().getBookmarks().get(name);
                assertTrue(((Run)bookmark.getBookmarkStart().getNextSibling()).getFont().getBold(), name);
            }
            verify(files, times(1)).downloadToTemporaryFile("tv360-source");
            assertFalse(Files.exists(input));
            Path preview = Paths.get("target", "tv360-feedback", "preview-01-09-2026.docx");
            Files.copy(output, preview, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(output); Files.deleteIfExists(input); }
    }

    @Test
    void missingSourceAndUnconfiguredBlockKeepHeadingAndRemoveAllTokens() throws Exception {
        ReportFileService files = mock(ReportFileService.class);
        for (boolean configured : new boolean[] {true, false}) {
            WordGenerationSnapshot snapshot = configured ? snapshot(null) : new WordGenerationSnapshot(
                    WordTv360FeedbackReaderTest.WEEK, "templates/weekly-vcx-word-template.docx", Collections.emptyList());
            Path output = generator(files).generate(snapshot);
            try {
                Document document = new Document(output.toString());
                assertHeading(document);
                assertEquals("Chưa có dữ liệu", body(document));
                assertFalse(document.getText().contains("{tv360"));
                if (configured) assertTrue(document.getText().contains("TV360: Thiếu file nguồn 1112"));
            } finally { Files.deleteIfExists(output); }
        }
        verifyNoInteractions(files);
    }

    @Test
    void missingPreviousWeekTopAndCausesPreserveCurrentDailyAverage() throws Exception {
        Path input = excel(1);
        Path output = generator(files(input)).generate(snapshot(source()));
        try {
            Document document = new Document(output.toString());
            assertHeading(document);
            assertEquals("Tiếp nhận TB 176SC/ngày. Chưa đủ dữ liệu so sánh với tuần trước."
                    + " Chưa đủ dữ liệu để xác định PAKH tập trung. Chưa đủ dữ liệu nguyên nhân lỗi.", body(document));
            assertFalse(document.getText().contains("{tv360"));
            assertTrue(document.getText().contains("TV360: So sánh tuần trước:"));
        } finally { Files.deleteIfExists(output); Files.deleteIfExists(input); }
    }

    @Test
    void zeroChangeAndSinglePositiveServiceRenderWithoutEmptySecondService() throws Exception {
        Path input = excel(2);
        Path output = generator(files(input)).generate(snapshot(source()));
        try {
            Document document = new Document(output.toString());
            assertTrue(body(document).contains("không đổi so với cùng kỳ tuần trước."));
            assertTrue(body(document).contains("PAKH tập trung lỗi Không xem được tất cả các kênh 100%. Nguyên nhân lỗi:"));
            assertFalse(document.getText().contains("{tv360"));
            assertEquals("", document.getRange().getBookmarks().get("BM_TV360_SECOND_SERVICE").getText());
        } finally { Files.deleteIfExists(output); Files.deleteIfExists(input); }
    }

    @Test
    void missingNdpaOrWrongPeriodKeepsHeadingAndNeverUsesAnotherWeek() throws Exception {
        for (int mode : new int[] {3, 4}) {
            Path input = excel(mode);
            Path output = generator(files(input)).generate(snapshot(source()));
            try {
                Document document = new Document(output.toString());
                assertHeading(document);
                assertEquals("Chưa có dữ liệu", body(document));
                assertFalse(document.getText().contains("{tv360"));
                assertTrue(document.getText().contains(mode == 3 ? "TV360: Thiếu sheet NDPA" : "TV360: Thiếu header tuần"));
            } finally { Files.deleteIfExists(output); Files.deleteIfExists(input); }
        }
    }

    private void assertHeading(Document document) throws Exception {
        Bookmark bookmark = document.getRange().getBookmarks().get("BM_TV360");
        assertNotNull(bookmark);
        assertFalse(bookmark.getText().contains("Phản ánh TV360:"));
        Paragraph paragraph = (Paragraph)bookmark.getBookmarkStart().getParentNode();
        assertTrue(paragraph.getText().startsWith("Phản ánh TV360:"), paragraph.getText());
        assertTrue(paragraph.getRuns().get(0).getFont().getBold());
    }

    private String body(Document document) throws Exception { return document.getRange().getBookmarks().get("BM_TV360").getText(); }

    private Path excel(int mode) throws Exception {
        Path input = Files.createTempFile("tv360-", ".xlsx");
        try (Workbook book = WordTv360FeedbackReaderTest.workbook(WordTv360FeedbackReaderTest.WEEK, 0, 0)) {
            if (mode == 1) {
                WordTv360FeedbackReaderTest.put(book.getSheet("NDPA"), 2, 5, 0);
                WordTv360FeedbackReaderTest.put(book.getSheet("NDPA"), 4, 2, "#N/A");
                book.removeSheetAt(book.getSheetIndex("NNLOI"));
            }
            if (mode == 2) {
                WordTv360FeedbackReaderTest.put(book.getSheet("NDPA"), 2, 5, 176);
                WordTv360FeedbackReaderTest.put(book.getSheet("NDPA"), 3, 2, 0);
                WordTv360FeedbackReaderTest.put(book.getSheet("NDPA"), 4, 2, 1);
                WordTv360FeedbackReaderTest.put(book.getSheet("NDPA"), 5, 2, 0);
            }
            if (mode == 3) book.setSheetName(0, "Sheet khác");
            if (mode == 4) WordTv360FeedbackReaderTest.put(book.getSheet("NDPA"), 0, 2, "01/09/2025-07/09/2025");
            if (mode == 5) { book.setSheetName(0, "Tiếp nhận mới"); book.setSheetName(1, "Nguyên nhân mới"); }
            try (OutputStream out = Files.newOutputStream(input)) { book.write(out); }
        }
        return input;
    }

    private ReportFileService files(Path input) {
        ReportFileService files = mock(ReportFileService.class);
        when(files.downloadToTemporaryFile("tv360-source")).thenReturn(new ReportFileService.TemporaryReportFile(input));
        return files;
    }

    private RptFile source() { return new RptFile().setId(1112L).setStorageObjectName("tv360-source"); }

    private WordGenerationSnapshot snapshot(RptFile file) {
        RptOutputSourceMap mapping = new RptOutputSourceMap().setSourceDefinitionId(1112L).setSourceRole(RptSourceRole.DATA).setSourceSheetName("NDPA").setSortOrder(1);
        RptOutputSourceMap causes = new RptOutputSourceMap().setSourceDefinitionId(1112L).setSourceRole(RptSourceRole.DATA).setSourceSheetName("NNLOI").setSortOrder(2);
        WordGenerationSnapshot.Block block = new WordGenerationSnapshot.Block(new RptOutputBlockDef()
                .setBlockCode("TV360").setTargetName("BM_TV360").setRenderType(RptRenderType.CUSTOM_HANDLER),
                Arrays.asList(new WordGenerationSnapshot.Source(causes, null, file, WordTv360FeedbackReaderTest.WEEK),
                        new WordGenerationSnapshot.Source(mapping, null, file, WordTv360FeedbackReaderTest.WEEK)));
        return new WordGenerationSnapshot(WordTv360FeedbackReaderTest.WEEK, "templates/weekly-vcx-word-template.docx", Collections.singletonList(block));
    }

    private WeeklyWordOutputGenerator generator(ReportFileService files) {
        return new WeeklyWordOutputGenerator(files, new WordSectionExtractor(), new WordBookmarkRenderer(),
                new WordProductIssueReader(), new WordIncidentChartReader(),
                new WordTrendChartRenderer(), new WordSystemIncidentReader(), new WordMobileFeedbackReader(),
                new WordNetworkQualityReader(), new WordTv360FeedbackReader(), new WordSmeFeedbackReader(), new WordFixedFeedbackReader());
    }
}
