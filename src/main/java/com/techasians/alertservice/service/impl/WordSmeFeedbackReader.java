package com.techasians.alertservice.service.impl;

import com.aspose.words.*;
import com.techasians.alertservice.entity.RptSectionDef;
import com.techasians.alertservice.enums.RptExtractMode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.techasians.alertservice.service.impl.WordSectionExtractor.MissingWordSectionException;

/** Đọc diễn giải SME trước ảnh đầu tiên và gộp các gạch đầu dòng thành một paragraph. */
@Component
public class WordSmeFeedbackReader {
    private static final Pattern PREFIX = Pattern.compile("^[\\s\\p{Zs}]*(?:[-–—•●▪◦][\\s\\p{Zs}]+)?");

    public Paragraph read(Document source, RptSectionDef definition) throws Exception {
        validateSection(definition);
        String startMarker = definition.getStartMarker();
        String endMarker = definition.getEndMarker();
        List<Node> blocks = new ArrayList<>();
        int start = -1;
        for (Section section : source.getSections()) {
            for (Node node = section.getBody().getFirstChild(); node != null; node = node.getNextSibling()) {
                blocks.add(node);
                if (matches(node, startMarker)) {
                    if (start >= 0) throw missing("Trùng marker " + startMarker);
                    start = blocks.size() - 1;
                }
            }
        }
        if (start < 0) throw missing("Không tìm thấy marker " + startMarker);

        Document fragment = new Document();
        Paragraph merged = fragment.getFirstSection().getBody().getFirstParagraph();
        TextState state = new TextState();
        boolean bounded = false;
        for (int index = definition.isIncludeStart() ? start : start + 1; index < blocks.size(); index++) {
            Node block = blocks.get(index);
            if (index > start && matches(block, endMarker)) {
                if (definition.isIncludeEnd()) {
                    List<Run> runs = new ArrayList<>();
                    collectUntilImage(block, runs);
                    appendRuns(merged, runs, state);
                }
                bounded = true;
                break;
            }
            if (!(block instanceof Paragraph)) throw missing("Gặp bảng trước ảnh hoặc marker kết thúc SME");
            List<Run> runs = new ArrayList<>();
            boolean image = collectUntilImage(block, runs);
            appendRuns(merged, runs, state);
            if (image) {
                bounded = true;
                break;
            }
            state.space = state.hasText;
        }
        if (!bounded) throw missing("Thiếu ảnh và marker kết thúc " + endMarker);
        if (!state.hasText) throw missing("Không có nội dung sau marker " + startMarker);
        return merged;
    }

    void validateSection(RptSectionDef definition) {
        if (definition == null || definition.getExtractMode() != RptExtractMode.TEXT_RANGE) {
            throw new IllegalArgumentException("SME phải có section dùng extract_mode TEXT_RANGE");
        }
        if (WordSectionExtractor.normalize(definition.getStartMarker()).isEmpty()
                || WordSectionExtractor.normalize(definition.getEndMarker()).isEmpty()) {
            throw new IllegalArgumentException("Section SME thiếu start_marker hoặc end_marker");
        }
        if ((!Integer.valueOf(0).equals(definition.getIncludeStart())
                && !Integer.valueOf(1).equals(definition.getIncludeStart()))
                || (!Integer.valueOf(0).equals(definition.getIncludeEnd())
                && !Integer.valueOf(1).equals(definition.getIncludeEnd()))) {
            throw new IllegalArgumentException("Section SME phải cấu hình include_start/include_end bằng 0 hoặc 1");
        }
    }

    private boolean matches(Node node, String marker) throws Exception {
        return node instanceof Paragraph
                && WordSectionExtractor.normalize(node.getText()).contains(WordSectionExtractor.normalize(marker));
    }

    /** Duyệt theo thứ tự node để giữ chữ trước ảnh ngay cả khi ảnh nằm chung paragraph. */
    private boolean collectUntilImage(Node node, List<Run> runs) throws Exception {
        if (node instanceof Shape && ((Shape) node).hasImage()) return true;
        if (node instanceof Run) runs.add((Run) node);
        else if (node instanceof CompositeNode) {
            for (Node child = ((CompositeNode) node).getFirstChild(); child != null; child = child.getNextSibling()) {
                if (collectUntilImage(child, runs)) return true;
            }
        }
        return false;
    }

    private void appendRuns(Paragraph target, List<Run> runs, TextState state) throws Exception {
        StringBuilder plain = new StringBuilder();
        for (Run run : runs) plain.append(run.getText());
        Matcher prefix = PREFIX.matcher(plain);
        int first = prefix.find() ? prefix.end() : 0;
        int offset = 0;
        for (Run run : runs) {
            String text = run.getText();
            StringBuilder cleaned = new StringBuilder();
            for (int index = Math.max(0, first - offset); index < text.length(); index++) {
                char value = text.charAt(index);
                if (Character.isWhitespace(value) || Character.isSpaceChar(value)) {
                    state.space = state.hasText;
                } else {
                    if (state.space) cleaned.append(' ');
                    cleaned.append(value);
                    state.space = false;
                    state.hasText = true;
                }
            }
            offset += text.length();
            if (cleaned.length() > 0) {
                Run copy = (Run) target.getDocument().importNode(run, true);
                copy.setText(cleaned.toString());
                target.appendChild(copy);
            }
        }
    }

    private MissingWordSectionException missing(String reason) { return new MissingWordSectionException(reason); }

    private static class TextState {
        private boolean hasText;
        private boolean space;
    }
}
