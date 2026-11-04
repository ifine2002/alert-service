package com.techasians.alertservice.dto;

import com.techasians.alertservice.enums.RptInstanceStatus;
import com.techasians.alertservice.enums.RptNodeType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ReportInputGroupResponse {
    private Long definitionId;
    private RptNodeType nodeType;
    private String reportName;
    private RptInstanceStatus status;
    private int completedFiles;
    private int totalFiles;
    private String updatedBy;
    private LocalDateTime updatedAt;
    private List<ReportInputFileResponse> files = new ArrayList<>();

    public Long getDefinitionId() { return definitionId; }
    public ReportInputGroupResponse setDefinitionId(Long value) { this.definitionId = value; return this; }
    public RptNodeType getNodeType() { return nodeType; }
    public ReportInputGroupResponse setNodeType(RptNodeType value) { this.nodeType = value; return this; }
    public String getReportName() { return reportName; }
    public ReportInputGroupResponse setReportName(String value) { this.reportName = value; return this; }
    public RptInstanceStatus getStatus() { return status; }
    public ReportInputGroupResponse setStatus(RptInstanceStatus value) { this.status = value; return this; }
    public int getCompletedFiles() { return completedFiles; }
    public ReportInputGroupResponse setCompletedFiles(int value) { this.completedFiles = value; return this; }
    public int getTotalFiles() { return totalFiles; }
    public ReportInputGroupResponse setTotalFiles(int value) { this.totalFiles = value; return this; }
    public String getUpdatedBy() { return updatedBy; }
    public ReportInputGroupResponse setUpdatedBy(String value) { this.updatedBy = value; return this; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public ReportInputGroupResponse setUpdatedAt(LocalDateTime value) { this.updatedAt = value; return this; }
    public List<ReportInputFileResponse> getFiles() { return files; }
    public ReportInputGroupResponse setFiles(List<ReportInputFileResponse> value) { this.files = value; return this; }
}
