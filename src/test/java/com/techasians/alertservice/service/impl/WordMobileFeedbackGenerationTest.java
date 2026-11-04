package com.techasians.alertservice.service.impl;

import com.aspose.words.*;
import com.techasians.alertservice.entity.*;
import com.techasians.alertservice.enums.*;
import com.techasians.alertservice.service.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.OutputStream;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

class WordMobileFeedbackGenerationTest {
    private static final String HEADING = "Phản ánh Dịch vụ di động:";

    @Test
    void fillsActualMobileFeedbackAndKeepsReviewablePreviewWithoutPlanExplanation() throws Exception {
        assumeTrue(Files.isRegularFile(WordMobileFeedbackReaderTest.fixture()));
        Path input = Files.createTempFile("mobile-source-", ".xlsm");
        Files.copy(WordMobileFeedbackReaderTest.fixture(), input, StandardCopyOption.REPLACE_EXISTING);
        ReportFileService files = files(input);
        Path output = generator(files).generate(snapshot(source()));
        try {
            Document document = new Document(output.toString());
            assertHeading(document);
            String text = document.getRange().getBookmarks().get("BM_MOBILE").getText();
            assertEquals("Tiếp nhận 1.722PA/ngày, tăng 13,4% (+204PA/ngày) so với tuần trước. "
                    + "PA tăng điển hình là Lỗi Kết nối tăng 6,8 lần.", text);
            assertFalse(document.getText().contains("do tác động từ kế hoạch"));
            assertFalse(document.getText().contains("{mobile"));
            for (String name : Arrays.asList("BM_MOBILE_AVERAGE", "BM_MOBILE_CHANGE",
                    "BM_MOBILE_SERVICE", "BM_MOBILE_SERVICE_CHANGE")) {
                Bookmark bookmark = document.getRange().getBookmarks().get(name);
                assertNotNull(bookmark);
                assertTrue(((Run)bookmark.getBookmarkStart().getNextSibling()).getFont().getBold(), name);
            }
            Bookmark details = document.getRange().getBookmarks().get("BM_MOBILE_CHANGE_DETAILS");
            assertEquals(" (+204PA/ngày)", details.getText());
            assertFalse(((Run)details.getBookmarkStart().getNextSibling()).getFont().getBold());
            Bookmark direction = document.getRange().getBookmarks().get("BM_MOBILE_DIRECTION");
            assertFalse(((Run)direction.getBookmarkStart().getNextSibling()).getFont().getBold());
            assertEquals("tăng 13,4%", document.getRange().getBookmarks().get("BM_MOBILE_CHANGE").getText());
            verify(files, times(1)).downloadToTemporaryFile("mobile-source");
            assertFalse(Files.exists(input));
            Path preview = Paths.get("target", "mobile-overview", "preview-01-09-2026.docx");
            Files.createDirectories(preview.getParent());
            Files.copy(output, preview, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(input); Files.deleteIfExists(output); }
    }

    @ParameterizedTest
    @ValueSource(longs = {1104L, 9104L})
    void missingFilePreservesHeadingAndClearsAllMobileTokens(long definitionId) throws Exception {
        ReportFileService files = mock(ReportFileService.class);
        WordGenerationSnapshot configured = snapshot(null);
        configured.getBlocks().get(0).getSources().get(0).getMapping().setSourceDefinitionId(definitionId);
        Path output = generator(files).generate(configured);
        try {
            Document document = new Document(output.toString());
            assertHeading(document);
            assertEquals("Chưa có dữ liệu", document.getRange().getBookmarks().get("BM_MOBILE").getText());
            assertFalse(document.getText().contains("{mobile"));
            assertTrue(document.getText().contains("MOBILE: Thiếu file nguồn " + definitionId));
            verifyNoInteractions(files);
        } finally { Files.deleteIfExists(output); }
    }

    @Test
    void wrongPeriodPreservesHeadingAndDoesNotFillAnotherWeek() throws Exception {
        Path input = excel(0);
        ReportFileService files = files(input);
        WordGenerationSnapshot configured = snapshot(source());
        WordGenerationSnapshot wrongWeek = new WordGenerationSnapshot(WordMobileFeedbackReaderTest.WEEK.plusWeeks(1),
                configured.getTemplatePath(), configured.getBlocks());
        Path output = generator(files).generate(wrongWeek);
        try {
            Document document = new Document(output.toString());
            assertHeading(document);
            assertEquals("Chưa có dữ liệu", document.getRange().getBookmarks().get("BM_MOBILE").getText());
            assertTrue(document.getText().contains("MOBILE: Không tìm thấy header TB tuần đúng kỳ"));
            assertFalse(document.getText().contains("{mobile"));
        } finally { Files.deleteIfExists(input); Files.deleteIfExists(output); }
    }

    @ParameterizedTest
    @ValueSource(longs = {1104L, 9104L})
    void handlesUnchangedTotalsAndUnavailableTypicalServiceWithoutLeavingNestedTokens(long definitionId) throws Exception {
        for (int mode : new int[] {1, 2}) {
            Path input = excel(mode);
            ReportFileService files = files(input);
            WordGenerationSnapshot configured = snapshot(source());
            configured.getBlocks().get(0).getSources().get(0).getMapping().setSourceDefinitionId(definitionId);
            Path output = generator(files).generate(configured);
            try {
                Document document = new Document(output.toString());
                assertHeading(document);
                String text = document.getRange().getBookmarks().get("BM_MOBILE").getText();
                assertTrue(text.startsWith("Tiếp nhận "));
                assertTrue(text.contains(mode == 1 ? "Không có PA biến động điển hình."
                        : "Chưa đủ dữ liệu để xác định PA điển hình."), text);
                assertFalse(document.getText().contains("{mobile"));
            } finally { Files.deleteIfExists(input); Files.deleteIfExists(output); }
        }
    }

    @Test
    void unconfiguredMobileBlockKeepsHeadingAndDoesNotLeakTokens() throws Exception {
        Path output = generator(mock(ReportFileService.class)).generate(new WordGenerationSnapshot(
                WordMobileFeedbackReaderTest.WEEK, "templates/weekly-vcx-word-template.docx", Collections.emptyList()));
        try {
            Document document = new Document(output.toString());
            assertHeading(document);
            assertEquals("Chưa có dữ liệu", document.getRange().getBookmarks().get("BM_MOBILE").getText());
            assertFalse(document.getText().contains("{mobile"));
        } finally { Files.deleteIfExists(output); }
    }

    private void assertHeading(Document document) throws Exception {
        Bookmark bookmark = document.getRange().getBookmarks().get("BM_MOBILE");
        assertNotNull(bookmark);
        assertFalse(bookmark.getText().contains(HEADING));
        Paragraph paragraph = (Paragraph) bookmark.getBookmarkStart().getParentNode();
        assertTrue(paragraph.getText().startsWith(HEADING), paragraph.getText());
        assertTrue(paragraph.getRuns().get(0).getFont().getBold());
        assertTrue(document.getText().contains("Phản ánh Khách hàng về dịch vụ"));
    }

    private Path excel(int mode) throws Exception {
        Path input = Files.createTempFile("mobile-source-", ".xlsx");
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = WordMobileFeedbackReaderTest.table(book, WordMobileFeedbackReaderTest.WEEK, 0);
            if (mode == 1) WordMobileFeedbackReaderTest.metric(sheet.getRow(2), 0, 100, 0, 0);
            if (mode == 2) {
                sheet.getRow(3).getCell(6).setBlank();
                sheet.getRow(4).getCell(6).setBlank();
            }
            try (OutputStream out = Files.newOutputStream(input)) { book.write(out); }
        }
        return input;
    }

    private ReportFileService files(Path input) {
        ReportFileService service = mock(ReportFileService.class);
        when(service.downloadToTemporaryFile("mobile-source")).thenReturn(new ReportFileService.TemporaryReportFile(input));
        return service;
    }

    private RptFile source() { return new RptFile().setId(1104L).setStorageObjectName("mobile-source"); }

    private WordGenerationSnapshot snapshot(RptFile file) {
        RptOutputSourceMap mapping = new RptOutputSourceMap().setSourceDefinitionId(1104L).setSourceRole(RptSourceRole.DATA)
                .setSourceSheetName(WordMobileFeedbackReaderTest.SHEET);
        WordGenerationSnapshot.Block block = new WordGenerationSnapshot.Block(new RptOutputBlockDef()
                .setBlockCode("MOBILE").setTargetName("BM_MOBILE").setRenderType(RptRenderType.CUSTOM_HANDLER),
                Collections.singletonList(new WordGenerationSnapshot.Source(mapping, null, file, WordMobileFeedbackReaderTest.WEEK)));
        return new WordGenerationSnapshot(WordMobileFeedbackReaderTest.WEEK,
                "templates/weekly-vcx-word-template.docx", Collections.singletonList(block));
    }

    private WeeklyWordOutputGenerator generator(ReportFileService files) {
        return new WeeklyWordOutputGenerator(files, new WordSectionExtractor(), new WordBookmarkRenderer(),
                new WordProductIssueReader(), new WordIncidentChartReader(),
                new WordTrendChartRenderer(), new WordSystemIncidentReader(), new WordMobileFeedbackReader(), new WordNetworkQualityReader(), new WordTv360FeedbackReader(), new WordSmeFeedbackReader(), new WordFixedFeedbackReader());
    }
}
