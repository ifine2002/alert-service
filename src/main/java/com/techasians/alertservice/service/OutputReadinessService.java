package com.techasians.alertservice.service;

import com.techasians.alertservice.dto.OutputReadinessResult;
import com.techasians.alertservice.entity.RptDefinition;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface OutputReadinessService {
    OutputReadinessResult checkReadiness(Long outputDefinitionId, LocalDate weekStartDate);

    Map<Long, OutputReadinessResult> checkReadinessBatch(
            List<RptDefinition> outputDefinitions, LocalDate weekStartDate);
}
