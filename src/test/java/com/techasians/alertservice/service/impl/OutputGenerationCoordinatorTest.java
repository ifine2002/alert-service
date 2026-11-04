package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.dto.OutputReadinessResult;
import com.techasians.alertservice.event.OutputGenerationPreparedEvent;
import com.techasians.alertservice.repository.RptDefinitionRepository;
import com.techasians.alertservice.repository.RptOutputSourceMapRepository;
import com.techasians.alertservice.service.ExcelOutputGenerator;
import com.techasians.alertservice.service.OutputGenerationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OutputGenerationCoordinatorTest {
    private RptOutputSourceMapRepository sourceMapRepository;
    private RptDefinitionRepository definitionRepository;
    private OutputReadinessServiceImpl readinessService;
    private ExcelOutputGenerator generator;
    private ReportOutputGenerationTransactionService transactionService;
    private OutputGenerationEventPublisher eventPublisher;
    private OutputGenerationCoordinator coordinator;

    @BeforeEach
    void setUp() {
        sourceMapRepository = mock(RptOutputSourceMapRepository.class);
        definitionRepository = mock(RptDefinitionRepository.class);
        readinessService = mock(OutputReadinessServiceImpl.class);
        generator = mock(ExcelOutputGenerator.class);
        transactionService = mock(ReportOutputGenerationTransactionService.class);
        eventPublisher = mock(OutputGenerationEventPublisher.class);
        coordinator = new OutputGenerationCoordinator(sourceMapRepository, definitionRepository,
                readinessService, generator, transactionService, eventPublisher);
    }

    @Test
    void doesNotPrepareWhenSourcesAreMissing() {
        LocalDate week = LocalDate.of(2026, 9, 21);
        when(sourceMapRepository.findActiveOutputDefinitionIdsBySourceDefinitionId(1101L))
                .thenReturn(Collections.singletonList(2002L));
        when(generator.supports(2002L)).thenReturn(true);
        when(readinessService.checkReadiness(2002L, week))
                .thenReturn(new OutputReadinessResult().setReadyToGenerate(false)
                        .setMissingSourceIds(Collections.singletonList(1102L)));

        coordinator.prepareAndPublish(1101L, week, "system");

        verify(transactionService, never()).prepare(any(), any(), any());
        verify(eventPublisher, never()).publishPrepared(any());
    }

    @Test
    void preparesAndPublishesOneEventForEachReadyOutput() {
        LocalDate week = LocalDate.of(2026, 9, 21);
        when(sourceMapRepository.findActiveOutputDefinitionIdsBySourceDefinitionId(1101L))
                .thenReturn(Arrays.asList(2002L, 2003L));
        when(generator.supports(any())).thenReturn(true);
        when(readinessService.checkReadiness(any(), eq(week)))
                .thenReturn(new OutputReadinessResult().setReadyToGenerate(true));
        when(transactionService.prepare(eq(2002L), eq(week), eq("system")))
                .thenReturn(new ReportOutputGenerationTransactionService.GenerationAllocation(21L, 1, false));
        when(transactionService.prepare(eq(2003L), eq(week), eq("system")))
                .thenReturn(new ReportOutputGenerationTransactionService.GenerationAllocation(22L, 2, true));

        coordinator.prepareAndPublish(1101L, week, "system");

        verify(eventPublisher).publishPrepared(argThat(event -> event.getOutputDefinitionId().equals(2002L)
                && event.getInstanceId().equals(21L) && !event.isRegeneration()));
        verify(eventPublisher).publishPrepared(argThat(event -> event.getOutputDefinitionId().equals(2003L)
                && event.getInstanceId().equals(22L) && event.isRegeneration()));
    }
}