package com.techasians.alertservice.dto;

import com.techasians.alertservice.enums.RptFileKind;
import com.techasians.alertservice.enums.RptInstanceStatus;
import com.techasians.alertservice.enums.RptNodeType;
import java.time.LocalDateTime;

public class ReportInputFileResponse {
    private Long definitionId;
    private Long groupDefinitionId;
    private RptNodeType nodeType;
    private String reportName;
    private RptFileKind fileKind;
    private String allowedExtension;
    private Long instanceId;
    private RptInstanceStatus status;
    private boolean rejected;
    private String importedFileName;
    private String updatedBy;
    private LocalDateTime updatedAt;

    public Long getDefinitionId() { return definitionId; }
    public ReportInputFileResponse setDefinitionId(Long value) { this.definitionId = value; return this; }
    public Long getGroupDefinitionId() { return groupDefinitionId; }
    public ReportInputFileResponse setGroupDefinitionId(Long value) { this.groupDefinitionId = value; return this; }
    public RptNodeType getNodeType() { return nodeType; }
    public ReportInputFileResponse setNodeType(RptNodeType value) { this.nodeType = value; return this; }
    public String getReportName() { return reportName; }
    public ReportInputFileResponse setReportName(String value) { this.reportName = value; return this; }
    public RptFileKind getFileKind() { return fileKind; }
    public ReportInputFileResponse setFileKind(RptFileKind value) { this.fileKind = value; return this; }
    public String getAllowedExtension() { return allowedExtension; }
    public ReportInputFileResponse setAllowedExtension(String value) { this.allowedExtension = value; return this; }
    public Long getInstanceId() { return instanceId; }
    public ReportInputFileResponse setInstanceId(Long value) { this.instanceId = value; return this; }
    public RptInstanceStatus getStatus() { return status; }
    public ReportInputFileResponse setStatus(RptInstanceStatus value) { this.status = value; return this; }
    public boolean isRejected() { return rejected; }
    public ReportInputFileResponse setRejected(boolean value) { this.rejected = value; return this; }
    public String getImportedFileName() { return importedFileName; }
    public ReportInputFileResponse setImportedFileName(String value) { this.importedFileName = value; return this; }
    public String getUpdatedBy() { return updatedBy; }
    public ReportInputFileResponse setUpdatedBy(String value) { this.updatedBy = value; return this; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public ReportInputFileResponse setUpdatedAt(LocalDateTime value) { this.updatedAt = value; return this; }
}
