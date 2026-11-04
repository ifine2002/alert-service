package com.techasians.alertservice.service.impl;

import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.aspose.words.Node;
import com.techasians.alertservice.entity.RptSectionDef;
import com.techasians.alertservice.enums.RptExtractMode;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WordSectionExtractorTest {
    private final WordSectionExtractor extractor = new WordSectionExtractor();

    @Test
    void copiesRangeWithTableWithoutBoundaryHeadings() throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.writeln("BẮT ĐẦU");
        builder.writeln("Nội dung cần giữ");
        builder.startTable();
        builder.insertCell();
        builder.write("Số liệu");
        builder.endRow();
        builder.endTable();
        builder.writeln("KẾT THÚC");
        RptSectionDef section = new RptSectionDef().setStartMarker("bắt đầu")
                .setEndMarker("kết thúc").setIncludeStart(0).setIncludeEnd(0);
        List<Node> result = extractor.extract(document, section);
        assertTrue(result.stream().anyMatch(node -> node instanceof com.aspose.words.Table));
        assertTrue(result.stream().anyMatch(node -> node.getText().contains("Nội dung cần giữ")));
        assertFalse(result.stream().anyMatch(node -> node.getText().contains("KẾT THÚC")));
    }

    @Test
    void missingMarkerIsRecoverableBusinessFailure() throws Exception {
        Document document = new Document();
        RptSectionDef section = new RptSectionDef().setStartMarker("Không tồn tại");
        assertThrows(WordSectionExtractor.MissingWordSectionException.class,
                () -> extractor.extract(document, section));
    }

    @Test
    void duplicateMarkerDoesNotSilentlyChooseFirst() throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.writeln("Trạng thái");
        builder.writeln("Trạng thái");
        assertThrows(WordSectionExtractor.MissingWordSectionException.class,
                () -> extractor.extract(document, new RptSectionDef().setStartMarker("Trạng thái")));
    }

    @Test
    void normalizesWhitespaceAndCombiningUnicode() {
        assertEquals("tiến độ", WordSectionExtractor.normalize("  TIẾN\u00a0 ĐỘ\r\n"));
    }

    @Test
    void nextParagraphsIgnoresEmptyParagraphsAndStopsAtCount() throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.writeln("Mục nguồn");
        builder.writeln("");
        builder.writeln("Một");
        builder.writeln("Hai");
        builder.writeln("Ba");
        RptSectionDef section = new RptSectionDef().setStartMarker("Mục nguồn")
                .setExtractMode(RptExtractMode.NEXT_PARAGRAPHS).setItemCount(2);
        List<Node> result = extractor.extract(document, section);
        assertEquals(2, result.size());
        assertEquals("Hai", result.get(1).getText().trim());
    }
}