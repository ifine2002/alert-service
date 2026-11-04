package com.techasians.alertservice.dto;

import com.techasians.alertservice.enums.RptDirection;
import com.techasians.alertservice.enums.RptInstanceStatus;
import java.time.LocalDateTime;

public class ReportDocumentSummaryResponse {
    private Long definitionId;
    private String name;
    private RptDirection direction;
    private RptInstanceStatus status;
    private LocalDateTime updatedAt;

    public Long getDefinitionId() { return definitionId; }
    public ReportDocumentSummaryResponse setDefinitionId(Long value) { this.definitionId = value; return this; }
    public String getName() { return name; }
    public ReportDocumentSummaryResponse setName(String value) { this.name = value; return this; }
    public RptDirection getDirection() { return direction; }
    public ReportDocumentSummaryResponse setDirection(RptDirection value) { this.direction = value; return this; }
    public RptInstanceStatus getStatus() { return status; }
    public ReportDocumentSummaryResponse setStatus(RptInstanceStatus value) { this.status = value; return this; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public ReportDocumentSummaryResponse setUpdatedAt(LocalDateTime value) { this.updatedAt = value; return this; }
}
