package com.techasians.alertservice.dto;

import com.techasians.alertservice.enums.RptAuditAction;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class ReportActionHistoryResponse {
    private Long definitionId;
    private Long instanceId;
    private String definitionName;
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private RptAuditAction action;
    private String actor;
    private LocalDateTime createdAt;
    private String reason;
    private Long fileId;
    private String fileName;

    public Long getDefinitionId() { return definitionId; }
    public ReportActionHistoryResponse setDefinitionId(Long value) { this.definitionId = value; return this; }
    public Long getInstanceId() { return instanceId; }
    public ReportActionHistoryResponse setInstanceId(Long value) { this.instanceId = value; return this; }
    public String getDefinitionName() { return definitionName; }
    public ReportActionHistoryResponse setDefinitionName(String value) { this.definitionName = value; return this; }
    public LocalDate getWeekStartDate() { return weekStartDate; }
    public ReportActionHistoryResponse setWeekStartDate(LocalDate value) { this.weekStartDate = value; return this; }
    public LocalDate getWeekEndDate() { return weekEndDate; }
    public ReportActionHistoryResponse setWeekEndDate(LocalDate value) { this.weekEndDate = value; return this; }
    public RptAuditAction getAction() { return action; }
    public ReportActionHistoryResponse setAction(RptAuditAction value) { this.action = value; return this; }
    public String getActor() { return actor; }
    public ReportActionHistoryResponse setActor(String value) { this.actor = value; return this; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public ReportActionHistoryResponse setCreatedAt(LocalDateTime value) { this.createdAt = value; return this; }
    public String getReason() { return reason; }
    public ReportActionHistoryResponse setReason(String value) { this.reason = value; return this; }
    public Long getFileId() { return fileId; }
    public ReportActionHistoryResponse setFileId(Long value) { this.fileId = value; return this; }
    public String getFileName() { return fileName; }
    public ReportActionHistoryResponse setFileName(String value) { this.fileName = value; return this; }
}
