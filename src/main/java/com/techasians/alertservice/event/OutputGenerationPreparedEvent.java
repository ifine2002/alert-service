package com.techasians.alertservice.event;

import java.time.LocalDate;

/**
 * Sự kiện được phát sau khi output đã được chuẩn bị và trạng thái GENERATING đã commit.
 */
public class OutputGenerationPreparedEvent {
    private final Long outputDefinitionId;
    private final LocalDate weekStartDate;
    private final Long instanceId;
    private final Integer versionNo;
    private final boolean regeneration;
    private final String actor;

    public OutputGenerationPreparedEvent(Long outputDefinitionId,
                                         LocalDate weekStartDate,
                                         Long instanceId,
                                         Integer versionNo,
                                         boolean regeneration,
                                         String actor) {
        this.outputDefinitionId = outputDefinitionId;
        this.weekStartDate = weekStartDate;
        this.instanceId = instanceId;
        this.versionNo = versionNo;
        this.regeneration = regeneration;
        this.actor = actor;
    }

    public Long getOutputDefinitionId() { return outputDefinitionId; }
    public LocalDate getWeekStartDate() { return weekStartDate; }
    public Long getInstanceId() { return instanceId; }
    public Integer getVersionNo() { return versionNo; }
    public boolean isRegeneration() { return regeneration; }
    public String getActor() { return actor; }
}
