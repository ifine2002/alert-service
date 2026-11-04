package com.techasians.alertservice.service.impl;

import com.aspose.words.*;
import com.techasians.alertservice.entity.RptSectionDef;
import com.techasians.alertservice.enums.RptExtractMode;
import org.springframework.stereotype.Component;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Trích xuất node gốc để giữ nguyên bảng và hình ảnh của section. */
@Component
public class WordSectionExtractor {
    public List<Node> extract(Document source, RptSectionDef definition) throws Exception {
        List<Node> nodes = new ArrayList<>();
        for (Section section : source.getSections()) {
            for (Node node = section.getBody().getFirstChild(); node != null; node = node.getNextSibling()) {
                nodes.add(node);
            }
        }
        RptExtractMode mode = definition.getExtractMode();
        if (mode == null) throw new IllegalArgumentException("Section chưa có extract mode");
        if (mode == RptExtractMode.WHOLE_DOCUMENT) return nodes;
        int start = findMarker(nodes, definition.getStartMarker(), 0);
        int end = nodes.size();
        if (!normalize(definition.getEndMarker()).isEmpty()) {
            end = findMarker(nodes, definition.getEndMarker(), start + 1);
        }
        List<Node> selected = new ArrayList<>();
        if (mode == RptExtractMode.PARAGRAPH_MARKER) {
            selected.add(nodes.get(start));
        } else if (mode == RptExtractMode.TEXT_RANGE) {
            int first = definition.isIncludeStart() ? start : start + 1;
            int last = definition.isIncludeEnd() && end < nodes.size() ? end + 1 : end;
            for (int index = first; index < last; index++) selected.add(nodes.get(index));
        } else {
            Integer count = definition.getItemCount();
            if (count == null || count <= 0) throw new IllegalArgumentException("item_count phải lớn hơn 0");
            selectItems(nodes, start + 1, end, count, mode, selected);
        }
        if (selected.isEmpty()) throw new MissingWordSectionException("Section không có nội dung");
        return selected;
    }

    private int findMarker(List<Node> nodes, String marker, int first) throws Exception {
        String expected = normalize(marker);
        if (expected.isEmpty()) throw new IllegalArgumentException("Marker không được để trống");
        int found = -1;
        for (int index = first; index < nodes.size(); index++) {
            Node node = nodes.get(index);
            // Không tìm trong text tổng hợp của bảng vì có thể khớp nhầm dữ liệu ô.
            if (node.getNodeType() != NodeType.PARAGRAPH) continue;
            if (normalize(node.getText()).contains(expected)) {
                if (found >= 0) throw new MissingWordSectionException("Marker bị trùng: " + marker);
                found = index;
            }
        }
        if (found < 0) throw new MissingWordSectionException("Không tìm thấy marker: " + marker);
        return found;
    }

    private void selectItems(List<Node> nodes, int first, int last, int count,
                             RptExtractMode mode, List<Node> selected) throws Exception {
        int found = 0;
        int baseLevel = -1;
        for (int index = first; index < last; index++) {
            Node node = nodes.get(index);
            if (mode == RptExtractMode.NEXT_PARAGRAPHS) {
                if (node.getNodeType() == NodeType.PARAGRAPH && !normalize(node.getText()).isEmpty()) {
                    selected.add(node);
                    found++;
                    if (found == count) break;
                }
            } else if (mode == RptExtractMode.NEXT_LIST_ITEMS) {
                if (node.getNodeType() == NodeType.PARAGRAPH && ((Paragraph) node).isListItem()) {
                    int level = ((Paragraph) node).getListFormat().getListLevelNumber();
                    if (baseLevel < 0) baseLevel = level;
                    if (level < baseLevel) break;
                    if (level == baseLevel) {
                        if (found == count) break;
                        found++;
                    }
                }
                if (found > 0) selected.add(node);
            } else {
                throw new IllegalArgumentException("Extract mode chưa được hỗ trợ: " + mode);
            }
        }
        if (found < count) throw new MissingWordSectionException("Section không đủ " + count + " mục");
    }

    static String normalize(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFC)
                .replace('\u00a0', ' ').replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    /** Thiếu section cho phép xuất thiếu; không dùng exception này cho lỗi render/lưu file. */
    public static class MissingWordSectionException extends RuntimeException {
        public MissingWordSectionException(String message) { super(message); }
    }
}
