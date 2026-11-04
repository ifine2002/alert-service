package com.techasians.alertservice.service;

import com.techasians.alertservice.event.OutputGenerationPreparedEvent;

public interface ReportOutputGenerationService {
    void execute(OutputGenerationPreparedEvent event);
}
