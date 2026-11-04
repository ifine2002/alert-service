package com.techasians.alertservice.dto;

import com.techasians.alertservice.enums.RptFileKind;
import com.techasians.alertservice.enums.RptInstanceStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ReportOutputResponse {
    private Long definitionId;
    private String name;
    private RptFileKind fileKind;
    private String allowedExtension;
    private Long instanceId;
    private RptInstanceStatus status;
    private boolean readinessConfigured;
    private boolean readyToGenerate;
    private List<Long> missingSourceIds = new ArrayList<>();
    private String fileName;
    private LocalDateTime updatedAt;
    private Boolean generationComplete;
    private String generationWarnings;

    public Long getDefinitionId() { return definitionId; }
    public ReportOutputResponse setDefinitionId(Long value) { this.definitionId = value; return this; }
    public String getName() { return name; }
    public ReportOutputResponse setName(String value) { this.name = value; return this; }
    public RptFileKind getFileKind() { return fileKind; }
    public ReportOutputResponse setFileKind(RptFileKind value) { this.fileKind = value; return this; }
    public String getAllowedExtension() { return allowedExtension; }
    public ReportOutputResponse setAllowedExtension(String value) { this.allowedExtension = value; return this; }
    public Long getInstanceId() { return instanceId; }
    public ReportOutputResponse setInstanceId(Long value) { this.instanceId = value; return this; }
    public RptInstanceStatus getStatus() { return status; }
    public ReportOutputResponse setStatus(RptInstanceStatus value) { this.status = value; return this; }
    public boolean isReadinessConfigured() { return readinessConfigured; }
    public ReportOutputResponse setReadinessConfigured(boolean value) { this.readinessConfigured = value; return this; }
    public boolean isReadyToGenerate() { return readyToGenerate; }
    public ReportOutputResponse setReadyToGenerate(boolean value) { this.readyToGenerate = value; return this; }
    public List<Long> getMissingSourceIds() { return missingSourceIds; }
    public ReportOutputResponse setMissingSourceIds(List<Long> value) { this.missingSourceIds = value; return this; }
    public String getFileName() { return fileName; }
    public ReportOutputResponse setFileName(String value) { this.fileName = value; return this; }

    public Boolean getGenerationComplete() { return generationComplete; }
    public ReportOutputResponse setGenerationComplete(Boolean value) { this.generationComplete = value; return this; }
    public String getGenerationWarnings() { return generationWarnings; }
    public ReportOutputResponse setGenerationWarnings(String value) { this.generationWarnings = value; return this; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public ReportOutputResponse setUpdatedAt(LocalDateTime value) { this.updatedAt = value; return this; }
}
