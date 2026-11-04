package com.techasians.alertservice.service.impl;

import com.aspose.words.*;
import com.techasians.alertservice.entity.*;
import com.techasians.alertservice.enums.*;
import com.techasians.alertservice.service.*;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Color;
import java.io.OutputStream;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.Collections;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WordProductIssueGenerationTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(longs = {1010L, 9010L})
    void fillsStaticSentencesWithAllRowsAsDenominatorAndFirstMatchingContent(long sourceDefinitionId) throws Exception {
        Path input = workbook(false);
        ReportFileService files = files(input);
        WordGenerationSnapshot configured = snapshot(sourceFile());
        configured.getBlocks().get(0).getSources().get(0).getMapping().setSourceDefinitionId(sourceDefinitionId);
        Path output = generator(files).generate(configured);
        try {
            Document document = new Document(output.toString());
            String text = document.getRange().getBookmarks().get("BM_PRODUCT_ISSUE").getText();
            assertTrue(text.contains("Trong tuần VCX tiếp nhận/phát hiện 4 phản ánh/góp ý cải tiến sản phẩm, dịch vụ. Kết quả:"), text);
            assertTrue(text.contains("50% (2ND) đã được các đơn vị ghi nhận để tối ưu: 1ND đã hoàn thành và 1ND đang thực hiện"), text);
            assertTrue(text.contains("25% (1ND) các đơn vị chủ trì nghiệp vụ đang đánh giá"), text);
            assertTrue(text.contains("Góp ý điển hình: Hiện trạng đầu tiên."), text);
            assertTrue(text.contains("Nguyên nhân: Nguyên nhân đầu tiên."), text);
            assertTrue(text.contains("=> Giải pháp: Kết quả xử lý đầu tiên."), text);
            assertFalse(text.contains("Tên đầu tiên"));
            assertFalse(text.contains("Giải pháp đầu tiên."));
            assertFalse(text.contains("Không lấy bất cập"));
            assertFalse(document.getText().contains("{productIssue"));
            assertEquals(3, document.getCustomDocumentProperties().get("ProductIssueSourceRow").getValue());
            assertEquals("9001", document.getCustomDocumentProperties().get("ProductIssueSourceFileId").getValue());
            assertFormattedValue(document, "TOTAL", "4");
            assertFormattedValue(document, "OPTIMIZED_RATE", "50");
            assertFormattedValue(document, "COMPLETED", "1");
            assertFormattedValue(document, "IN_PROGRESS", "1");
            verify(files, times(1)).downloadToTemporaryFile("product-issue-source");
            assertFalse(Files.exists(input));
        } finally { Files.deleteIfExists(output); }
    }

    @Test
    void missingContentSectionKeepsFirstRowAndDoesNotSubstituteAnotherRow() throws Exception {
        Path input = workbook(true);
        Path output = generator(files(input)).generate(snapshot(sourceFile()));
        try {
            Document document = new Document(output.toString());
            assertEquals("Hiện trạng đầu tiên.", value(document, "ACTUALITY"));
            assertEquals("Chưa có dữ liệu", value(document, "CAUSE"));
            assertEquals("Kết quả xử lý đầu tiên.", value(document, "PROPOSAL"));
            assertTrue(document.getText().contains("PRODUCT_ISSUE: Dòng 3 thiếu nội dung Nguyên nhân"));
            assertEquals(3, document.getCustomDocumentProperties().get("ProductIssueSourceRow").getValue());
        } finally { Files.deleteIfExists(output); }
    }

    @Test
    void missingResultDoesNotFallBackToProposalInContentOrAnotherRow() throws Exception {
        Path input = workbook(false);
        try (java.io.InputStream stream = Files.newInputStream(input);
             XSSFWorkbook book = new XSSFWorkbook(stream)) {
            book.getSheet("Bất cập DV").getRow(2).getCell(4).setCellValue("");
            book.getSheet("Bất cập DV").getRow(3).getCell(4).setCellValue("Kết quả dòng tiếp theo");
            try (OutputStream output = Files.newOutputStream(input)) { book.write(output); }
        }
        Path output = generator(files(input)).generate(snapshot(sourceFile()));
        try {
            Document document = new Document(output.toString());
            assertEquals("Chưa có dữ liệu", value(document, "PROPOSAL"));
            assertTrue(document.getText().contains("PRODUCT_ISSUE: Dòng 3 thiếu nội dung Kết quả"));
            assertEquals(3, document.getCustomDocumentProperties().get("ProductIssueSourceRow").getValue());
        } finally { Files.deleteIfExists(output); }
    }

    @Test
    void missingSourceRemovesAllNestedPlaceholders() throws Exception {
        ReportFileService files = mock(ReportFileService.class);
        Path output = generator(files).generate(snapshot(null));
        try {
            Document document = new Document(output.toString());
            assertEquals("Chưa có dữ liệu", document.getRange().getBookmarks().get("BM_PRODUCT_ISSUE").getText());
            assertFalse(document.getText().contains("{productIssue"));
            assertTrue(document.getText().contains("PRODUCT_ISSUE: Thiếu file bất cập"));
            verifyNoInteractions(files);
        } finally { Files.deleteIfExists(output); }
    }

    @Test
    void emptyWeekProducesNoTypicalIssueOrDivisionByZero() throws Exception {
        Path input = workbook(false);
        WordGenerationSnapshot original = snapshot(sourceFile());
        Path output = generator(files(input)).generate(new WordGenerationSnapshot(LocalDate.of(2027, 1, 1),
                original.getTemplatePath(), original.getBlocks()));
        try {
            Document document = new Document(output.toString());
            assertEquals("Không phát sinh bất cập có ngày phát hiện trong tuần.",
                    document.getRange().getBookmarks().get("BM_PRODUCT_ISSUE").getText());
            assertFalse(document.getText().contains("{productIssue"));
            assertNull(document.getCustomDocumentProperties().get("ProductIssueSourceRow"));
        } finally { Files.deleteIfExists(output); }
    }

    @Test
    @EnabledIfSystemProperty(named = "word.realFixtures", matches = "true")
    void generatesPreviewFromAttachedWorkbookUsingDetectionDateRatherThanWeekLabel() throws Exception {
        Path fixture;
        try (Stream<Path> paths = Files.walk(Paths.get("documents", "File 2"))) {
            fixture = paths.filter(path -> path.getFileName().toString().equals("Phu luc - TH BCDV_PAGY_2026.xlsx"))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Missing workbook fixture"));
        }
        Path copy = temporary.resolve("fixture.xlsx");
        Files.copy(fixture, copy);
        Path output = generator(files(copy)).generate(snapshot(sourceFile()));
        try {
            Document document = new Document(output.toString());
            assertEquals("3", value(document, "TOTAL"));
            assertEquals("33,3", value(document, "OPTIMIZED_RATE"));
            assertEquals("0", value(document, "COMPLETED"));
            assertEquals("1", value(document, "IN_PROGRESS"));
            assertEquals("66,7", value(document, "EVALUATING_RATE"));
            assertEquals(73, document.getCustomDocumentProperties().get("ProductIssueSourceRow").getValue());
            assertTrue(value(document, "ACTUALITY").startsWith("Ứng dụng MyBox trên Android"));
            assertEquals("Chưa có dữ liệu", value(document, "CAUSE"));
            assertEquals("TT SME (đ/c Chivtt) đang đánh giá", value(document, "PROPOSAL"));
            assertFalse(document.getText().contains("{productIssue"));
            Path preview = Paths.get("target", "product-issue", "preview-01-09-2026.docx");
            Files.createDirectories(preview.getParent());
            Files.copy(output, preview, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(output); }
    }

    private Path workbook(boolean missingCause) throws Exception {
        Path path = temporary.resolve("source.xlsx");
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            Sheet sheet = book.createSheet("Bất cập DV");
            row(sheet, 0, "Ngày phát hiện", "Tiến độ", "Nội dung", "Tên bất cập/lỗi", "Kết quả ", "Lũy kế tuần");
            row(sheet, 1, "31/8/2026", "Đã hoàn thành", "Ngoài tuần", "", "", "1/9 - 7/9");
            row(sheet, 2, "7/9/2026", "Không khả thi", "1. Thực trạng: Hiện trạng đầu tiên.\n"
                    + "2. Bất cập: Không lấy bất cập.\n"
                    + (missingCause ? "" : "3. Nguyên nhân: Nguyên nhân đầu tiên.\n")
                    + "4. Đề xuất: Giải pháp đầu tiên.", "Tên đầu tiên", "Kết quả xử lý đầu tiên.", "8/9 - 14/9");
            row(sheet, 3, "1/9/2026", "Đã hoàn thành", "Thực trạng: Dòng tiếp theo", "", "", "");
            row(sheet, 4, "4/9/2026", "Đang thực hiện", "", "", "", "");
            row(sheet, 5, "4/9/2026", "Đang đánh giá", "", "", "", "");
            row(sheet, 6, "8/9/2026", "Đã hoàn thành", "Ngoài tuần", "", "", "1/9 - 7/9");
            try (OutputStream stream = Files.newOutputStream(path)) { book.write(stream); }
        }
        return path;
    }

    private void row(Sheet sheet, int index, String... values) {
        Row row = sheet.createRow(index);
        for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
    }

    private ReportFileService files(Path path) {
        ReportFileService files = mock(ReportFileService.class);
        when(files.downloadToTemporaryFile("product-issue-source"))
                .thenReturn(new ReportFileService.TemporaryReportFile(path));
        return files;
    }

    private RptFile sourceFile() { return new RptFile().setId(9001L).setStorageObjectName("product-issue-source"); }

    private WordGenerationSnapshot snapshot(RptFile file) {
        LocalDate week = LocalDate.of(2026, 9, 1);
        RptOutputBlockDef definition = new RptOutputBlockDef().setBlockCode("PRODUCT_ISSUE")
                .setTargetName("BM_PRODUCT_ISSUE").setRenderType(RptRenderType.CUSTOM_HANDLER);
        RptOutputSourceMap mapping = new RptOutputSourceMap().setSourceDefinitionId(1010L)
                .setSourceRole(RptSourceRole.DATA).setSourceSheetName("Bất cập DV");
        return new WordGenerationSnapshot(week, "templates/weekly-vcx-word-template.docx", Collections.singletonList(
                new WordGenerationSnapshot.Block(definition, Collections.singletonList(
                        new WordGenerationSnapshot.Source(mapping, null, file, week)))));
    }

    private WeeklyWordOutputGenerator generator(ReportFileService files) {
        return new WeeklyWordOutputGenerator(files, new WordSectionExtractor(), new WordBookmarkRenderer(),
                new WordProductIssueReader(), new WordIncidentChartReader(),
                new WordTrendChartRenderer(), new WordSystemIncidentReader(), new WordMobileFeedbackReader(),
                new WordNetworkQualityReader(), new WordTv360FeedbackReader(), new WordSmeFeedbackReader(),
                new WordFixedFeedbackReader());
    }

    private String value(Document document, String suffix) throws Exception {
        return document.getRange().getBookmarks().get("BM_PRODUCT_ISSUE_" + suffix).getText();
    }

    private void assertFormattedValue(Document document, String suffix, String expected) throws Exception {
        Bookmark bookmark = document.getRange().getBookmarks().get("BM_PRODUCT_ISSUE_" + suffix);
        assertEquals(expected, bookmark.getText());
        int runs = 0;
        for (Node node = bookmark.getBookmarkStart().getNextSibling(); node != bookmark.getBookmarkEnd(); node = node.getNextSibling()) {
            assertNotNull(node);
            if (node instanceof Run) {
                runs++;
                Font font = ((Run) node).getFont();
                assertTrue(font.getBold());
                assertEquals(14, font.getSize());
                assertEquals(Color.BLACK, font.getColor());
            }
        }
        assertTrue(runs > 0);
    }
}
