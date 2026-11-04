package com.techasians.alertservice.service;

import com.techasians.alertservice.event.OutputGenerationPreparedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class OutputGenerationEventPublisher {
    private final ApplicationEventPublisher applicationEventPublisher;

    public OutputGenerationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publishPrepared(OutputGenerationPreparedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
