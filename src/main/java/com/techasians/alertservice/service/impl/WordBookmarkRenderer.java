package com.techasians.alertservice.service.impl;

import com.aspose.words.*;
import org.springframework.stereotype.Component;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Chèn nội dung vào bookmark mà không thay đổi phần câu nằm ngoài bookmark. */
@Component
public class WordBookmarkRenderer {
    public void replaceText(Document document, String name, String text) throws Exception {
        Bookmark bookmark = requireBookmark(document, name);
        bookmark.setText(text);
        // Aspose có thể giữ marker con ở cùng vị trí đầu/cuối khi thay một vùng nhiều paragraph.
        removeIncompleteBookmarks(document);
    }

    /** Giữ paragraph/font của mẫu; lấy chữ, màu và định dạng nhấn mạnh từ nội dung nguồn. */
    public void replaceInlineRuns(Document document, String name, Paragraph content) throws Exception {
        Bookmark bookmark = requireBookmark(document, name);
        Node parent = bookmark.getBookmarkStart().getParentNode();
        if (!(parent instanceof Paragraph) || parent != bookmark.getBookmarkEnd().getParentNode()) {
            throw new IllegalArgumentException("Bookmark phải nằm trong một paragraph: " + name);
        }
        Paragraph paragraph = (Paragraph) parent;
        Run format = null;
        for (Node node = bookmark.getBookmarkStart().getNextSibling(); node != bookmark.getBookmarkEnd();
             node = node.getNextSibling()) {
            if (node instanceof Run) { format = (Run) node.deepClone(true); break; }
        }
        if (format == null) throw new IllegalArgumentException("Bookmark thiếu run định dạng mẫu: " + name);
        bookmark.setText("");
        for (Run run : content.getRuns()) {
            Run copy = (Run) format.deepClone(true);
            copy.setText(run.getText());
            copy.getFont().setColor(run.getFont().getColor());
            copy.getFont().setBold(run.getFont().getBold());
            copy.getFont().setItalic(run.getFont().getItalic());
            copy.getFont().setUnderline(run.getFont().getUnderline());
            paragraph.insertBefore(copy, bookmark.getBookmarkEnd());
        }
        removeIncompleteBookmarks(document);
    }

    public void insertNodes(Document document, String name, List<Node> nodes) throws Exception {
        Bookmark bookmark = requireBookmark(document, name);
        Document fragment = new Document();
        Body body = fragment.getFirstSection().getBody();
        body.removeAllChildren();
        if (!nodes.isEmpty()) {
            NodeImporter importer = new NodeImporter(nodes.get(0).getDocument(), fragment,
                    ImportFormatMode.KEEP_SOURCE_FORMATTING);
            for (Node node : nodes) body.appendChild(importer.importNode(node, true));
        }
        removeIncompleteBookmarks(fragment);
        while (body.getLastChild() instanceof Paragraph
                && isEmptyParagraph((Paragraph) body.getLastChild(), false)) {
            body.getLastChild().remove();
        }
        bookmark.setText("");
        if (body.hasChildNodes()) {
            // Bỏ qua marker khi kiểm tra block cuối; chúng không tạo ra một dòng.
            Node lastBlock = body.getLastChild();
            while (isBookmarkMarker(lastBlock)) lastBlock = lastBlock.getPreviousSibling();
            Paragraph ending;
            if (lastBlock instanceof Paragraph) ending = (Paragraph) lastBlock;
            else {
                ending = new Paragraph(fragment);
                minimizeStructuralParagraph(ending);
                body.insertAfter(ending, lastBlock);
            }
            while (ending.getNextSibling() != null) ending.appendChild(ending.getNextSibling());
            DocumentBuilder builder = new DocumentBuilder(document);
            builder.moveToBookmark(name, true, true);
            builder.insertDocument(fragment, ImportFormatMode.KEEP_SOURCE_FORMATTING);
        }
        removeEmptyBookmarkTail(bookmark);
    }

    private void removeIncompleteBookmarks(Document fragment) {
        Set<String> starts = new HashSet<>();
        Set<String> ends = new HashSet<>();
        NodeCollection startNodes = fragment.getChildNodes(NodeType.BOOKMARK_START, true);
        NodeCollection endNodes = fragment.getChildNodes(NodeType.BOOKMARK_END, true);
        for (int index = 0; index < startNodes.getCount(); index++) {
            starts.add(((BookmarkStart) startNodes.get(index)).getName());
        }
        for (int index = 0; index < endNodes.getCount(); index++) {
            ends.add(((BookmarkEnd) endNodes.get(index)).getName());
        }
        // Dọn marker không có cặp trong fragment trích xuất hoặc document output đang xử lý.
        for (int index = startNodes.getCount() - 1; index >= 0; index--) {
            BookmarkStart start = (BookmarkStart) startNodes.get(index);
            if (!ends.contains(start.getName())) start.remove();
        }
        for (int index = endNodes.getCount() - 1; index >= 0; index--) {
            BookmarkEnd end = (BookmarkEnd) endNodes.get(index);
            if (!starts.contains(end.getName())) end.remove();
        }
    }

    private void removeEmptyBookmarkTail(Bookmark bookmark) throws Exception {
        Node parent = bookmark.getBookmarkEnd().getParentNode();
        if (!(parent instanceof Paragraph)) return;
        Paragraph tail = (Paragraph) parent;
        if (!isEmptyParagraph(tail, true)) return;
        Node previous = tail.getPreviousSibling();
        if (previous instanceof Paragraph) {
            // Giữ các marker trong paragraph nội dung cuối, không xóa bookmark đích.
            for (Node node = tail.getFirstChild(); node != null;) {
                Node next = node.getNextSibling();
                if (isBookmarkMarker(node)) ((Paragraph) previous).appendChild(node);
                node = next;
            }
            tail.remove();
        } else if (previous instanceof Table) {
            // Word cần paragraph sau bảng; chỉ giữ paragraph cấu trúc cao 1pt.
            minimizeStructuralParagraph(tail);
        }
    }

    private boolean isEmptyParagraph(Paragraph paragraph, boolean allowBookmarks) throws Exception {
        if ((!allowBookmarks && paragraph.isListItem()) || paragraph.getParagraphFormat().getPageBreakBefore()) return false;
        for (Node child = paragraph.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (allowBookmarks && isBookmarkMarker(child)) continue;
            if (!(child instanceof Run)) return false;
            String text = ((Run) child).getText();
            // Không coi page break/line break/column break là khoảng trắng để xóa.
            if (!text.codePoints().allMatch(value -> value == ' ' || value == '\t'
                    || Character.getType(value) == Character.SPACE_SEPARATOR)) return false;
        }
        return true;
    }

    private boolean isBookmarkMarker(Node node) {
        return node instanceof BookmarkStart || node instanceof BookmarkEnd;
    }

    private void minimizeStructuralParagraph(Paragraph paragraph) throws Exception {
        paragraph.getListFormat().removeNumbers();
        ParagraphFormat format = paragraph.getParagraphFormat();
        format.setSpaceBeforeAuto(false);
        format.setSpaceAfterAuto(false);
        format.setSpaceBefore(0);
        format.setSpaceAfter(0);
        format.setLineSpacingRule(LineSpacingRule.EXACTLY);
        format.setLineSpacing(1);
        paragraph.getParagraphBreakFont().setSize(1);
    }

    public void insertImage(Document document, String name, byte[] image) throws Exception {
        Bookmark bookmark = requireBookmark(document, name);
        bookmark.setText("");
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.moveToBookmark(name, true, true);
        // Chart bookmarks use their own paragraph; list indents would shift a page-width image past the margin.
        builder.getListFormat().removeNumbers();
        ParagraphFormat format = builder.getParagraphFormat();
        format.setLeftIndent(0);
        format.setRightIndent(0);
        format.setFirstLineIndent(0);
        format.setAlignment(ParagraphAlignment.CENTER);
        Shape shape = builder.insertImage(image);
        PageSetup page = builder.getPageSetup();
        double available = page.getPageWidth() - page.getLeftMargin() - page.getRightMargin();
        if (shape.getWidth() > available) {
            double ratio = available / shape.getWidth();
            shape.setWidth(available);
            shape.setHeight(shape.getHeight() * ratio);
        }
    }

    private Bookmark requireBookmark(Document document, String name) throws Exception {
        Bookmark bookmark = document.getRange().getBookmarks().get(name);
        if (bookmark == null) {
            throw new IllegalArgumentException("Template thiếu bookmark: " + name);
        }
        return bookmark;
    }
}
