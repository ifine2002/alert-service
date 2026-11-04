package com.techasians.alertservice.service.impl;

import com.aspose.words.*;
import com.techasians.alertservice.entity.*;
import com.techasians.alertservice.enums.*;
import com.techasians.alertservice.service.*;
import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

class WordProgramReactionGenerationTest {
    private static final String PROGRAM = "CÁC CHƯƠNG TRÌNH/SỰ KIỆN TRỌNG ĐIỂM, NỔI BẬT";
    private static final String REACTION = "PHẢN ỨNG KH VỀ CÁC CHƯƠNG TRÌNH/CHÍNH SÁCH MỚI/ĐIỀU CHỈNH";
    private static final String TEMPLATE = "templates/weekly-vcx-word-template.docx";
    private static final LocalDate WEEK = LocalDate.of(2026, 9, 1);

    @Test
    void copiesBothCompleteSectionsFromUpdated1119WithTablesImagesAndFormatting() throws Exception {
        Path fixture = fixture();
        assumeTrue(fixture != null, "Missing Word 1119 fixture");
        byte[] original = Files.readAllBytes(fixture);
        Document source = new Document(fixture.toString());
        List<Node> programs = range(source, PROGRAM, REACTION);
        List<Node> reactions = range(source, REACTION, null);
        Path downloaded = Files.createTempFile("program-reaction-source-", ".docx");
        Files.copy(fixture, downloaded, StandardCopyOption.REPLACE_EXISTING);
        ReportFileService files = mock(ReportFileService.class);
        when(files.downloadToTemporaryFile("word-1119"))
                .thenReturn(new ReportFileService.TemporaryReportFile(downloaded));
        Path output = null;
        try {
            output = generator(files).generate(snapshot(new RptFile().setId(1119L).setStorageObjectName("word-1119")));
            Document result = new Document(output.toString());
            assertCompleteSection(result, "BM_PROGRAM_EVENT", programs);
            assertCompleteSection(result, "BM_CUSTOMER_REACTION", reactions);
            for (String prefix : Arrays.asList("FO xử lý:", "BO xử lý:", "Chuyển tỉnh xử lý:")) {
                assertParagraphFonts(source, result, prefix);
            }
            String events = result.getRange().getBookmarks().get("BM_PROGRAM_EVENT").getText();
            String feedback = result.getRange().getBookmarks().get("BM_CUSTOMER_REACTION").getText();
            assertTrue(events.contains("Phản ứng khách hàng về hoạt động xác thực khi đổi máy"));
            assertTrue(events.contains("Kết quả đo kiểm chất lượng mạng trải nghiệm người dùng"));
            assertTrue(events.contains("Triển khai kế hoạch Tắt sóng 2G"));
            assertTrue(feedback.contains("Chính sách nâng băng thông và giá cước dịch vụ FTTH"));
            assertTrue(feedback.contains("Điều chỉnh tăng giá cước gói cước ĐẲNG CẤP BOX"));
            assertTrue(feedback.contains("Triển khai cập nhật sơ đồ nghề cá mới"));
            assertTrue(feedback.contains("90,25% (213TH)"), "Must retain the last item of section II");
            assertFalse(events.contains("Chính sách nâng băng thông"));
            assertFalse(feedback.contains("Triển khai kế hoạch Tắt sóng 2G"));
            assertEquals(1, occurrences(result.getText(), PROGRAM));
            assertEquals(1, occurrences(result.getText(), REACTION));
            assertFalse(result.getText().contains("Kính gửi: Ban Giám đốc Công ty"));
            assertFalse(result.getText().contains("PROGRAM_EVENT:"));
            assertFalse(result.getText().contains("CUSTOMER_REACTION:"));
            assertFalse(result.getText().contains("[NỘI DUNG CÁC CHƯƠNG TRÌNH"));
            verify(files, times(1)).downloadToTemporaryFile("word-1119");
            assertFalse(Files.exists(downloaded));
            assertArrayEquals(original, Files.readAllBytes(fixture));
            Path preview = Paths.get("target", "program-reaction", "preview-01-09-2026.docx");
            Files.createDirectories(preview.getParent());
            Files.copy(output, preview, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (output != null) Files.deleteIfExists(output);
            Files.deleteIfExists(downloaded);
        }
    }

    @Test
    void preservesInheritedAndExplicitFontsWithoutForcingOneFontOnAllCopiedText() throws Exception {
        Document source = new Document();
        Style caption = source.getStyles().add(StyleType.PARAGRAPH, "SourceCaption");
        caption.getFont().setName("Times New Roman");
        DocumentBuilder builder = new DocumentBuilder(source);
        builder.writeln(PROGRAM);
        builder.startTable();
        builder.insertCell();
        builder.getParagraphFormat().setStyleName("SourceCaption");
        builder.getFont().clearFormatting();
        builder.getFont().setBold(true);
        builder.write("FO xử lý: 91,3%");
        builder.insertCell();
        builder.getFont().setName("Courier New");
        builder.getFont().setBold(false);
        builder.getFont().setItalic(true);
        builder.write("BO xử lý: 7,8%");
        builder.endRow();
        builder.endTable();
        builder.getParagraphFormat().clearFormatting();
        builder.getFont().clearFormatting();
        builder.getFont().setName("Times New Roman");
        builder.writeln("Chuyển tỉnh xử lý: 0,9%");
        builder.writeln(REACTION);
        builder.write("Nội dung phần II");
        Path downloaded = Files.createTempFile("program-reaction-mixed-fonts-", ".docx");
        source.save(downloaded.toString());
        Document savedSource = new Document(downloaded.toString());
        ReportFileService files = mock(ReportFileService.class);
        when(files.downloadToTemporaryFile("word-1119"))
                .thenReturn(new ReportFileService.TemporaryReportFile(downloaded));
        Path output = null;
        try {
            output = generator(files).generate(snapshot(new RptFile().setId(1119L).setStorageObjectName("word-1119")));
            Document result = new Document(output.toString());
            for (String prefix : Arrays.asList("FO xử lý:", "BO xử lý:", "Chuyển tỉnh xử lý:")) {
                assertParagraphFonts(savedSource, result, prefix);
            }
            assertEquals("Times New Roman", findParagraph(result, "FO xử lý:").getRuns().get(0).getFont().getNameAscii());
            assertEquals("Courier New", findParagraph(result, "BO xử lý:").getRuns().get(0).getFont().getNameAscii());
        } finally {
            if (output != null) Files.deleteIfExists(output);
            Files.deleteIfExists(downloaded);
        }
    }

    @Test
    void missingFileAndUnconfiguredBlocksReplaceBothPlaceholdersAndKeepHeadings() throws Exception {
        ReportFileService files = mock(ReportFileService.class);
        for (boolean configured : new boolean[] {true, false}) {
            WordGenerationSnapshot snapshot = configured ? snapshot(null)
                    : new WordGenerationSnapshot(WEEK, TEMPLATE, Collections.emptyList());
            Path output = generator(files).generate(snapshot);
            try {
                Document result = new Document(output.toString());
                for (String name : Arrays.asList("BM_PROGRAM_EVENT", "BM_CUSTOMER_REACTION")) {
                    assertEquals("Chưa có dữ liệu", result.getRange().getBookmarks().get(name).getText());
                }
                assertEquals(1, occurrences(result.getText(), PROGRAM));
                assertEquals(1, occurrences(result.getText(), REACTION));
                if (configured) {
                    assertTrue(result.getText().contains("PROGRAM_EVENT: Thiếu file nguồn 1119"));
                    assertTrue(result.getText().contains("CUSTOMER_REACTION: Thiếu file nguồn 1119"));
                }
            } finally { Files.deleteIfExists(output); }
        }
        verifyNoInteractions(files);
    }

    @Test
    void missingSecondHeadingDoesNotCopyUnboundedContentIntoEitherSection() throws Exception {
        Document input = new Document();
        DocumentBuilder builder = new DocumentBuilder(input);
        builder.writeln(PROGRAM);
        builder.writeln("Nội dung chưa phân chia đủ hai phần");
        Path downloaded = Files.createTempFile("program-reaction-missing-boundary-", ".docx");
        input.save(downloaded.toString());
        ReportFileService files = mock(ReportFileService.class);
        when(files.downloadToTemporaryFile("word-1119"))
                .thenReturn(new ReportFileService.TemporaryReportFile(downloaded));
        Path output = null;
        try {
            output = generator(files).generate(snapshot(new RptFile().setId(1119L).setStorageObjectName("word-1119")));
            Document result = new Document(output.toString());
            assertEquals("Chưa có dữ liệu", result.getRange().getBookmarks().get("BM_PROGRAM_EVENT").getText());
            assertEquals("Chưa có dữ liệu", result.getRange().getBookmarks().get("BM_CUSTOMER_REACTION").getText());
            assertFalse(result.getText().contains("Nội dung chưa phân chia"));
            assertTrue(result.getText().contains("PROGRAM_EVENT: Không tìm thấy marker:"));
            assertTrue(result.getText().contains("CUSTOMER_REACTION: Không tìm thấy marker:"));
        } finally {
            if (output != null) Files.deleteIfExists(output);
            Files.deleteIfExists(downloaded);
        }
    }

    private void assertCompleteSection(Document output, String name, List<Node> expected) throws Exception {
        Bookmark bookmark = output.getRange().getBookmarks().get(name);
        assertNotNull(bookmark);
        StringBuilder text = new StringBuilder();
        int tables = 0;
        int shapes = 0;
        for (Node node : expected) {
            text.append(node.getText());
            if (node instanceof Table) tables++;
            if (node instanceof CompositeNode) shapes += ((CompositeNode) node).getChildNodes(NodeType.SHAPE, true).getCount();
        }
        assertEquals(plain(text.toString()), plain(bookmark.getText()), name + " must contain the complete source range");
        int actualTables = 0;
        int actualShapes = 0;
        for (Node node = bookmark.getBookmarkStart().nextPreOrder(output);
             node != bookmark.getBookmarkEnd(); node = node.nextPreOrder(output)) {
            assertNotNull(node, name + " must have an end marker");
            if (node instanceof Table) actualTables++;
            if (node instanceof Shape) actualShapes++;
            if (node instanceof Run) assertEquals(14, ((Run) node).getFont().getSize(), name);
        }
        assertEquals(tables, actualTables, name);
        assertEquals(shapes, actualShapes, name);
    }

    private void assertParagraphFonts(Document source, Document output, String prefix) throws Exception {
        Paragraph before = findParagraph(source, prefix);
        Paragraph after = findParagraph(output, prefix);
        assertEquals(before.getRuns().getCount(), after.getRuns().getCount(), prefix);
        for (int index = 0; index < before.getRuns().getCount(); index++) {
            Run original = before.getRuns().get(index);
            Run copy = after.getRuns().get(index);
            assertEquals(original.getText(), copy.getText(), prefix);
            assertEquals(original.getFont().getNameAscii(), copy.getFont().getNameAscii(), prefix + " ASCII font");
            assertEquals(original.getFont().getNameOther(), copy.getFont().getNameOther(), prefix + " Vietnamese font");
            assertEquals(original.getFont().getNameFarEast(), copy.getFont().getNameFarEast(), prefix + " East Asian font");
            assertEquals(original.getFont().getNameBi(), copy.getFont().getNameBi(), prefix + " complex script font");
            assertEquals(original.getFont().getBold(), copy.getFont().getBold(), prefix);
            assertEquals(original.getFont().getItalic(), copy.getFont().getItalic(), prefix);
        }
    }

    private Paragraph findParagraph(Document document, String prefix) throws Exception {
        NodeCollection paragraphs = document.getChildNodes(NodeType.PARAGRAPH, true);
        for (int index = 0; index < paragraphs.getCount(); index++) {
            Paragraph paragraph = (Paragraph) paragraphs.get(index);
            if (paragraph.getText().trim().startsWith(prefix)) return paragraph;
        }
        throw new AssertionError("Missing paragraph: " + prefix);
    }

    // Independent expected range: top-level source nodes between the two headings, excluding headings.
    private List<Node> range(Document input, String start, String end) throws Exception {
        List<Node> result = new ArrayList<>();
        boolean found = false;
        for (Section section : input.getSections()) {
            for (Node node = section.getBody().getFirstChild(); node != null; node = node.getNextSibling()) {
                if (node instanceof Paragraph && node.getText().trim().equals(start)) { found = true; continue; }
                if (found && node instanceof Paragraph && node.getText().trim().equals(end)) return result;
                if (found) result.add(node);
            }
        }
        assertTrue(found, start);
        assertNull(end, "Missing expected end heading");
        return result;
    }

    private String plain(String value) { return value.replaceAll("[\\p{Cc}\\s\\u00a0]+", " ").trim(); }
    private int occurrences(String value, String text) { return value.split(java.util.regex.Pattern.quote(text), -1).length - 1; }

    private WordGenerationSnapshot snapshot(RptFile file) {
        List<WordGenerationSnapshot.Block> blocks = new ArrayList<>();
        for (boolean program : new boolean[] {true, false}) {
            RptSectionDef section = new RptSectionDef().setSourceDefinitionId(1119L)
                    .setExtractMode(RptExtractMode.TEXT_RANGE).setStartMarker(program ? PROGRAM : REACTION)
                    .setEndMarker(program ? REACTION : null).setIncludeStart(0).setIncludeEnd(0);
            RptOutputSourceMap mapping = new RptOutputSourceMap().setSourceDefinitionId(1119L)
                    .setSourceRole(RptSourceRole.TEXT).setSourceSectionDefId(program ? 8104L : 8105L).setSortOrder(1);
            RptOutputBlockDef block = new RptOutputBlockDef().setBlockCode(program ? "PROGRAM_EVENT" : "CUSTOMER_REACTION")
                    .setTargetName(program ? "BM_PROGRAM_EVENT" : "BM_CUSTOMER_REACTION").setRenderType(RptRenderType.COPY_SECTION);
            blocks.add(new WordGenerationSnapshot.Block(block, Collections.singletonList(
                    new WordGenerationSnapshot.Source(mapping, section, file, WEEK))));
        }
        return new WordGenerationSnapshot(WEEK, TEMPLATE, blocks);
    }

    private Path fixture() throws Exception {
        try (Stream<Path> paths = Files.walk(Paths.get("documents", "File 2"))) {
            return paths.filter(path -> path.getFileName().toString().equals("Nổi bật tuần 01.09 - 07.09.docx"))
                    .findFirst().orElse(null);
        }
    }

    private WeeklyWordOutputGenerator generator(ReportFileService files) {
        return new WeeklyWordOutputGenerator(files, new WordSectionExtractor(), new WordBookmarkRenderer(),
                new WordProductIssueReader(), new WordIncidentChartReader(),
                new WordTrendChartRenderer(), new WordSystemIncidentReader(), new WordMobileFeedbackReader(),
                new WordNetworkQualityReader(), new WordTv360FeedbackReader(), new WordSmeFeedbackReader(), new WordFixedFeedbackReader());
    }
}
