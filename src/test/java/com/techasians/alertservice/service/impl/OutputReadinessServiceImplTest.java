package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.dto.OutputReadinessResult;
import com.techasians.alertservice.entity.RptDefinition;
import com.techasians.alertservice.entity.RptInstance;
import com.techasians.alertservice.entity.RptOutputBlockDef;
import com.techasians.alertservice.entity.RptOutputSourceMap;
import com.techasians.alertservice.enums.*;
import com.techasians.alertservice.exception.InvalidReportStateException;
import com.techasians.alertservice.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutputReadinessServiceImplTest {
    private RptDefinitionRepository definitions;
    private RptOutputBlockDefRepository blocks;
    private RptOutputSourceMapRepository sourceMaps;
    private RptInstanceRepository instances;
    private OutputReadinessServiceImpl service;
    private final LocalDate weekStartDate = LocalDate.of(2026, 3, 17);

    @BeforeEach
    void setUp() {
        definitions = mock(RptDefinitionRepository.class);
        blocks = mock(RptOutputBlockDefRepository.class);
        sourceMaps = mock(RptOutputSourceMapRepository.class);
        instances = mock(RptInstanceRepository.class);
        service = new OutputReadinessServiceImpl(definitions, blocks, sourceMaps, instances);
        when(definitions.findById(2002L)).thenReturn(java.util.Optional.of(output(2002L)));
        when(blocks.findByOutputDefinitionIdAndStatusOrderBySortOrder(2002L, 1))
                .thenReturn(Arrays.asList(block(5001L), block(5002L)));
        when(sourceMaps.findByOutputBlockIdAndStatusOrderBySortOrder(5001L, 1))
                .thenReturn(Arrays.asList(source(1102L)));
        when(sourceMaps.findByOutputBlockIdAndStatusOrderBySortOrder(5002L, 1))
                .thenReturn(Arrays.asList(source(1102L)));
    }

    @Test
    void readyWhenAllDistinctSourcesAreCompleted() {
        when(instances.findByWeekStartDateAndDefinitionIdIn(eq(weekStartDate), anyList()))
                .thenReturn(Arrays.asList(instance(1102L, RptInstanceStatus.COMPLETED)));
        OutputReadinessResult result = service.checkReadiness(2002L, weekStartDate);
        assertTrue(result.isConfigured());
        assertTrue(result.isReadyToGenerate());
        assertTrue(result.getMissingSourceIds().isEmpty());
        verify(instances).findByWeekStartDateAndDefinitionIdIn(weekStartDate, Arrays.asList(1102L));
    }

    @Test
    void missingSourceIsReportedOnlyOnce() {
        when(instances.findByWeekStartDateAndDefinitionIdIn(eq(weekStartDate), anyList()))
                .thenReturn(Collections.emptyList());
        OutputReadinessResult result = service.checkReadiness(2002L, weekStartDate);
        assertFalse(result.isReadyToGenerate());
        assertEquals(Arrays.asList(1102L), result.getMissingSourceIds());
    }

    @Test
    void rejectedSourceIsMissing() {
        when(instances.findByWeekStartDateAndDefinitionIdIn(eq(weekStartDate), anyList()))
                .thenReturn(Arrays.asList(instance(1102L, RptInstanceStatus.REJECTED)));
        assertEquals(Arrays.asList(1102L), service.checkReadiness(2002L, weekStartDate).getMissingSourceIds());
    }

    @Test
    void outputWithoutActiveSourcesIsNotConfigured() {
        when(blocks.findByOutputDefinitionIdAndStatusOrderBySortOrder(2002L, 1)).thenReturn(Collections.emptyList());
        OutputReadinessResult result = service.checkReadiness(2002L, weekStartDate);
        assertFalse(result.isConfigured());
        assertFalse(result.isReadyToGenerate());
        verify(instances, never()).findByWeekStartDateAndDefinitionIdIn(any(), anyList());
    }

    @Test
    void batchReadinessLoadsDataOnceForAllOutputs() {
        RptDefinition firstOutput = output(2001L);
        RptDefinition secondOutput = output(2002L);
        List<RptDefinition> outputs = Arrays.asList(firstOutput, secondOutput);
        when(sourceMaps.findActiveSourceDefinitionIdsByOutputDefinitionIds(
                Arrays.asList(2001L, 2002L))).thenReturn(Arrays.asList(
                new Object[]{2001L, 1101L},
                new Object[]{2002L, 1102L}));
        when(instances.findByWeekStartDateAndDefinitionIdIn(
                weekStartDate, Arrays.asList(1101L, 1102L)))
                .thenReturn(Collections.singletonList(instance(1101L, RptInstanceStatus.COMPLETED)));

        Map<Long, OutputReadinessResult> results = service.checkReadinessBatch(outputs, weekStartDate);

        assertTrue(results.get(2001L).isReadyToGenerate());
        assertFalse(results.get(2002L).isReadyToGenerate());
        assertEquals(Collections.singletonList(1102L), results.get(2002L).getMissingSourceIds());
        verify(sourceMaps, times(1))
                .findActiveSourceDefinitionIdsByOutputDefinitionIds(Arrays.asList(2001L, 2002L));
        verifyNoInteractions(blocks);
        verify(instances, times(1)).findByWeekStartDateAndDefinitionIdIn(eq(weekStartDate), anyList());
        verify(definitions, never()).findById(anyLong());
    }

    @Test
    void inactiveOrNonGeneratedDefinitionIsRejected() {
        RptDefinition invalid = output(2002L).setSourceMode(RptSourceMode.IMPORT);
        when(definitions.findById(2002L)).thenReturn(java.util.Optional.of(invalid));
        assertThrows(InvalidReportStateException.class, () -> service.checkReadiness(2002L, weekStartDate));
    }

    private RptDefinition output(Long id) {
        return new RptDefinition().setId(id).setName("Output").setDirection(RptDirection.OUTPUT)
                .setNodeType(RptNodeType.FILE).setSourceMode(RptSourceMode.GENERATE)
                .setFileKind(RptFileKind.EXCEL).setAllowedExtension("xlsx").setStatus(1);
    }
    private RptOutputBlockDef block(Long id) { return new RptOutputBlockDef().setId(id).setStatus(1); }
    private RptOutputSourceMap source(Long definitionId) { return new RptOutputSourceMap().setSourceDefinitionId(definitionId).setStatus(1); }
    private RptInstance instance(Long definitionId, RptInstanceStatus status) {
        return new RptInstance().setDefinitionId(definitionId).setWeekStartDate(weekStartDate).setStatus(status);
    }
}
