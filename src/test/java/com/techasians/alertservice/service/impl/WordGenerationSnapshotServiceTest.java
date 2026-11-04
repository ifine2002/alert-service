package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.entity.*;
import com.techasians.alertservice.enums.*;
import com.techasians.alertservice.repository.*;
import com.techasians.alertservice.service.WordGenerationSnapshot;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WordGenerationSnapshotServiceTest {
    @Test
    void loadsConfiguredSectionForSmeCustomHandler() {
        RptDefinitionRepository definitions = mock(RptDefinitionRepository.class);
        RptOutputBlockDefRepository blocks = mock(RptOutputBlockDefRepository.class);
        RptOutputSourceMapRepository maps = mock(RptOutputSourceMapRepository.class);
        RptSectionDefRepository sections = mock(RptSectionDefRepository.class);
        RptInstanceRepository instances = mock(RptInstanceRepository.class);
        RptFileRepository files = mock(RptFileRepository.class);
        LocalDate week = LocalDate.of(2026, 9, 1);
        when(definitions.findById(2001L)).thenReturn(Optional.of(new RptDefinition()
                .setId(2001L).setFileKind(RptFileKind.WORD).setDirection(RptDirection.OUTPUT)
                .setSourceMode(RptSourceMode.GENERATE).setStatus(1)
                .setTemplateRelativePath("templates/weekly-vcx-word-template.docx")));
        RptOutputBlockDef block = new RptOutputBlockDef().setId(8212L).setBlockCode("SME")
                .setTargetName("BM_SME").setRenderType(RptRenderType.CUSTOM_HANDLER);
        RptOutputSourceMap mapping = new RptOutputSourceMap().setSourceDefinitionId(1108L)
                .setSourceRole(RptSourceRole.TEXT).setSourceSectionDefId(8110L);
        RptSectionDef section = WordSmeFeedbackReaderTest.section().setStartMarker("Tiêu đề mới từ DB:")
                .setEndMarker("Kết thúc mới từ DB:").setIncludeStart(1);
        when(blocks.findByOutputDefinitionIdAndStatusOrderBySortOrder(2001L, 1))
                .thenReturn(Collections.singletonList(block));
        when(maps.findByOutputBlockIdAndStatusOrderBySortOrder(8212L, 1))
                .thenReturn(Collections.singletonList(mapping));
        when(sections.findById(8110L)).thenReturn(Optional.of(section));
        when(instances.findByDefinitionIdAndWeekStartDate(1108L, week)).thenReturn(Optional.empty());

        WordGenerationSnapshot snapshot = new WordGenerationSnapshotService(definitions, blocks, maps,
                sections, instances, files).load(2001L, week);
        WordGenerationSnapshot.Source source = snapshot.getBlocks().get(0).getSources().get(0);
        assertSame(section, source.getSection());
        assertEquals("Tiêu đề mới từ DB:", source.getSection().getStartMarker());
        assertTrue(source.getSection().isIncludeStart());
        assertNull(source.getFile());
        verify(sections).findById(8110L);
        verifyNoInteractions(files);
    }

    @Test
    void accumulatedStationChartSharesCurrentWorkbookWithSummaryWithoutLoadingSevenOtherFiles() {
        RptDefinitionRepository definitions = mock(RptDefinitionRepository.class);
        RptOutputBlockDefRepository blocks = mock(RptOutputBlockDefRepository.class);
        RptOutputSourceMapRepository maps = mock(RptOutputSourceMapRepository.class);
        RptSectionDefRepository sections = mock(RptSectionDefRepository.class);
        RptInstanceRepository instances = mock(RptInstanceRepository.class);
        RptFileRepository files = mock(RptFileRepository.class);
        LocalDate week = LocalDate.of(2026, 9, 1);
        when(definitions.findById(2001L)).thenReturn(Optional.of(new RptDefinition()
                .setId(2001L).setFileKind(RptFileKind.WORD).setDirection(RptDirection.OUTPUT)
                .setSourceMode(RptSourceMode.GENERATE).setStatus(1).setTemplateRelativePath("templates/word.docx")));
        when(blocks.findByOutputDefinitionIdAndStatusOrderBySortOrder(2001L, 1)).thenReturn(Arrays.asList(
                new RptOutputBlockDef().setId(8213L).setBlockCode("STATION_OVERVIEW"),
                new RptOutputBlockDef().setId(8214L).setBlockCode("STATION_INCIDENT_CHART")));
        when(maps.findByOutputBlockIdAndStatusOrderBySortOrder(8213L, 1)).thenReturn(Collections.singletonList(
                new RptOutputSourceMap().setSourceDefinitionId(1102L).setSourceRole(RptSourceRole.DATA)));
        when(maps.findByOutputBlockIdAndStatusOrderBySortOrder(8214L, 1)).thenReturn(Arrays.asList(
                new RptOutputSourceMap().setSourceDefinitionId(1102L).setSourceRole(RptSourceRole.CHART_ACCUMULATED)
                        .setSortOrder(1).setSourceSheetName("Lũy kế trạm năm {yyyy}"),
                new RptOutputSourceMap().setSourceDefinitionId(1102L).setSourceRole(RptSourceRole.CHART_ACCUMULATED)
                        .setSortOrder(2).setSourceSheetName("Tổng trạm {yyyy-1}")));
        when(instances.findByDefinitionIdAndWeekStartDate(1102L, week)).thenReturn(Optional.of(
                new RptInstance().setId(7L).setCurrentFileId(8L).setStatus(RptInstanceStatus.COMPLETED)));
        when(files.findById(8L)).thenReturn(Optional.of(new RptFile().setId(8L).setReportInstanceId(7L)));
        WordGenerationSnapshot snapshot = new WordGenerationSnapshotService(definitions, blocks, maps,
                sections, instances, files).load(2001L, week);
        for (WordGenerationSnapshot.Block block : snapshot.getBlocks()) {
            assertEquals(block.getDefinition().getId() == 8214L ? 2 : 1, block.getSources().size());
            for (WordGenerationSnapshot.Source source : block.getSources()) {
                assertEquals(week, source.getWeekStartDate());
                assertSame(snapshot.getBlocks().get(0).getSources().get(0).getFile(), source.getFile());
            }
        }
        assertSame(snapshot.getBlocks().get(0).getSources().get(0).getFile(),
                snapshot.getBlocks().get(1).getSources().get(0).getFile());
        verify(instances, times(1)).findByDefinitionIdAndWeekStartDate(1102L, week);
        verifyNoMoreInteractions(instances);
        verify(files, times(1)).findById(8L);
    }

    @Test
    void keepsMissingSourcesAndLoadsEightChartWeeks() {
        RptDefinitionRepository definitions = mock(RptDefinitionRepository.class);
        RptOutputBlockDefRepository blocks = mock(RptOutputBlockDefRepository.class);
        RptOutputSourceMapRepository maps = mock(RptOutputSourceMapRepository.class);
        RptSectionDefRepository sections = mock(RptSectionDefRepository.class);
        RptInstanceRepository instances = mock(RptInstanceRepository.class);
        RptFileRepository files = mock(RptFileRepository.class);
        LocalDate week = LocalDate.of(2026, 9, 1);
        when(definitions.findById(2001L)).thenReturn(Optional.of(new RptDefinition()
                .setId(2001L).setFileKind(RptFileKind.WORD).setDirection(RptDirection.OUTPUT)
                .setSourceMode(RptSourceMode.GENERATE).setStatus(1).setTemplateRelativePath("templates/word.docx")));
        when(blocks.findByOutputDefinitionIdAndStatusOrderBySortOrder(2001L, 1))
                .thenReturn(Collections.singletonList(new RptOutputBlockDef().setId(5001L)));
        when(maps.findByOutputBlockIdAndStatusOrderBySortOrder(5001L, 1))
                .thenReturn(Collections.singletonList(new RptOutputSourceMap()
                        .setSourceDefinitionId(1102L).setSourceRole(RptSourceRole.CHART)));
        when(instances.findByDefinitionIdAndWeekStartDate(1102L, week))
                .thenReturn(Optional.of(new RptInstance().setId(7L).setCurrentFileId(8L)
                        .setStatus(RptInstanceStatus.COMPLETED)));
        when(files.findById(8L)).thenReturn(Optional.of(new RptFile().setId(8L).setReportInstanceId(7L)));
        WordGenerationSnapshot snapshot = new WordGenerationSnapshotService(definitions, blocks, maps,
                sections, instances, files).load(2001L, week);
        List<WordGenerationSnapshot.Source> sources = snapshot.getBlocks().get(0).getSources();
        assertEquals(8, sources.size());
        assertEquals(week.minusWeeks(7), sources.get(0).getWeekStartDate());
        assertNull(sources.get(0).getFile());
        assertEquals(8L, sources.get(7).getFile().getId());
        verify(files, times(1)).findById(8L);
    }
}
