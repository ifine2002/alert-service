package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.dto.OutputReadinessResult;
import com.techasians.alertservice.entity.RptDefinition;
import com.techasians.alertservice.enums.RptDirection;
import com.techasians.alertservice.enums.RptNodeType;
import com.techasians.alertservice.enums.RptSourceMode;
import com.techasians.alertservice.event.OutputGenerationPreparedEvent;
import com.techasians.alertservice.exception.InvalidReportStateException;
import com.techasians.alertservice.repository.RptDefinitionRepository;
import com.techasians.alertservice.repository.RptOutputSourceMapRepository;
import com.techasians.alertservice.service.ExcelOutputGenerator;
import com.techasians.alertservice.service.OutputGenerationEventPublisher;
import com.techasians.alertservice.service.OutputReadinessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OutputGenerationCoordinator {
    private static final Logger log = LoggerFactory.getLogger(OutputGenerationCoordinator.class);

    private final RptOutputSourceMapRepository sourceMapRepository;
    private final RptDefinitionRepository definitionRepository;
    private final OutputReadinessService readinessService;
    private final ExcelOutputGenerator generator;
    private final ReportOutputGenerationTransactionService transactionService;
    private final OutputGenerationEventPublisher eventPublisher;

    public OutputGenerationCoordinator(RptOutputSourceMapRepository sourceMapRepository,
                                       RptDefinitionRepository definitionRepository,
                                       OutputReadinessService readinessService,
                                       ExcelOutputGenerator generator,
                                       ReportOutputGenerationTransactionService transactionService,
                                       OutputGenerationEventPublisher eventPublisher) {
        this.sourceMapRepository = sourceMapRepository;
        this.definitionRepository = definitionRepository;
        this.readinessService = readinessService;
        this.generator = generator;
        this.transactionService = transactionService;
        this.eventPublisher = eventPublisher;
    }

    public void prepareAndPublish(Long inputDefinitionId, LocalDate weekStartDate, String actor) {
        long coordinatorStart = System.nanoTime();
        long lookupStart = System.nanoTime();
        List<Long> outputDefinitionIds = sourceMapRepository
                .findActiveOutputDefinitionIdsBySourceDefinitionId(inputDefinitionId);
        long dependencyLookupMs = elapsed(lookupStart);

        for (Long outputDefinitionId : outputDefinitionIds) {
            prepareAndPublishOne(outputDefinitionId, weekStartDate, actor);
        }

        log.info("[output-coordinator] Hoàn tất — inputDefinitionId={}, weekStartDate={}, "
                        + "dependencyLookupMs={}, coordinatorTotalMs={}",
                inputDefinitionId, weekStartDate, dependencyLookupMs, elapsed(coordinatorStart));
    }

    public void prepareAndPublishOutputs(List<Long> requestedDefinitionIds, LocalDate weekStartDate,
                                         String actor) {
        List<RptDefinition> outputDefinitions = resolveOutputDefinitions(requestedDefinitionIds);
        List<RptDefinition> supportedOutputDefinitions =
                validateAndGetSupportedOutputs(outputDefinitions, weekStartDate);

        for (RptDefinition outputDefinition : supportedOutputDefinitions) {
            prepareAndPublishReadyOutput(outputDefinition.getId(), weekStartDate, actor);
        }

        log.info("[output-coordinator] Đã tiếp nhận batch — outputDefinitionIds={}, weekStartDate={}",
                toDefinitionIds(supportedOutputDefinitions), weekStartDate);
    }

    private List<RptDefinition> resolveOutputDefinitions(List<Long> requestedDefinitionIds) {
        if (requestedDefinitionIds == null || requestedDefinitionIds.isEmpty()) {
            return definitionRepository
                    .findByDirectionAndNodeTypeAndSourceModeAndStatusOrderBySortOrderAscIdAsc(
                            RptDirection.OUTPUT, RptNodeType.FILE, RptSourceMode.GENERATE, 1);
        }

        Set<Long> uniqueDefinitionIds = new LinkedHashSet<>();
        for (Long definitionId : requestedDefinitionIds) {
            if (definitionId == null) {
                throw new IllegalArgumentException("definitionIds không được chứa giá trị null");
            }
            uniqueDefinitionIds.add(definitionId);
        }

        Map<Long, RptDefinition> definitionById = new HashMap<>();
        for (RptDefinition definition : definitionRepository.findAllById(uniqueDefinitionIds)) {
            definitionById.put(definition.getId(), definition);
        }

        List<Long> missingDefinitionIds = new ArrayList<>();
        List<RptDefinition> outputDefinitions = new ArrayList<>();
        for (Long definitionId : uniqueDefinitionIds) {
            RptDefinition definition = definitionById.get(definitionId);
            if (definition == null) {
                missingDefinitionIds.add(definitionId);
            } else {
                outputDefinitions.add(definition);
            }
        }
        if (!missingDefinitionIds.isEmpty()) {
            throw new InvalidReportStateException("Output definition không tồn tại: " + missingDefinitionIds);
        }
        return outputDefinitions;
    }

    private List<RptDefinition> validateAndGetSupportedOutputs(
            List<RptDefinition> outputDefinitions, LocalDate weekStartDate) {
        if (outputDefinitions.isEmpty()) {
            throw new InvalidReportStateException("Không có output nào để generate");
        }

        Map<Long, OutputReadinessResult> readinessByOutputId =
                readinessService.checkReadinessBatch(outputDefinitions, weekStartDate);
        List<RptDefinition> supportedOutputDefinitions = new ArrayList<>();

        for (RptDefinition outputDefinition : outputDefinitions) {
            if (!Long.valueOf(2001L).equals(outputDefinition.getId()) && !generator.supports(outputDefinition.getId())) {
                log.info("Bỏ qua output chưa được hỗ trợ: {}", outputDefinition.getId());
                continue;
            }

            OutputReadinessResult readiness = readinessByOutputId.get(outputDefinition.getId());
            if (readiness == null || !readiness.isReadyToGenerate()) {
                List<Long> missingSourceIds = readiness == null
                        ? new ArrayList<>() : readiness.getMissingSourceIds();
                log.info("Bỏ qua output {} cho tuần {} vì chưa đủ input: {}",
                        outputDefinition.getId(), weekStartDate, missingSourceIds);
                continue;
            }
            supportedOutputDefinitions.add(outputDefinition);
        }
        return supportedOutputDefinitions;
    }

    private List<Long> toDefinitionIds(List<RptDefinition> definitions) {
        List<Long> definitionIds = new ArrayList<>();
        for (RptDefinition definition : definitions) {
            definitionIds.add(definition.getId());
        }
        return definitionIds;
    }

    private void prepareAndPublishOne(Long outputDefinitionId, LocalDate weekStartDate, String actor) {
        try {
            if (!Long.valueOf(2001L).equals(outputDefinitionId) && !generator.supports(outputDefinitionId)) {
                log.info("Bỏ qua output chưa được hỗ trợ: {}", outputDefinitionId);
                return;
            }

            long readinessStart = System.nanoTime();
            OutputReadinessResult readiness = readinessService.checkReadiness(outputDefinitionId, weekStartDate);
            long readinessMs = elapsed(readinessStart);
            if (!readiness.isReadyToGenerate()) {
                log.info("Bỏ qua output {} cho tuần {} vì chưa đủ input: {}, readinessMs={}",
                        outputDefinitionId, weekStartDate, readiness.getMissingSourceIds(), readinessMs);
                return;
            }
            prepareAndPublishReadyOutput(outputDefinitionId, weekStartDate, actor);
        } catch (InvalidReportStateException exception) {
            log.info("Bỏ qua output {} cho tuần {}: {}", outputDefinitionId, weekStartDate,
                    exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("Không thể chuẩn bị output {} cho tuần {}", outputDefinitionId,
                    weekStartDate, exception);
        }
    }

    private void prepareAndPublishReadyOutput(Long outputDefinitionId, LocalDate weekStartDate,
                                              String actor) {
        long prepareStart = System.nanoTime();
        ReportOutputGenerationTransactionService.GenerationAllocation allocation =
                transactionService.prepare(outputDefinitionId, weekStartDate, actor);
        long outputPrepareMs = elapsed(prepareStart);

        OutputGenerationPreparedEvent event = new OutputGenerationPreparedEvent(
                outputDefinitionId, weekStartDate, allocation.getInstanceId(),
                allocation.getVersionNo(), allocation.isRegeneration(), actor);
        long publishStart = System.nanoTime();
        try {
            eventPublisher.publishPrepared(event);
        } catch (TaskRejectedException exception) {
            transactionService.markError(allocation.getInstanceId(), actor);
            throw exception;
        }
        log.info("[output-coordinator] Đã prepare — outputDefinitionId={}, instanceId={}, "
                        + "outputPrepareMs={}, eventPublishMs={}",
                outputDefinitionId, allocation.getInstanceId(), outputPrepareMs, elapsed(publishStart));
    }

    private static long elapsed(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000;
    }
}
