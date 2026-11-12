package com.techasians.alertservice.service.impl;

import com.aspose.words.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class WordBookmarkRendererTest {
    @ParameterizedTest
    @ValueSource(strings = {"BM_STATION_INCIDENT_CHART", "BM_CABLE_INCIDENT_CHART"})
    void chartStaysCenteredInsidePageMarginsAfterSaveAndReload(String name) throws Exception {
        Document document;
        try (InputStream template = getClass().getResourceAsStream("/templates/weekly-vcx-word-template.docx")) {
            document = new Document(template);
        }
        Double[][] values = new Double[8][3];
        Arrays.setAll(values, index -> new Double[] {318.0, 5301.0, 143987.0});
        WordTrendChartRenderer charts = new WordTrendChartRenderer();
        byte[] image = name.equals("BM_CABLE_INCIDENT_CHART")
                ? charts.renderCable(LocalDate.of(2026, 9, 1), values)
                : charts.renderStation(LocalDate.of(2026, 9, 1), values);

        new WordBookmarkRenderer().insertImage(document, name, image);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        document.save(output, SaveFormat.DOCX);
        Document saved = new Document(new ByteArrayInputStream(output.toByteArray()));
        Paragraph paragraph = (Paragraph) saved.getRange().getBookmarks().get(name)
                .getBookmarkStart().getParentNode();
        Shape shape = (Shape) paragraph.getChildNodes(NodeType.SHAPE, true).get(0);
        PageSetup page = paragraph.getParentSection().getPageSetup();
        LayoutCollector collector = new LayoutCollector(saved);
        saved.updatePageLayout();
        LayoutEnumerator layout = new LayoutEnumerator(saved);
        layout.setCurrent(collector.getEntity(shape));
        Rectangle2D bounds = layout.getRectangle();

        assertTrue(bounds.getMinX() >= page.getLeftMargin() - 0.1, name);
        assertTrue(bounds.getMaxX() <= page.getPageWidth() - page.getRightMargin() + 0.1, name);
        assertEquals((page.getLeftMargin() + page.getPageWidth() - page.getRightMargin()) / 2,
                bounds.getCenterX(), 0.1, name);
        assertEquals(1700.0 / 600, shape.getWidth() / shape.getHeight(), 0.001);
        assertArrayEquals(image, shape.getImageData().getImageBytes());
    }

    @Test
    void inlineRunsKeepSourceColorsWithGrayTemplatePlaceholderAfterSaveAndReload() throws Exception {
        Document source = new Document();
        DocumentBuilder sourceBuilder = new DocumentBuilder(source);
        sourceBuilder.getFont().setName("Arial");
        sourceBuilder.getFont().setSize(22);
        sourceBuilder.write("Màu mặc định. ");
        sourceBuilder.getFont().setColor(Color.RED);
        sourceBuilder.getFont().setBold(true);
        sourceBuilder.write("Màu đỏ. ");
        sourceBuilder.getFont().setColor(Color.BLUE);
        sourceBuilder.getFont().setBold(false);
        sourceBuilder.getFont().setItalic(true);
        sourceBuilder.write("Màu xanh.");
        Paragraph content = source.getFirstSection().getBody().getFirstParagraph();

        Document target = new Document();
        DocumentBuilder builder = new DocumentBuilder(target);
        builder.getFont().setName("Times New Roman");
        builder.getFont().setSize(14);
        builder.write("Phản ánh DV SME: ");
        builder.startBookmark("CONTENT");
        builder.getFont().setColor(new Color(0x777777));
        builder.write("[PLACEHOLDER]");
        builder.endBookmark("CONTENT");
        builder.getFont().setColor(Color.BLACK);
        builder.write(" Kết thúc.");

        new WordBookmarkRenderer().replaceInlineRuns(target, "CONTENT", content);
        Path output = Files.createTempFile("word-inline-colors-", ".docx");
        try {
            target.save(output.toString());
            Document saved = new Document(output.toString());
            Bookmark bookmark = saved.getRange().getBookmarks().get("CONTENT");
            Paragraph paragraph = (Paragraph) bookmark.getBookmarkStart().getParentNode();
            assertSame(paragraph, bookmark.getBookmarkEnd().getParentNode());
            assertTrue(paragraph.getText().startsWith("Phản ánh DV SME: Màu mặc định. Màu đỏ. Màu xanh. Kết thúc."));
            for (Run original : content.getRuns()) {
                Run actual = WordSmeFeedbackReaderTest.findRun(paragraph, original.getText());
                assertEquals(original.getFont().getColor(), actual.getFont().getColor(), original.getText());
                assertEquals(original.getFont().getBold(), actual.getFont().getBold());
                assertEquals(original.getFont().getItalic(), actual.getFont().getItalic());
                assertEquals("Times New Roman", actual.getFont().getName());
                assertEquals(14, actual.getFont().getSize());
            }
        } finally { Files.deleteIfExists(output); }
    }

    @Test
    void replacingParentAcrossParagraphsRemovesOrphanChildrenAtSharedBoundaries() throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.write("Tiêu đề: ");
        builder.startBookmark("PARENT");
        builder.startBookmark("FIRST_CHILD");
        builder.writeln("Nội dung đầu");
        builder.endBookmark("FIRST_CHILD");
        builder.startBookmark("LAST_CHILD");
        builder.write("Nội dung cuối");
        builder.endBookmark("LAST_CHILD");
        builder.endBookmark("PARENT");
        builder.writeln();
        builder.startBookmark("NEXT");
        builder.write("Phần tiếp theo");
        builder.endBookmark("NEXT");

        new WordBookmarkRenderer().replaceText(document, "PARENT", "Chưa có dữ liệu");
        for (Bookmark bookmark : document.getRange().getBookmarks()) assertDoesNotThrow(bookmark::getText);
        assertNull(document.getRange().getBookmarks().get("FIRST_CHILD"));
        assertNull(document.getRange().getBookmarks().get("LAST_CHILD"));
        assertEquals(document.getChildNodes(NodeType.BOOKMARK_START, true).getCount(),
                document.getChildNodes(NodeType.BOOKMARK_END, true).getCount());
        Path path = Files.createTempFile("word-parent-fallback-", ".docx");
        try {
            document.save(path.toString());
            Document output = new Document(path.toString());
            assertTrue(output.getText().contains("Tiêu đề: Chưa có dữ liệu"));
            assertEquals("Chưa có dữ liệu", output.getRange().getBookmarks().get("PARENT").getText());
            assertEquals("Phần tiếp theo", output.getRange().getBookmarks().get("NEXT").getText());
        } finally { Files.deleteIfExists(path); }
    }

    @Test
    void removesBoundaryBlanksAndOrphanBookmarkWithoutRemovingInternalBlank() throws Exception {
        Document source = new Document();
        Body body = source.getFirstSection().getBody();
        body.removeAllChildren();
        Paragraph excluded = paragraph(source, "Đầu mục ngoài vùng cắt");
        excluded.prependChild(new BookmarkStart(source, "OUTSIDE"));
        Paragraph first = paragraph(source, "Nội dung đầu");
        Paragraph internalBlank = paragraph(source, "");
        Paragraph last = paragraph(source, "Nội dung cuối");
        Paragraph trailingBlank = paragraph(source, "   ");
        BookmarkEnd orphan = new BookmarkEnd(source, "OUTSIDE");
        body.appendChild(orphan);
        Document target = target();

        new WordBookmarkRenderer().insertNodes(target, "CONTENT",
                Arrays.asList(first, internalBlank, last, trailingBlank, orphan));
        Path path = Files.createTempFile("word-boundary-", ".docx");
        try {
            target.save(path.toString());
            Document output = new Document(path.toString());
            Paragraph next = find(output, "Đầu mục tiếp theo");
            assertEquals("Nội dung cuối", next.getPreviousSibling().getText().trim());
            Paragraph end = (Paragraph) next.getPreviousSibling();
            assertEquals("", end.getPreviousSibling().getText().trim());
            assertEquals("Nội dung đầu", end.getPreviousSibling().getPreviousSibling().getText().trim());
            Bookmark bookmark = output.getRange().getBookmarks().get("CONTENT");
            assertNotNull(bookmark);
            assertEquals(end, bookmark.getBookmarkEnd().getParentNode());
            assertTrue(bookmark.getText().contains("Nội dung đầu"));
            assertTrue(bookmark.getText().contains("Nội dung cuối"));
            assertNull(output.getRange().getBookmarks().get("OUTSIDE"));
            assertEquals(body, orphan.getParentNode());
            assertEquals(body, trailingBlank.getParentNode());
        } finally { Files.deleteIfExists(path); }
    }

    @Test
    void preservesCompleteSourceBookmarkAtBlockBoundary() throws Exception {
        Document source = new Document();
        Body body = source.getFirstSection().getBody();
        body.removeAllChildren();
        BookmarkStart start = new BookmarkStart(source, "SOURCE_RANGE");
        body.appendChild(start);
        Paragraph content = paragraph(source, "Nội dung có bookmark");
        BookmarkEnd end = new BookmarkEnd(source, "SOURCE_RANGE");
        body.appendChild(end);
        Document target = target();

        new WordBookmarkRenderer().insertNodes(target, "CONTENT", Arrays.asList(start, content, end));

        Bookmark kept = target.getRange().getBookmarks().get("SOURCE_RANGE");
        assertNotNull(kept);
        assertTrue(kept.getText().contains("Nội dung có bookmark"));
        assertEquals("Nội dung có bookmark", find(target, "Đầu mục tiếp theo").getPreviousSibling().getText().trim());
    }

    @Test
    void preservesImageFieldBreakAndEmptyListAtEndOfSource() throws Exception {
        for (String kind : Arrays.asList("shape", "field", "break", "list")) {
            Document source = new Document();
            DocumentBuilder builder = new DocumentBuilder(source);
            builder.writeln("Nội dung");
            Paragraph last = builder.getCurrentParagraph();
            if (kind.equals("shape")) {
                Shape shape = new Shape(source, ShapeType.RECTANGLE);
                shape.setWidth(30);
                shape.setHeight(20);
                shape.setWrapType(WrapType.INLINE);
                last.appendChild(shape);
            } else if (kind.equals("field")) {
                builder.insertField("PAGE");
            } else if (kind.equals("break")) {
                builder.insertBreak(BreakType.PAGE_BREAK);
            } else {
                builder.getListFormat().applyBulletDefault();
            }
            List<Node> nodes = new ArrayList<>();
            for (Node node = source.getFirstSection().getBody().getFirstChild(); node != null; node = node.getNextSibling()) nodes.add(node);
            Document target = target();

            new WordBookmarkRenderer().insertNodes(target, "CONTENT", nodes);

            Paragraph tail = (Paragraph) find(target, "Đầu mục tiếp theo").getPreviousSibling();
            if (kind.equals("shape")) assertEquals(1, tail.getChildNodes(NodeType.SHAPE, true).getCount());
            else if (kind.equals("field")) assertEquals(1, tail.getChildNodes(NodeType.FIELD_START, true).getCount());
            else if (kind.equals("break")) assertTrue(tail.getText().contains("\f"));
            else assertTrue(tail.isListItem());
        }
    }

    @Test
    void retainsInlineLabelsAndSuffixWhenInsertingParagraphs() throws Exception {
        Document source = new Document();
        new DocumentBuilder(source).write("Nội dung nguồn");
        Document target = new Document();
        DocumentBuilder builder = new DocumentBuilder(target);
        builder.write("Nhãn: ");
        builder.startBookmark("CONTENT");
        builder.write("[PLACEHOLDER]");
        builder.endBookmark("CONTENT");
        builder.write(" Kết thúc.");

        new WordBookmarkRenderer().insertNodes(target, "CONTENT",
                Arrays.asList(source.getFirstSection().getBody().getFirstParagraph()));

        assertTrue(target.getText().contains("Nhãn: "));
        assertTrue(target.getText().contains("Nội dung nguồn"));
        assertTrue(target.getText().contains(" Kết thúc."));
        assertNotNull(target.getRange().getBookmarks().get("CONTENT"));
    }

    @Test
    void keepsOnlyMinimalStructuralParagraphAfterTable() throws Exception {
        Document source = new Document();
        DocumentBuilder builder = new DocumentBuilder(source);
        builder.startTable();
        builder.insertCell();
        builder.write("Bảng nguồn");
        builder.endRow();
        Table table = builder.endTable();
        Document target = target();

        new WordBookmarkRenderer().insertNodes(target, "CONTENT", Arrays.asList(table));

        Paragraph tail = (Paragraph) find(target, "Đầu mục tiếp theo").getPreviousSibling();
        assertEquals("", tail.getText().trim());
        assertTrue(tail.getPreviousSibling() instanceof Table);
        assertEquals(1, tail.getParagraphFormat().getLineSpacing(), 0.01);
        assertEquals(0, tail.getParagraphFormat().getSpaceAfter(), 0.01);
        assertNotNull(target.getRange().getBookmarks().get("CONTENT"));
    }

    private Document target() throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.writeln("Đầu mục trước");
        builder.startBookmark("CONTENT");
        builder.write("[PLACEHOLDER]");
        builder.endBookmark("CONTENT");
        builder.writeln();
        builder.write("Đầu mục tiếp theo");
        return document;
    }

    private Paragraph paragraph(Document document, String text) {
        Paragraph paragraph = new Paragraph(document);
        paragraph.appendChild(new Run(document, text));
        document.getFirstSection().getBody().appendChild(paragraph);
        return paragraph;
    }

    private Paragraph find(Document document, String text) {
        for (Node node = document.getFirstSection().getBody().getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Paragraph && node.getText().trim().equals(text)) return (Paragraph) node;
        }
        fail("Không tìm thấy paragraph: " + text);
        return null;
    }

    @Test
    void replacesInlineContentWithoutRemovingLabel() throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.write("Phản ánh dịch vụ: ");
        builder.startBookmark("CONTENT");
        builder.write("[PLACEHOLDER]");
        builder.endBookmark("CONTENT");
        builder.write(" Kết thúc.");
        new WordBookmarkRenderer().replaceText(document, "CONTENT", "Nội dung nguồn");
        assertTrue(document.getText().contains("Phản ánh dịch vụ: Nội dung nguồn Kết thúc."));
        assertFalse(document.getText().contains("PLACEHOLDER"));
    }

    @Test
    void insertsSourceParagraphAndTable() throws Exception {
        Document source = new Document();
        DocumentBuilder sourceBuilder = new DocumentBuilder(source);
        sourceBuilder.writeln("Nhận xét nguồn");
        sourceBuilder.startTable();
        sourceBuilder.insertCell();
        sourceBuilder.write("Bảng nguồn");
        sourceBuilder.endRow();
        sourceBuilder.endTable();
        Document target = new Document();
        DocumentBuilder builder = new DocumentBuilder(target);
        builder.startBookmark("CONTENT");
        builder.write("[PLACEHOLDER]");
        builder.endBookmark("CONTENT");
        new WordBookmarkRenderer().insertNodes(target, "CONTENT", Arrays.asList(
                source.getFirstSection().getBody().getFirstParagraph(),
                source.getFirstSection().getBody().getTables().get(0)));
        assertTrue(target.getText().contains("Nhận xét nguồn"));
        assertTrue(target.getText().contains("Bảng nguồn"));
        assertFalse(target.getText().contains("PLACEHOLDER"));
    }

    @Test
    void missingTargetIsConfigurationError() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> new WordBookmarkRenderer().replaceText(new Document(), "MISSING", "text"));
    }
}
