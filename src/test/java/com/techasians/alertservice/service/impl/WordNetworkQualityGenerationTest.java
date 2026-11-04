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

class WordNetworkQualityGenerationTest {
    private static final String HEADING = "Phản ánh Chất lượng mạng:";

    @Test
    void fillsActualWorkbookAndPreservesHeadingFormattingAndReviewablePreview() throws Exception {
        assumeTrue(Files.isRegularFile(WordNetworkQualityReaderTest.fixture()));
        Path input = Files.createTempFile("network-quality-", ".xlsx");
        Files.copy(WordNetworkQualityReaderTest.fixture(), input, StandardCopyOption.REPLACE_EXISTING);
        ReportFileService files = files(input);
        Path output = generator(files).generate(snapshot(source()));
        try {
            Document document = new Document(output.toString());
            assertHeading(document);
            assertEquals("TB tiếp nhận 147PA/ngày ~ 2,03PA/1tr TB => Xu thế giảm so với tuần trước (2,24PA).",
                    document.getRange().getBookmarks().get("BM_CLM_OVERVIEW").getText());
            String text = document.getRange().getBookmarks().get("BM_CLM").getText();
            assertTrue(text.contains("TLPA/1trTB là 0,9PA – Không đạt KPI (0,59PA). Cảnh báo 5 tỉnh không đạt:"), text);
            assertTrue(text.contains("TLPA/1trTB là 1,77PA – Đạt KPI (1,97PA). Cảnh báo 5 tỉnh không đạt:"), text);
            assertTrue(text.contains("Lào Cai: 1,93PA; Điện Biên: 1,71PA; Tuyên Quang: 1,25PA; Hồ Chí Minh: 0,98PA; Cao Bằng: 0,89PA."), text);
            assertTrue(text.contains("Hà Nội: 2,97PA; Sơn La: 2,64PA; Điện Biên: 2,11PA; Cần Thơ: 1,63PA; Khánh Hòa: 2,13PA."), text);
            assertFalse(text.contains("Thành phố"));
            assertFalse(text.contains("Tỉnh Lào Cai"));
            assertFalse(document.getText().contains("{clm"));
            assertFalse(document.getText().contains("CLM:"));
            for (String name : Arrays.asList("BM_CLM_DAILY_PA", "BM_CLM_RATE", "BM_CLM_VOICE_RATE", "BM_CLM_DATA_RATE")) {
                Bookmark bookmark = document.getRange().getBookmarks().get(name);
                assertTrue(((Run)bookmark.getBookmarkStart().getNextSibling()).getFont().getBold(), name);
            }
            verify(files, times(1)).downloadToTemporaryFile("network-quality-source");
            assertFalse(Files.exists(input));
            Path preview = Paths.get("target", "network-quality", "preview-01-09-2026.docx");
            Files.createDirectories(preview.getParent());
            Files.copy(output, preview, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(output); Files.deleteIfExists(input); }
    }

    @Test
    void missingSourceOrUnconfiguredBlockKeepsHeadingAndRemovesAllQualityTokens() throws Exception {
        ReportFileService files = mock(ReportFileService.class);
        for (boolean configured : new boolean[] {true, false}) {
            WordGenerationSnapshot snapshot = configured ? snapshot(null) : new WordGenerationSnapshot(
                    WordNetworkQualityReaderTest.WEEK, "templates/weekly-vcx-word-template.docx", Collections.emptyList());
            Path output = generator(files).generate(snapshot);
            try {
                Document document = new Document(output.toString());
                assertHeading(document);
                assertEquals("Chưa có dữ liệu", document.getRange().getBookmarks().get("BM_CLM").getText());
                assertFalse(document.getText().contains("{clm"));
                if (configured) assertTrue(document.getText().contains("CLM: Thiếu file nguồn 1107"));
            } finally { Files.deleteIfExists(output); }
        }
        verifyNoInteractions(files);
    }

    @ParameterizedTest
    @ValueSource(longs = {1107L, 9107L})
    void invalidVoiceOrMissingDataTopDoesNotClearOtherValidParts(long definitionId) throws Exception {
        Path input = excel(1);
        ReportFileService files = files(input);
        WordGenerationSnapshot configured = snapshot(source());
        configured.getBlocks().get(0).getSources().get(0).getMapping().setSourceDefinitionId(definitionId);
        Path output = generator(files).generate(configured);
        try {
            Document document = new Document(output.toString());
            assertHeading(document);
            assertEquals("Chưa có dữ liệu", document.getRange().getBookmarks().get("BM_CLM_VOICE").getText());
            assertEquals("147", document.getRange().getBookmarks().get("BM_CLM_DAILY_PA").getText());
            assertEquals("1,77", document.getRange().getBookmarks().get("BM_CLM_DATA_RATE").getText());
            assertEquals("Chưa đủ dữ liệu cảnh báo tỉnh.",
                    document.getRange().getBookmarks().get("BM_CLM_DATA_WARNING_SENTENCE").getText());
            assertTrue(document.getText().contains("DV Thoại:"));
            assertTrue(document.getText().contains("CLM: Chỉ tiêu thoại:"));
            assertFalse(document.getText().contains("{clm"));
        } finally { Files.deleteIfExists(output); Files.deleteIfExists(input); }
    }

    @Test
    void invalidOverviewPreservesBothServicesAndNeverFillsZeroForMissingRate() throws Exception {
        Path input = excel(2);
        ReportFileService files = files(input);
        Path output = generator(files).generate(snapshot(source()));
        try {
            Document document = new Document(output.toString());
            assertHeading(document);
            assertEquals("Chưa có dữ liệu", document.getRange().getBookmarks().get("BM_CLM_OVERVIEW").getText());
            assertEquals("0,9", document.getRange().getBookmarks().get("BM_CLM_VOICE_RATE").getText());
            assertEquals("1,77", document.getRange().getBookmarks().get("BM_CLM_DATA_RATE").getText());
            assertFalse(document.getText().contains("{clm"));
        } finally { Files.deleteIfExists(output); Files.deleteIfExists(input); }
    }

    @Test
    void wrongPeriodDoesNotFillAnyOtherWeek() throws Exception {
        Path input = excel(0);
        ReportFileService files = files(input);
        WordGenerationSnapshot configured = snapshot(source());
        Path output = generator(files).generate(new WordGenerationSnapshot(WordNetworkQualityReaderTest.WEEK.plusWeeks(1),
                configured.getTemplatePath(), configured.getBlocks()));
        try {
            Document document = new Document(output.toString());
            assertHeading(document);
            for (String name : Arrays.asList("BM_CLM_OVERVIEW", "BM_CLM_VOICE", "BM_CLM_DATA")) {
                assertEquals("Chưa có dữ liệu", document.getRange().getBookmarks().get(name).getText());
            }
            assertTrue(document.getText().contains("Thiếu nhóm header tuần"));
            assertFalse(document.getText().contains("{clm"));
        } finally { Files.deleteIfExists(output); Files.deleteIfExists(input); }
    }

    @Test
    void missingSheetReplacesWholeContentButKeepsHeading() throws Exception {
        Path input = excel(3);
        ReportFileService files = files(input);
        Path output = generator(files).generate(snapshot(source()));
        try {
            Document document = new Document(output.toString());
            assertHeading(document);
            assertEquals("Chưa có dữ liệu", document.getRange().getBookmarks().get("BM_CLM").getText());
            assertTrue(document.getText().contains("CLM: Thiếu sheet Tỷ lệ PA tuần"));
            assertFalse(document.getText().contains("{clm"));
        } finally { Files.deleteIfExists(output); Files.deleteIfExists(input); }
    }

    private void assertHeading(Document document) throws Exception {
        Bookmark bookmark = document.getRange().getBookmarks().get("BM_CLM");
        assertNotNull(bookmark);
        assertFalse(bookmark.getText().contains(HEADING));
        Paragraph paragraph = (Paragraph) bookmark.getBookmarkStart().getParentNode();
        assertTrue(paragraph.getText().startsWith(HEADING), paragraph.getText());
        assertTrue(paragraph.getRuns().get(0).getFont().getBold());
    }

    private Path excel(int mode) throws Exception {
        Path input = Files.createTempFile("network-quality-", ".xlsx");
        try (Workbook book = new XSSFWorkbook()) {
            Sheet sheet = WordNetworkQualityReaderTest.table(book, WordNetworkQualityReaderTest.WEEK, 0, 0);
            if (mode == 1) {
                sheet.getRow(8).getCell(0).setCellValue("Không phải KPI");
                sheet.getRow(27).getCell(15).setBlank();
            }
            if (mode == 2) sheet.getRow(3).getCell(8).setCellErrorValue(FormulaError.DIV0.getCode());
            if (mode == 3) book.setSheetName(0, "Sheet khác");
            try (OutputStream out = Files.newOutputStream(input)) { book.write(out); }
        }
        return input;
    }

    private ReportFileService files(Path input) {
        ReportFileService files = mock(ReportFileService.class);
        when(files.downloadToTemporaryFile("network-quality-source")).thenReturn(new ReportFileService.TemporaryReportFile(input));
        return files;
    }

    private RptFile source() { return new RptFile().setId(1107L).setStorageObjectName("network-quality-source"); }

    private WordGenerationSnapshot snapshot(RptFile file) {
        RptOutputSourceMap mapping = new RptOutputSourceMap().setSourceDefinitionId(1107L).setSourceRole(RptSourceRole.DATA)
                .setSourceSheetName(WordNetworkQualityReaderTest.SHEET);
        WordGenerationSnapshot.Block block = new WordGenerationSnapshot.Block(new RptOutputBlockDef()
                .setBlockCode("CLM").setTargetName("BM_CLM").setRenderType(RptRenderType.CUSTOM_HANDLER),
                Collections.singletonList(new WordGenerationSnapshot.Source(mapping, null, file, WordNetworkQualityReaderTest.WEEK)));
        return new WordGenerationSnapshot(WordNetworkQualityReaderTest.WEEK,
                "templates/weekly-vcx-word-template.docx", Collections.singletonList(block));
    }

    private WeeklyWordOutputGenerator generator(ReportFileService files) {
        return new WeeklyWordOutputGenerator(files, new WordSectionExtractor(), new WordBookmarkRenderer(),
                new WordProductIssueReader(), new WordIncidentChartReader(),
                new WordTrendChartRenderer(), new WordSystemIncidentReader(), new WordMobileFeedbackReader(), new WordNetworkQualityReader(), new WordTv360FeedbackReader(), new WordSmeFeedbackReader(), new WordFixedFeedbackReader());
    }
}
