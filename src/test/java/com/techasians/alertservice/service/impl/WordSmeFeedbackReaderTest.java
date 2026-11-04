package com.techasians.alertservice.service.impl;

import com.aspose.words.*;
import com.techasians.alertservice.entity.RptSectionDef;
import com.techasians.alertservice.enums.RptExtractMode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class WordSmeFeedbackReaderTest {
    static final String START = "Tình hình tiếp nhận và xử lý PAKH:";
    static final String END = "Xu thế PAKH theo tuần:";
    static final byte[] IMAGE = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a9WQAAAAASUVORK5CYII=");
    private final WordSmeFeedbackReader reader = new WordSmeFeedbackReader();

    @Test
    void usesConfiguredMarkersAndIgnoresPreviousHeadings() throws Exception {
        Document source = new Document();
        DocumentBuilder builder = new DocumentBuilder(source);
        builder.writeln(START);
        builder.writeln("Không lấy vùng theo marker cũ.");
        builder.writeln(END);
        builder.writeln("Bắt đầu phần SME mới:");
        builder.writeln("Nội dung đúng cấu hình DB.");
        builder.writeln("Kết thúc phần SME mới:");
        builder.writeln("Không lấy phần sau.");
        RptSectionDef definition = section().setStartMarker("Bắt đầu phần SME mới:")
                .setEndMarker("Kết thúc phần SME mới:");
        assertEquals("Nội dung đúng cấu hình DB.", reader.read(source, definition).getText().trim());
    }

    @Test
    void respectsConfiguredIncludeStartAndIncludeEndFlags() throws Exception {
        Document source = new Document();
        DocumentBuilder builder = new DocumentBuilder(source);
        builder.writeln(START);
        builder.writeln("Nội dung.");
        builder.writeln(END);
        builder.writeln("Không lấy phần sau.");
        for (int includeStart = 0; includeStart <= 1; includeStart++) {
            for (int includeEnd = 0; includeEnd <= 1; includeEnd++) {
                RptSectionDef definition = section().setIncludeStart(includeStart).setIncludeEnd(includeEnd);
                String expected = (includeStart == 1 ? START + " " : "") + "Nội dung."
                        + (includeEnd == 1 ? " " + END : "");
                assertEquals(expected, reader.read(source, definition).getText().trim());
            }
        }
    }

    @Test
    void imageStillStopsExtractionWhenEndHeadingIsConfiguredToBeIncluded() throws Exception {
        Document source = new Document();
        DocumentBuilder builder = new DocumentBuilder(source);
        builder.writeln(START);
        builder.write("Nội dung trước ảnh.");
        builder.insertImage(IMAGE);
        builder.writeln("Không lấy chữ sau ảnh.");
        builder.writeln(END);
        assertEquals("Nội dung trước ảnh.", reader.read(source, section().setIncludeEnd(1)).getText().trim());
    }

    @Test
    void rejectsInvalidSectionConfiguration() throws Exception {
        Document source = new Document();
        for (RptSectionDef definition : new RptSectionDef[] { null,
                section().setStartMarker(" "), section().setEndMarker(null),
                section().setExtractMode(RptExtractMode.WHOLE_DOCUMENT),
                section().setIncludeStart(null), section().setIncludeEnd(2) }) {
            assertThrows(IllegalArgumentException.class, () -> reader.read(source, definition));
        }
    }

    @Test
    void joinsTwoListItemsAndKeepsEmphasisWithoutCopyingImagesOrLaterText() throws Exception {
        Document source = new Document();
        DocumentBuilder builder = new DocumentBuilder(source);
        builder.writeln("Không lấy nội dung trước tiêu đề.");
        builder.writeln(START.toUpperCase().replace(' ', '\u00a0'));
        builder.getListFormat().applyBulletDefault();
        builder.write("Trong tuần tiếp nhận ");
        builder.getFont().setBold(true);
        builder.write("1.003PA");
        builder.getFont().setBold(false);
        builder.writeln(" (TB: 143PA/ngày).");
        builder.getFont().setItalic(true);
        builder.writeln("Trong đó: 43% báo lỗi và 57% hỗ trợ KH.");
        builder.getListFormat().removeNumbers();
        builder.writeln("");
        builder.insertImage(IMAGE);
        builder.writeln();
        builder.writeln("Không lấy phần sau ảnh.");
        builder.writeln(END);
        String original = source.getText();

        Paragraph content = reader.read(source, section());
        assertEquals("Trong tuần tiếp nhận 1.003PA (TB: 143PA/ngày). Trong đó: 43% báo lỗi và 57% hỗ trợ KH.",
                content.getText().trim());
        assertFalse(content.isListItem());
        assertEquals(0, content.getChildNodes(NodeType.SHAPE, true).getCount());
        assertBoldText(content, "1.003PA");
        assertTrue(findRun(content, "Trong đó:").getFont().getItalic());
        assertEquals(original, source.getText(), "Reader must not mutate the cached source");
    }

    @Test
    void keepsOnlyTextBeforeAnInlineImageEvenWithoutFallbackMarker() throws Exception {
        Document source = new Document();
        DocumentBuilder builder = new DocumentBuilder(source);
        builder.writeln(START);
        builder.write("Nội dung trước ảnh.");
        builder.insertImage(IMAGE);
        builder.write("Không lấy chữ sau ảnh trong cùng paragraph.");
        assertEquals("Nội dung trước ảnh.", reader.read(source, section()).getText().trim());
    }

    @Test
    void fallsBackToEndMarkerAndNormalizesManualBulletsAcrossRunsAndLineBreaks() throws Exception {
        Document source = new Document();
        DocumentBuilder builder = new DocumentBuilder(source);
        builder.writeln(START);
        builder.write("  -");
        builder.getFont().setBold(true);
        builder.write("\t Trong  tuần\u00a0tiếp nhận 1.003PA. ");
        builder.getFont().setBold(false);
        builder.writeln();
        builder.writeln("   ");
        builder.writeln("• Trong đó:\u000b43% báo lỗi.  ");
        builder.writeln(END);
        builder.writeln("Không lấy KPI.");
        assertEquals("Trong tuần tiếp nhận 1.003PA. Trong đó: 43% báo lỗi.", reader.read(source, section()).getText().trim());
    }

    @Test
    void doesNotCopyAnImageWhenFallbackHeadingComesFirst() throws Exception {
        Document source = new Document();
        DocumentBuilder builder = new DocumentBuilder(source);
        builder.writeln(START);
        builder.writeln("Nội dung cần lấy.");
        builder.writeln(END);
        builder.writeln("Không lấy xu thế.");
        builder.insertImage(IMAGE);
        assertEquals("Nội dung cần lấy.", reader.read(source, section()).getText().trim());
    }

    @Test
    void readsAcrossWordSectionsWithoutRequiringAnImage() throws Exception {
        Document source = new Document();
        DocumentBuilder builder = new DocumentBuilder(source);
        builder.writeln(START);
        builder.write("Đoạn thứ nhất.");
        builder.insertBreak(BreakType.SECTION_BREAK_NEW_PAGE);
        builder.writeln("Đoạn thứ hai.");
        builder.writeln(END);
        assertEquals("Đoạn thứ nhất. Đoạn thứ hai.", reader.read(source, section()).getText().trim());
    }

    @Test
    void rejectsMissingOrDuplicateStartMissingEndAndEmptyContent() throws Exception {
        for (int mode = 0; mode < 5; mode++) {
            Document source = new Document();
            DocumentBuilder builder = new DocumentBuilder(source);
            if (mode != 0) builder.writeln(START);
            if (mode == 1) builder.writeln(START);
            if (mode != 3) builder.writeln("Nội dung không đủ điều kiện.");
            if (mode == 4) builder.startTable();
            if (mode == 4) {
                builder.insertCell(); builder.write("Không lấy bảng"); builder.endRow(); builder.endTable();
            }
            if (mode != 2) builder.writeln(END);
            assertThrows(WordSectionExtractor.MissingWordSectionException.class, () -> reader.read(source, section()), "mode=" + mode);
        }
    }

    @Test
    void readsActualReportAsOneParagraph() throws Exception {
        assumeTrue(Files.isRegularFile(fixture()));
        Paragraph content = reader.read(new Document(fixture().toString()), section());
        assertEquals("Trong tuần tiếp nhận 1.003PA (TB: 143PA/ngày) KH báo lỗi dịch vụ SME, giảm 19% so với tuần trước "
                + "(1.238PA ~TB: 177PA/ngày). Trong đó: 43% báo lỗi và 57% hỗ trợ KH. PA tập trung tại: "
                + "Dịch vụ HDDT 33% (330PA); Stracking 16% (160PA); Vtracking 14% (138PA); …", content.getText().trim());
        assertFalse(content.isListItem());
        assertBoldText(content, "1.003PA");
    }

    static RptSectionDef section() {
        return new RptSectionDef().setId(8110L).setSourceDefinitionId(1108L)
                .setExtractMode(RptExtractMode.TEXT_RANGE).setStartMarker(START).setEndMarker(END)
                .setIncludeStart(0).setIncludeEnd(0).setStatus(1);
    }

    static Path fixture() {
        return Paths.get("documents", "File 2", "3+4. Báo cáo PAKH dịch vụ chất lượng mạng + SME tuần_thuyntg",
                "Bao cao CLDV thong qua PAKH tuan 01.09-07.09.docx");
    }

    static Run findRun(Paragraph paragraph, String text) throws Exception {
        for (Run run : paragraph.getRuns()) if (run.getText().contains(text)) return run;
        throw new AssertionError("Missing run: " + text);
    }

    static void assertBoldText(Paragraph paragraph, String text) throws Exception {
        int first = paragraph.getText().indexOf(text);
        assertTrue(first >= 0, "Missing text: " + text);
        int offset = 0;
        for (Run run : paragraph.getRuns()) {
            int end = offset + run.getText().length();
            if (end > first && offset < first + text.length()) assertTrue(run.getFont().getBold(), run.getText());
            offset = end;
        }
    }
}
