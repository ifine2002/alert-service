package com.techasians.alertservice.service.impl;

import com.fasterxml.jackson.databind.*;
import com.techasians.alertservice.entity.*;
import com.techasians.alertservice.repository.*;
import com.techasians.alertservice.exception.ReportFileValidationException;
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
    void validatesAllDocumentedSheetsAndHeaderNamesAgainstActualInputWorkbooks() throws Exception {
        JsonNode requirements = new ObjectMapper().readTree(Paths.get("documents/weekly-report-excel-requirements.json").toFile());
        Map<Long, List<JsonNode>> grouped = new LinkedHashMap<>();
        for (JsonNode item : requirements.get("sheets")) grouped.computeIfAbsent(item.get("definitionId").asLong(), key -> new ArrayList<>()).add(item);
        long sheetId = 1;
        for (Map.Entry<Long, List<JsonNode>> entry : grouped.entrySet()) {
            RptExcelSheetDefRepository sheets = mock(RptExcelSheetDefRepository.class);
            RptExcelHeaderDefRepository headers = mock(RptExcelHeaderDefRepository.class);
            List<RptExcelSheetDef> sheetDefs = new ArrayList<>();
            List<RptExcelHeaderDef> headerDefs = new ArrayList<>();
            for (JsonNode item : entry.getValue()) {
                long id = sheetId++;
                sheetDefs.add(new RptExcelSheetDef().setId(id).setDefinitionId(entry.getKey()).setSheetName(item.get("sheetName").asText()));
                for (JsonNode name : item.get("headers")) headerDefs.add(new RptExcelHeaderDef().setSheetDefId(id).setHeaderName(name.asText()));
            }
            String prefix = entry.getValue().get(0).get("sourceFilePrefix").asText();
            Path file;
            try (Stream<Path> paths = Files.walk(Paths.get("documents"))) {
                file = paths.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().startsWith(prefix)).findFirst().orElseThrow();
            }
            String name = file.getFileName().toString();
            String extension = name.substring(name.lastIndexOf('.') + 1);
            MockMultipartFile upload = new MockMultipartFile("file", name, "application/octet-stream", Files.readAllBytes(file));
            for (RptExcelSheetDef sheet : sheetDefs) {
                JsonNode item = entry.getValue().stream().filter(candidate -> candidate.get("sheetName").asText().equals(sheet.getSheetName())).findFirst().orElseThrow();
                List<RptExcelHeaderDef> required = new ArrayList<>();
                for (RptExcelHeaderDef header : headerDefs) if (header.getSheetDefId().equals(sheet.getId())) required.add(header);
                when(sheets.findByDefinitionIdAndStatusOrderBySortOrderAsc(entry.getKey(), 1)).thenReturn(Collections.singletonList(sheet));
                when(headers.findBySheetDefIdInAndStatusOrderBySheetDefIdAscIdAsc(anyList(), eq(1))).thenReturn(required);
                ReportValidationServiceImpl service = new ReportValidationServiceImpl(sheets, headers);
                if (item.has("sampleMissingHeaders")) {
                    List<String> missing = new ArrayList<>();
                    item.get("sampleMissingHeaders").forEach(value -> missing.add(value.asText()));
                    ReportFileValidationException failure = assertThrows(ReportFileValidationException.class,
                            () -> service.validate(upload, extension, entry.getKey(), LocalDate.of(2026, 9, 1)));
                    assertEquals("Sheet \"" + ReportSheetNameResolver.resolve(sheet.getSheetName(), LocalDate.of(2026, 9, 1))
                            + "\" thiếu hoặc sai header bắt buộc: " + String.join(", ", missing), failure.getMessage());
                } else {
                    assertDoesNotThrow(() -> service.validate(upload, extension, entry.getKey(), LocalDate.of(2026, 9, 1)),
                            "Definition " + entry.getKey() + ", sheet " + sheet.getSheetName());
                }
            }
        }
        assertEquals(15, sheetId - 1);
    }
}
