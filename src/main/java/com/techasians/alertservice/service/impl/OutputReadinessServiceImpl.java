package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.dto.OutputReadinessResult;
import com.techasians.alertservice.entity.RptDefinition;
import com.techasians.alertservice.entity.RptInstance;
import com.techasians.alertservice.entity.RptOutputBlockDef;
import com.techasians.alertservice.entity.RptOutputSourceMap;
import com.techasians.alertservice.enums.RptDirection;
import com.techasians.alertservice.enums.RptInstanceStatus;
import com.techasians.alertservice.enums.RptNodeType;
import com.techasians.alertservice.enums.RptSourceMode;
import com.techasians.alertservice.exception.InvalidReportStateException;
import com.techasians.alertservice.exception.ResourceNotFoundException;
import com.techasians.alertservice.repository.RptDefinitionRepository;
import com.techasians.alertservice.repository.RptInstanceRepository;
import com.techasians.alertservice.repository.RptOutputBlockDefRepository;
import com.techasians.alertservice.repository.RptOutputSourceMapRepository;
import com.techasians.alertservice.service.OutputReadinessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class OutputReadinessServiceImpl implements OutputReadinessService {
    private final RptDefinitionRepository definitionRepository;
    private final RptOutputBlockDefRepository blockRepository;
    private final RptOutputSourceMapRepository sourceMapRepository;
    private final RptInstanceRepository instanceRepository;

    public OutputReadinessServiceImpl(RptDefinitionRepository definitionRepository,
                                      RptOutputBlockDefRepository blockRepository,
                                      RptOutputSourceMapRepository sourceMapRepository,
                                      RptInstanceRepository instanceRepository) {
        this.definitionRepository = definitionRepository;
        this.blockRepository = blockRepository;
        this.sourceMapRepository = sourceMapRepository;
        this.instanceRepository = instanceRepository;
    }

    @Override
    public OutputReadinessResult checkReadiness(Long outputDefinitionId, LocalDate weekStartDate) {
        RptDefinition output = definitionRepository.findById(outputDefinitionId)
                .orElseThrow(() -> new ResourceNotFoundException("Output definition không tồn tại: id=" + outputDefinitionId));
        validateOutput(output);

        List<RptOutputBlockDef> blocks = blockRepository
                .findByOutputDefinitionIdAndStatusOrderBySortOrder(outputDefinitionId, 1);
        List<Long> sourceDefinitionIds = loadDistinctSourceDefinitionIds(blocks);
        if (sourceDefinitionIds.isEmpty()) {
            return new OutputReadinessResult()
                    .setConfigured(false)
                    .setReadyToGenerate(false)
                    .setMissingSourceIds(new ArrayList<>());
        }

        Map<Long, RptInstanceStatus> statusByDefinitionId = new HashMap<>();
        for (RptInstance instance : instanceRepository
                .findByWeekStartDateAndDefinitionIdIn(weekStartDate, sourceDefinitionIds)) {
            statusByDefinitionId.put(instance.getDefinitionId(), instance.getStatus());
        }

        List<Long> missingSourceIds = new ArrayList<>();
        for (Long sourceDefinitionId : sourceDefinitionIds) {
            if (statusByDefinitionId.get(sourceDefinitionId) != RptInstanceStatus.COMPLETED) {
                missingSourceIds.add(sourceDefinitionId);
            }
        }
        return new OutputReadinessResult()
                .setConfigured(true)
                .setReadyToGenerate(Long.valueOf(2001L).equals(outputDefinitionId) || missingSourceIds.isEmpty())
                .setMissingSourceIds(missingSourceIds);
    }

    @Override
    public Map<Long, OutputReadinessResult> checkReadinessBatch(
            List<RptDefinition> outputDefinitions, LocalDate weekStartDate) {
        Map<Long, OutputReadinessResult> resultByOutputId = new HashMap<>();
        if (outputDefinitions == null || outputDefinitions.isEmpty()) {
            return resultByOutputId;
        }

        List<Long> outputDefinitionIds = new ArrayList<>();
        for (RptDefinition outputDefinition : outputDefinitions) {
            validateOutput(outputDefinition);
            outputDefinitionIds.add(outputDefinition.getId());
            resultByOutputId.put(outputDefinition.getId(), notConfiguredResult());
        }

        Map<Long, Set<Long>> sourceIdsByOutputId = new HashMap<>();
        List<Object[]> outputSourcePairs = sourceMapRepository
                .findActiveSourceDefinitionIdsByOutputDefinitionIds(outputDefinitionIds);
        for (Object[] outputSourcePair : outputSourcePairs) {
            Long outputDefinitionId = (Long) outputSourcePair[0];
            Long sourceDefinitionId = (Long) outputSourcePair[1];
            sourceIdsByOutputId
                    .computeIfAbsent(outputDefinitionId, key -> new HashSet<>())
                    .add(sourceDefinitionId);
        }


        Set<Long> allSourceDefinitionIds = new HashSet<>();
        for (Set<Long> sourceDefinitionIds : sourceIdsByOutputId.values()) {
            allSourceDefinitionIds.addAll(sourceDefinitionIds);
        }

        Map<Long, RptInstanceStatus> statusByDefinitionId = new HashMap<>();
        if (!allSourceDefinitionIds.isEmpty()) {
            List<Long> sourceDefinitionIds = new ArrayList<>(allSourceDefinitionIds);
            for (RptInstance instance : instanceRepository
                    .findByWeekStartDateAndDefinitionIdIn(weekStartDate, sourceDefinitionIds)) {
                statusByDefinitionId.put(instance.getDefinitionId(), instance.getStatus());
            }
        }

        for (Long outputDefinitionId : outputDefinitionIds) {
            Set<Long> sourceDefinitionIds = sourceIdsByOutputId.get(outputDefinitionId);
            if (sourceDefinitionIds == null || sourceDefinitionIds.isEmpty()) {
                continue;
            }

            List<Long> missingSourceIds = new ArrayList<>();
            for (Long sourceDefinitionId : sourceDefinitionIds) {
                if (statusByDefinitionId.get(sourceDefinitionId) != RptInstanceStatus.COMPLETED) {
                    missingSourceIds.add(sourceDefinitionId);
                }
            }
            resultByOutputId.put(outputDefinitionId, new OutputReadinessResult()
                    .setConfigured(true)
                    .setReadyToGenerate(Long.valueOf(2001L).equals(outputDefinitionId) || missingSourceIds.isEmpty())
                    .setMissingSourceIds(missingSourceIds));
        }
        return resultByOutputId;
    }

    private OutputReadinessResult notConfiguredResult() {
        return new OutputReadinessResult()
                .setConfigured(false)
                .setReadyToGenerate(false)
                .setMissingSourceIds(new ArrayList<>());
    }

    private void validateOutput(RptDefinition output) {
        boolean valid = output.isActive()
                && output.getDirection() == RptDirection.OUTPUT
                && output.getNodeType() == RptNodeType.FILE
                && output.getSourceMode() == RptSourceMode.GENERATE;
        if (!valid) {
            throw new InvalidReportStateException("Definition không phải output generate đang hoạt động: id=" + output.getId());
        }
    }

    private List<Long> loadDistinctSourceDefinitionIds(List<RptOutputBlockDef> blocks) {
        List<Long> sourceDefinitionIds = new ArrayList<>();
        for (RptOutputBlockDef block : blocks) {
            List<RptOutputSourceMap> sourceMaps = sourceMapRepository
                    .findByOutputBlockIdAndStatusOrderBySortOrder(block.getId(), 1);
            for (RptOutputSourceMap sourceMap : sourceMaps) {
                if (!sourceDefinitionIds.contains(sourceMap.getSourceDefinitionId())) {
                    sourceDefinitionIds.add(sourceMap.getSourceDefinitionId());
                }
            }
        }
        return sourceDefinitionIds;
    }
}
