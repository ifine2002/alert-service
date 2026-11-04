package com.techasians.alertservice.event;

import com.techasians.alertservice.service.ReportOutputGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class OutputGenerationEventListener {
    private static final Logger log = LoggerFactory.getLogger(OutputGenerationEventListener.class);
    private final ReportOutputGenerationService generationService;

    public OutputGenerationEventListener(ReportOutputGenerationService generationService) {
        this.generationService = generationService;
    }

    @EventListener
    @Async
    public void onGenerationPrepared(OutputGenerationPreparedEvent event) {
        log.info("Bắt đầu generate output {} cho tuần {}, instanceId={}",
                event.getOutputDefinitionId(), event.getWeekStartDate(), event.getInstanceId());
        try {
            generationService.execute(event);
            log.info("Hoàn tất generate output {}, instanceId={}",
                    event.getOutputDefinitionId(), event.getInstanceId());
        } catch (RuntimeException exception) {
            log.error("Generate output {} thất bại, instanceId={}: {}",
                    event.getOutputDefinitionId(), event.getInstanceId(), exception.getMessage(), exception);
        }
    }
}
