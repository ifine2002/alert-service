package com.techasians.alertservice.entity;

import javax.persistence.*;
import java.io.Serializable;

/** Một phiên bản bất biến của file báo cáo. */
@Entity
@Table(name = "rpt_files")
public class RptFile extends BaseAuditEntity<RptFile> implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_instance_id", nullable = false)
    private Long reportInstanceId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "original_filename", nullable = false, length = 500)
    private String originalFilename;

    @Column(name = "storage_object_name", nullable = false, length = 1000)
    private String storageObjectName;

    @Column(name = "file_size", nullable = false)
    private Long fileSize = 0L;

    @Column(name = "sha256", nullable = false, length = 64,
            columnDefinition = "CHAR(64) CHARACTER SET ascii COLLATE ascii_general_ci")
    private String sha256;

    @Column(name = "generation_complete")
    private Boolean generationComplete;

    @Column(name = "generation_warnings", columnDefinition = "TEXT")
    private String generationWarnings;

    public Boolean getGenerationComplete() { return generationComplete; }
    public RptFile setGenerationComplete(Boolean value) { generationComplete = value; return this; }
    public String getGenerationWarnings() { return generationWarnings; }
    public RptFile setGenerationWarnings(String value) { generationWarnings = value; return this; }

    public Long getId() { return id; }
    public RptFile setId(Long id) { this.id = id; return this; }
    public Long getReportInstanceId() { return reportInstanceId; }
    public RptFile setReportInstanceId(Long value) { this.reportInstanceId = value; return this; }
    public Integer getVersionNo() { return versionNo; }
    public RptFile setVersionNo(Integer value) { this.versionNo = value; return this; }
    public String getOriginalFilename() { return originalFilename; }
    public RptFile setOriginalFilename(String value) { this.originalFilename = value; return this; }
    public String getStorageObjectName() { return storageObjectName; }
    public RptFile setStorageObjectName(String value) { this.storageObjectName = value; return this; }
    public Long getFileSize() { return fileSize; }
    public RptFile setFileSize(Long value) { this.fileSize = value; return this; }
    public String getSha256() { return sha256; }
    public RptFile setSha256(String value) { this.sha256 = value; return this; }
}
