package com.techasians.alertservice.service.impl;

import com.aspose.words.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WordSectionAlignmentTest {
    @Test
    void translatesWholeBlockAndPreservesInternalFormattingAndSource() throws Exception {
        Document source = emptyDocument();
        Paragraph first = bullet(source, "☞", 14.2, -21.3, "Tiêu đề");
        first.getRuns().get(0).getFont().setBold(true);
        Paragraph detail = bullet(source, "-", 0, -7.1, "Hiện tượng");
        Paragraph note = paragraph(source, "(Diễn giải)");
        note.getParagraphFormat().setLeftIndent(3);
        note.getRuns().get(0).getFont().setItalic(true);
        Paragraph child = bullet(source, "•", 36, -18, "Chi tiết con");
        child.getParagraphFormat().getTabStops().add(60, TabAlignment.RIGHT, TabLeader.DOTS);
        List<Node> originals = nodes(source);
        List<Node> copies = copy(originals);
        String content = copies.get(0).getDocument().getText();

        WordSectionAlignment.align(originals, copies, 0);

        double delta = 7.1;
        for (int index = 0; index < originals.size(); index++) {
            Paragraph before = (Paragraph) originals.get(index);
            Paragraph after = (Paragraph) copies.get(index);
            assertEquals(before.getParagraphFormat().getLeftIndent() + delta,
                    after.getParagraphFormat().getLeftIndent(), 0.01);
            assertEquals(before.getParagraphFormat().getFirstLineIndent(), after.getParagraphFormat().getFirstLineIndent(), 0.01);
            assertEquals(before.getParagraphFormat().getRightIndent() - delta, after.getParagraphFormat().getRightIndent(), 0.01);
            if (before.isListItem()) {
                assertEquals(before.getListFormat().getListLevelNumber(), after.getListFormat().getListLevelNumber());
                assertEquals(before.getListFormat().getListLevel().getNumberFormat(), after.getListFormat().getListLevel().getNumberFormat());
            }
        }
        assertEquals(content, copies.get(0).getDocument().getText());
        assertTrue(((Paragraph) copies.get(0)).getRuns().get(0).getFont().getBold());
        assertTrue(((Paragraph) copies.get(2)).getRuns().get(0).getFont().getItalic());
        TabStop tab = ((Paragraph) copies.get(3)).getParagraphFormat().getTabStops().get(67.1);
        assertNotNull(tab);
        assertEquals(67.1, tab.getPosition(), 0.01);
        assertEquals(TabAlignment.RIGHT, tab.getAlignment());
        assertEquals(TabLeader.DOTS, tab.getLeader());
        assertEquals(14.2, first.getParagraphFormat().getLeftIndent(), 0.01);
        assertEquals(0, detail.getParagraphFormat().getLeftIndent(), 0.01);
    }

    @Test
    void movesInheritedMultilevelListsLeftAndPreservesNumbering() throws Exception {
        Document source = new Document();
        DocumentBuilder builder = new DocumentBuilder(source);
        builder.getListFormat().applyNumberDefault();
        builder.writeln("Một");
        builder.getListFormat().setListLevelNumber(1);
        builder.write("Một chấm một");
        List<Node> originals = nodes(source);
        List<Node> copies = copy(originals);
        double sourceStart = leading((Paragraph) originals.get(0));

        WordSectionAlignment.align(originals, copies, -5);

        assertEquals(-5, leading((Paragraph) copies.get(0)), 0.01);
        assertEquals(1, ((Paragraph) copies.get(1)).getListFormat().getListLevelNumber());
        for (int index = 0; index < originals.size(); index++) {
            Paragraph before = (Paragraph) originals.get(index);
            Paragraph after = (Paragraph) copies.get(index);
            assertEquals(leading(before) - sourceStart - 5, leading(after), 0.01);
            assertEquals(before.getListFormat().getListLevel().getNumberStyle(), after.getListFormat().getListLevel().getNumberStyle());
            assertEquals(before.getListFormat().getListLevel().getTabPosition() - sourceStart - 5,
                    after.getListFormat().getListLevel().getTabPosition(), 0.01);
        }
    }

    @Test
    void recognizesTypedSymbolWithoutRewritingTextAndSkipsPlainText() throws Exception {
        Document source = emptyDocument();
        paragraph(source, "");
        Paragraph manual = paragraph(source, "❖\u00a0Nội dung gõ trực tiếp");
        manual.getParagraphFormat().setLeftIndent(-7);
        List<Node> originals = nodes(source);
        List<Node> copies = copy(originals);
        WordSectionAlignment.align(originals, copies, 0);
        assertEquals(0, leading((Paragraph) copies.get(1)), 0.01);
        assertEquals(manual.getText(), copies.get(1).getText());
        manual.getRuns().get(0).setText("Nội dung thường");
        copies = copy(originals);
        WordSectionAlignment.align(originals, copies, 0);
        assertEquals(-7, leading((Paragraph) copies.get(1)), 0.01);
    }

    @Test
    void translatesTableAndFloatingShapeWithoutMovingCellContentTwice() throws Exception {
        Document source = emptyDocument();
        Paragraph first = bullet(source, "•", 10, -20, "Mục nguồn");
        Shape floating = new Shape(source, ShapeType.RECTANGLE);
        floating.setWrapType(WrapType.NONE);
        floating.setRelativeHorizontalPosition(RelativeHorizontalPosition.MARGIN);
        floating.setHorizontalAlignment(HorizontalAlignment.NONE);
        floating.setLeft(15);
        floating.setWidth(30);
        floating.setHeight(20);
        first.appendChild(floating);
        Table table = new Table(source);
        table.ensureMinimum();
        table.setLeftIndent(12);
        Paragraph cellParagraph = table.getFirstRow().getFirstCell().getFirstParagraph();
        cellParagraph.getParagraphFormat().setLeftIndent(8);
        cellParagraph.appendChild(new Run(source, "Trong ô"));
        source.getFirstSection().getBody().appendChild(table);
        List<Node> originals = nodes(source);
        List<Node> copies = copy(originals);

        WordSectionAlignment.align(originals, copies, 0);

        Table after = (Table) copies.get(1);
        assertEquals(22, after.getLeftIndent(), 0.01);
        assertEquals(8, after.getFirstRow().getFirstCell().getFirstParagraph().getParagraphFormat().getLeftIndent(), 0.01);
        Shape shifted = (Shape) ((Paragraph) copies.get(0)).getLastChild();
        assertEquals(25, shifted.getLeft(), 0.01);
        assertEquals(30, shifted.getWidth(), 0.01);
        assertEquals(15, floating.getLeft(), 0.01);
    }

    @Test
    void translatesCenteredTableAndAlignedShapesUsingTheirReferenceFrames() throws Exception {
        Document source = emptyDocument();
        PageSetup page = source.getFirstSection().getPageSetup();
        page.setPageWidth(600);
        page.setLeftMargin(50);
        page.setRightMargin(50);
        Paragraph first = bullet(source, "•", 10, -20, "Mục nguồn");
        Shape centered = new Shape(source, ShapeType.RECTANGLE);
        centered.setWrapType(WrapType.NONE);
        centered.setRelativeHorizontalPosition(RelativeHorizontalPosition.PAGE);
        centered.setHorizontalAlignment(HorizontalAlignment.CENTER);
        centered.setWidth(40);
        centered.setHeight(20);
        first.appendChild(centered);
        Shape margin = (Shape) centered.deepClone(true);
        margin.setRelativeHorizontalPosition(RelativeHorizontalPosition.RIGHT_MARGIN);
        first.appendChild(margin);
        Table table = new Table(source);
        table.ensureMinimum();
        table.setAllowAutoFit(false);
        table.getFirstRow().getFirstCell().getCellFormat().setWidth(100);
        table.setAlignment(TableAlignment.CENTER);
        source.getFirstSection().getBody().appendChild(table);
        List<Node> originals = nodes(source);
        List<Node> copies = copy(originals);

        WordSectionAlignment.align(originals, copies, 0);

        assertEquals(210, ((Table) copies.get(1)).getLeftIndent(), 0.01);
        Paragraph after = (Paragraph) copies.get(0);
        assertEquals(290, ((Shape) after.getChildNodes(NodeType.SHAPE, false).get(0)).getLeft(), 0.01);
        assertEquals(15, ((Shape) after.getChildNodes(NodeType.SHAPE, false).get(1)).getLeft(), 0.01);
    }

    @Test
    void selectsHeadingAnchorForStandaloneBookmarkAndOwnAnchorForInlineBookmark() throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.getListFormat().applyNumberDefault();
        builder.getParagraphFormat().setLeftIndent(18);
        builder.getParagraphFormat().setFirstLineIndent(-18);
        builder.writeln("Đầu mục");
        builder.getListFormat().removeNumbers();
        builder.getParagraphFormat().setLeftIndent(21.3);
        builder.getParagraphFormat().setFirstLineIndent(0);
        builder.startBookmark("CONTENT");
        builder.write("[NỘI DUNG]");
        builder.endBookmark("CONTENT");
        Bookmark bookmark = document.getRange().getBookmarks().get("CONTENT");
        assertEquals(0, WordSectionAlignment.targetPosition(bookmark), 0.01);
        builder.write(" Nhãn nằm ngoài bookmark");
        assertEquals(21.3, WordSectionAlignment.targetPosition(bookmark), 0.01);
        builder.getListFormat().applyBulletDefault();
        builder.getParagraphFormat().setLeftIndent(30);
        builder.getParagraphFormat().setFirstLineIndent(-10);
        assertEquals(20, WordSectionAlignment.targetPosition(bookmark), 0.01);
    }

    private Document emptyDocument() throws Exception {
        Document document = new Document();
        document.getFirstSection().getBody().removeAllChildren();
        return document;
    }

    private Paragraph paragraph(Document document, String text) {
        Paragraph paragraph = new Paragraph(document);
        paragraph.appendChild(new Run(document, text));
        document.getFirstSection().getBody().appendChild(paragraph);
        return paragraph;
    }

    private Paragraph bullet(Document document, String glyph, double left, double firstLine, String text) {
        Paragraph paragraph = paragraph(document, text);
        com.aspose.words.List list = document.getLists().add(ListTemplate.BULLET_DEFAULT);
        list.getListLevels().get(0).setNumberFormat(glyph);
        paragraph.getListFormat().setList(list);
        paragraph.getParagraphFormat().setLeftIndent(left);
        paragraph.getParagraphFormat().setFirstLineIndent(firstLine);
        return paragraph;
    }

    private List<Node> nodes(Document document) {
        List<Node> nodes = new ArrayList<>();
        for (Node node = document.getFirstSection().getBody().getFirstChild(); node != null; node = node.getNextSibling()) nodes.add(node);
        return nodes;
    }

    private List<Node> copy(List<Node> nodes) throws Exception {
        Document fragment = emptyDocument();
        NodeImporter importer = new NodeImporter(nodes.get(0).getDocument(), fragment, ImportFormatMode.KEEP_SOURCE_FORMATTING);
        List<Node> copies = new ArrayList<>();
        for (Node node : nodes) {
            Node copy = importer.importNode(node, true);
            fragment.getFirstSection().getBody().appendChild(copy);
            copies.add(copy);
        }
        return copies;
    }

    private double leading(Paragraph paragraph) {
        return paragraph.getParagraphFormat().getLeftIndent() + paragraph.getParagraphFormat().getFirstLineIndent();
    }
}
