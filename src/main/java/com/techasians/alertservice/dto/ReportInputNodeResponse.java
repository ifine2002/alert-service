package com.techasians.alertservice.dto;

import com.techasians.alertservice.enums.RptFileKind;
import com.techasians.alertservice.enums.RptInstanceStatus;
import com.techasians.alertservice.enums.RptNodeType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ReportInputNodeResponse {
    private Long definitionId;
    private Long parentDefinitionId;
    private RptNodeType nodeType;
    private String reportName;
    private RptInstanceStatus status;
    private String updatedBy;
    private LocalDateTime updatedAt;
    private List<ReportInputNodeResponse> children = new ArrayList<>();
    private RptFileKind fileKind;
    private String allowedExtension;
    private Long instanceId;
    private Boolean rejected;
    private String importedFileName;
    private Integer completedFiles;
    private Integer totalFiles;

    public Long getDefinitionId() { return definitionId; }
    public ReportInputNodeResponse setDefinitionId(Long value) { this.definitionId = value; return this; }
    public Long getParentDefinitionId() { return parentDefinitionId; }
    public ReportInputNodeResponse setParentDefinitionId(Long value) { this.parentDefinitionId = value; return this; }
    public RptNodeType getNodeType() { return nodeType; }
    public ReportInputNodeResponse setNodeType(RptNodeType value) { this.nodeType = value; return this; }
    public String getReportName() { return reportName; }
    public ReportInputNodeResponse setReportName(String value) { this.reportName = value; return this; }
    public RptInstanceStatus getStatus() { return status; }
    public ReportInputNodeResponse setStatus(RptInstanceStatus value) { this.status = value; return this; }
    public String getUpdatedBy() { return updatedBy; }
    public ReportInputNodeResponse setUpdatedBy(String value) { this.updatedBy = value; return this; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public ReportInputNodeResponse setUpdatedAt(LocalDateTime value) { this.updatedAt = value; return this; }
    public List<ReportInputNodeResponse> getChildren() { return children; }
    public ReportInputNodeResponse setChildren(List<ReportInputNodeResponse> value) { this.children = value; return this; }
    public RptFileKind getFileKind() { return fileKind; }
    public ReportInputNodeResponse setFileKind(RptFileKind value) { this.fileKind = value; return this; }
    public String getAllowedExtension() { return allowedExtension; }
    public ReportInputNodeResponse setAllowedExtension(String value) { this.allowedExtension = value; return this; }
    public Long getInstanceId() { return instanceId; }
    public ReportInputNodeResponse setInstanceId(Long value) { this.instanceId = value; return this; }
    public Boolean getRejected() { return rejected; }
    public ReportInputNodeResponse setRejected(Boolean value) { this.rejected = value; return this; }
    public String getImportedFileName() { return importedFileName; }
    public ReportInputNodeResponse setImportedFileName(String value) { this.importedFileName = value; return this; }
    public Integer getCompletedFiles() { return completedFiles; }
    public ReportInputNodeResponse setCompletedFiles(Integer value) { this.completedFiles = value; return this; }
    public Integer getTotalFiles() { return totalFiles; }
    public ReportInputNodeResponse setTotalFiles(Integer value) { this.totalFiles = value; return this; }
}