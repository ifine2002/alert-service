package com.techasians.alertservice.service.impl;

import com.aspose.words.Document;
import com.aspose.words.Node;
import com.techasians.alertservice.entity.RptSectionDef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Optional local-fixture checks; no database, network or macro execution. */
@EnabledIfSystemProperty(named = "word.realFixtures", matches = "true")
class WordRealFixtureTest {
    @Test
    void preservesLongDocumentWithoutEvaluationArtifacts() throws Exception {
        Document document = new Document();
        com.aspose.words.DocumentBuilder builder = new com.aspose.words.DocumentBuilder(document);
        for (int index = 0; index < 600; index++) {
            builder.writeln("WORD_FIDELITY_PARAGRAPH_" + index);
        }
        Path output = Files.createTempFile("word-fidelity-", ".docx");
        try {
            document.save(output.toString());
            String text = new Document(output.toString()).getText();
            assertFalse(text.contains("Evaluation Only"), "Aspose evaluation watermark found");
            assertFalse(text.contains("evaluation mode"), "Aspose evaluation limit found");
            for (int index = 0; index < 600; index++) {
                assertTrue(text.contains("WORD_FIDELITY_PARAGRAPH_" + index + "\r"),
                        "Missing paragraph " + index);
            }
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private Path find(String prefix, String extension) throws Exception {
        try (Stream<Path> paths = Files.walk(Paths.get("documents"))) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(prefix))
                    .filter(path -> path.toString().endsWith(extension)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Missing fixture: " + prefix));
        }
    }

    @Test
    void extractsFixedProgressFromActualMacroEnabledDocument() throws Exception {
        Document document = new Document(find("BC DVCDBR TUAN", ".docm").toString());
        RptSectionDef section = new RptSectionDef().setStartMarker("2.3 Ti\u1ebfn \u0111\u1ed9 x\u1eed l\u00fd:")
                .setEndMarker("\u0110\u1ec0 XU\u1ea4T /KI\u1ebeN NGH\u1eca:").setIncludeStart(1).setIncludeEnd(0);
        List<Node> nodes = new WordSectionExtractor().extract(document, section);
        StringBuilder content = new StringBuilder();
        for (Node node : nodes) content.append(node.getText());
        assertTrue(content.toString().contains("3/6"));
        assertTrue(content.toString().contains("6/6"));
        assertFalse(content.toString().contains(section.getEndMarker()));
    }

    @Test
    void extractsSmeKpiWithoutAppendixTable() throws Exception {
        Document document = new Document(find("Bao cao CLDV thong qua", ".docx").toString());
        RptSectionDef section = new RptSectionDef()
                .setStartMarker("K\u1ebft qu\u1ea3 th\u1ef1c hi\u1ec7n c\u00e1c KPI ch\u00ednh:")
                .setEndMarker("B\u1ea3ng ti\u1ebfp nh\u1eadn theo d\u1ecbch v\u1ee5:").setIncludeStart(1).setIncludeEnd(0);
        List<Node> nodes = new WordSectionExtractor().extract(document, section);
        assertFalse(nodes.isEmpty());
        assertTrue(nodes.get(0).getText().contains("4/4"));
        for (Node node : nodes) assertFalse(node.getText().contains(section.getEndMarker()));
    }
}