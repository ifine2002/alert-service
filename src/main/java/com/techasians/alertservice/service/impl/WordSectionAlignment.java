package com.techasians.alertservice.service.impl;

import com.aspose.words.*;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Dịch nguyên khối theo bullet đầu tiên, không sửa phân cấp hay nội dung nguồn. */
final class WordSectionAlignment {
    private WordSectionAlignment() { }

    static double targetPosition(Bookmark bookmark) throws Exception {
        Paragraph placeholder = (Paragraph) bookmark.getBookmarkStart().getParentNode();
        // Bookmark nằm trong đầu mục thì dùng chính đầu mục đó. Với bookmark ở
        // paragraph riêng, dùng đầu mục ngay trước nó nếu có.
        boolean standalone = placeholder.getText().trim().equals(bookmark.getText().trim());
        if (standalone && !placeholder.isListItem()) {
            Node previous = placeholder.getPreviousSibling();
            while (previous instanceof Paragraph && previous.getText().trim().isEmpty()) {
                previous = previous.getPreviousSibling();
            }
            if (previous instanceof Paragraph) {
                Paragraph heading = (Paragraph) previous;
                if (heading.isListItem() || heading.getParagraphFormat().isHeading()) {
                    return leadingPosition(heading);
                }
            }
        }
        return leadingPosition(placeholder);
    }

    static void align(List<Node> source, List<Node> copies, double targetPosition) throws Exception {
        Paragraph first = firstContentParagraph(copies);
        if (first == null || !startsWithBullet(first)) return;
        double delta = targetPosition - leadingPosition(first);
        if (Math.abs(delta) < 0.01) return;

        Map<com.aspose.words.List, com.aspose.words.List> lists = new IdentityHashMap<>();
        for (int index = 0; index < copies.size(); index++) {
            Node node = copies.get(index);
            PageSetup page = ((Section) source.get(index).getAncestor(NodeType.SECTION)).getPageSetup();
            if (node instanceof Paragraph) {
                shiftParagraph((Paragraph) node, delta, lists, page);
            } else if (node instanceof Table) {
                shiftTable((Table) node, delta, page);
            }
        }
    }

    private static Paragraph firstContentParagraph(List<Node> nodes) {
        for (Node node : nodes) {
            if (node instanceof Paragraph && !node.getText().trim().isEmpty()) return (Paragraph) node;
            // Không lấy bullet trong ô làm mốc dịch cả section.
            if (node instanceof Table) return null;
        }
        return null;
    }

    private static boolean startsWithBullet(Paragraph paragraph) {
        if (paragraph.isListItem()) return true;
        String text = paragraph.getText().trim();
        int symbol = text.codePointAt(0);
        int next = Character.charCount(symbol);
        if (next >= text.length()) return false;
        int separator = text.codePointAt(next);
        if (!Character.isWhitespace(separator) && !Character.isSpaceChar(separator)) return false;
        int type = Character.getType(symbol);
        return type == Character.OTHER_SYMBOL || type == Character.MATH_SYMBOL
                || type == Character.DASH_PUNCTUATION || type == Character.OTHER_PUNCTUATION
                || type == Character.PRIVATE_USE;
    }

    private static double leadingPosition(Paragraph paragraph) {
        ParagraphFormat format = paragraph.getParagraphFormat();
        // Aspose trả về lề hiệu lực, bao gồm cả lề kế thừa từ list/style.
        return format.getLeftIndent() + format.getFirstLineIndent();
    }

    private static void shiftParagraph(Paragraph paragraph, double delta,
            Map<com.aspose.words.List, com.aspose.words.List> lists, PageSetup page) throws Exception {
        ParagraphFormat format = paragraph.getParagraphFormat();
        double left = format.getLeftIndent();
        double firstLine = format.getFirstLineIndent();
        double right = format.getRightIndent();
        TabStop[] tabs = paragraph.getEffectiveTabStops();
        if (paragraph.isListItem()) {
            ListFormat listFormat = paragraph.getListFormat();
            com.aspose.words.List original = listFormat.getList();
            com.aspose.words.List shifted = lists.get(original);
            if (shifted == null) {
                shifted = ((Document) paragraph.getDocument()).getLists().addCopy(original);
                for (ListLevel level : shifted.getListLevels()) {
                    level.setNumberPosition(level.getNumberPosition() + delta);
                    level.setTextPosition(level.getTextPosition() + delta);
                    // 0 là tab mặc định của list, không phải tab stop tường minh.
                    if (level.getTabPosition() != 0) level.setTabPosition(level.getTabPosition() + delta);
                }
                lists.put(original, shifted);
            }
            listFormat.setList(shifted);
        }
        format.setLeftIndent(left + delta);
        format.setFirstLineIndent(firstLine);
        // Dịch cả mép phải để giữ độ rộng và khoảng cách tương đối trong khối.
        format.setRightIndent(right - delta);
        format.getTabStops().clear();
        for (TabStop tab : tabs) {
            format.getTabStops().add(tab.getPosition() + delta, tab.getAlignment(), tab.getLeader());
        }
        // Ảnh inline đi theo paragraph; chỉ dịch neo của ảnh/hình nổi cấp ngoài.
        for (Node child = paragraph.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof ShapeBase) shiftShape((ShapeBase) child, delta, page);
        }
    }

    private static void shiftTable(Table table, double delta, PageSetup page) throws Exception {
        if (table.getTextWrapping() == TextWrapping.AROUND) {
            table.setAbsoluteHorizontalDistance(table.getAbsoluteHorizontalDistance() + delta);
            return;
        }
        double left = table.getLeftIndent();
        if (table.getAlignment() != TableAlignment.LEFT) {
            double width = 0;
            for (Cell cell : table.getFirstRow().getCells()) width += cell.getCellFormat().getWidth();
            double available = page.getPageWidth() - page.getLeftMargin() - page.getRightMargin();
            left = table.getAlignment() == TableAlignment.CENTER ? (available - width) / 2 : available - width;
            table.setAlignment(TableAlignment.LEFT);
        }
        table.setLeftIndent(left + delta);
        // Không dịch paragraph trong ô: chúng đã đi theo bảng.
    }

    private static void shiftShape(ShapeBase shape, double delta, PageSetup page) {
        if (shape.isInline() || shape.getRelativeHorizontalPosition() == RelativeHorizontalPosition.CHARACTER) return;
        double left = shape.getLeft();
        if (shape.getHorizontalAlignment() != HorizontalAlignment.NONE) {
            double available = horizontalWidth(shape.getRelativeHorizontalPosition(), page);
            if (shape.getHorizontalAlignment() == HorizontalAlignment.LEFT) left = 0;
            else if (shape.getHorizontalAlignment() == HorizontalAlignment.CENTER) left = (available - shape.getWidth()) / 2;
            else if (shape.getHorizontalAlignment() == HorizontalAlignment.RIGHT) left = available - shape.getWidth();
            else return;
            shape.setHorizontalAlignment(HorizontalAlignment.NONE);
        }
        shape.setLeft(left + delta);
    }

    private static double horizontalWidth(int relativeTo, PageSetup page) {
        if (relativeTo == RelativeHorizontalPosition.PAGE) return page.getPageWidth();
        if (relativeTo == RelativeHorizontalPosition.LEFT_MARGIN) return page.getLeftMargin();
        if (relativeTo == RelativeHorizontalPosition.RIGHT_MARGIN) return page.getRightMargin();
        return page.getPageWidth() - page.getLeftMargin() - page.getRightMargin();
    }
}
