package com.techasians.alertservice.dto;

import com.techasians.alertservice.enums.RptAuditAction;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface ReportActionHistoryProjection {
    Long getDefinitionId();
    Long getInstanceId();
    String getDefinitionName();
    LocalDate getWeekStartDate();
    RptAuditAction getAction();
    String getActor();
    LocalDateTime getCreatedAt();
    String getReason();
    Long getFileId();
    String getFileName();
}
