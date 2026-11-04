package com.techasians.alertservice.dto;

import java.time.LocalDateTime;

public class ReportFileVersionResponse {
    private Long fileId;
    private Integer versionNo;
    private String originalFilename;
    private Long fileSize;
    private String sha256;
    private LocalDateTime createdAt;
    private String createdBy;
    private boolean current;

    public Long getFileId() { return fileId; }
    public ReportFileVersionResponse setFileId(Long value) { this.fileId = value; return this; }
    public Integer getVersionNo() { return versionNo; }
    public ReportFileVersionResponse setVersionNo(Integer value) { this.versionNo = value; return this; }
    public String getOriginalFilename() { return originalFilename; }
    public ReportFileVersionResponse setOriginalFilename(String value) { this.originalFilename = value; return this; }
    public Long getFileSize() { return fileSize; }
    public ReportFileVersionResponse setFileSize(Long value) { this.fileSize = value; return this; }
    public String getSha256() { return sha256; }
    public ReportFileVersionResponse setSha256(String value) { this.sha256 = value; return this; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public ReportFileVersionResponse setCreatedAt(LocalDateTime value) { this.createdAt = value; return this; }
    public String getCreatedBy() { return createdBy; }
    public ReportFileVersionResponse setCreatedBy(String value) { this.createdBy = value; return this; }
    public boolean isCurrent() { return current; }
    public ReportFileVersionResponse setCurrent(boolean value) { this.current = value; return this; }
}
