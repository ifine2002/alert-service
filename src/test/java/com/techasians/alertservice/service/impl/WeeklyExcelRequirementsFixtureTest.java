package com.techasians.alertservice.service.impl;

import com.fasterxml.jackson.databind.*;
import com.techasians.alertservice.entity.RptExcelSheetDef;
import com.techasians.alertservice.repository.RptExcelSheetDefRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named = "word.realFixtures", matches = "true")
class WeeklyExcelRequirementsFixtureTest {
    @Test
    void validatesAllDocumentedSheetsAgainstActualInputWorkbooks() throws Exception {
        JsonNode requirements = new ObjectMapper().readTree(Paths.get("documents/weekly-report-excel-requirements.json").toFile());
        Map<Long, List<JsonNode>> grouped = new LinkedHashMap<>();
        for (JsonNode item : requirements.get("sheets")) {
            grouped.computeIfAbsent(item.get("definitionId").asLong(), key -> new ArrayList<>()).add(item);
        }
        int totalSheets = 0;
        for (Map.Entry<Long, List<JsonNode>> entry : grouped.entrySet()) {
            RptExcelSheetDefRepository sheets = mock(RptExcelSheetDefRepository.class);
            List<RptExcelSheetDef> sheetDefs = new ArrayList<>();
            for (JsonNode item : entry.getValue()) {
                sheetDefs.add(new RptExcelSheetDef().setDefinitionId(entry.getKey()).setSheetName(item.get("sheetName").asText()));
            }
            totalSheets += sheetDefs.size();
            when(sheets.findByDefinitionIdAndStatusOrderBySortOrderAsc(entry.getKey(), 1)).thenReturn(sheetDefs);
            String prefix = entry.getValue().get(0).get("sourceFilePrefix").asText();
            Path file;
            try (Stream<Path> paths = Files.walk(Paths.get("documents"))) {
                file = paths.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().startsWith(prefix)).findFirst().orElseThrow();
            }
            String name = file.getFileName().toString();
            String extension = name.substring(name.lastIndexOf('.') + 1);
            MockMultipartFile upload = new MockMultipartFile("file", name, "application/octet-stream", Files.readAllBytes(file));
            assertDoesNotThrow(() -> new ReportValidationServiceImpl(sheets)
                    .validate(upload, extension, entry.getKey(), LocalDate.of(2026, 9, 1)), "Definition " + entry.getKey());
        }
        assertEquals(15, totalSheets);
    }
}
