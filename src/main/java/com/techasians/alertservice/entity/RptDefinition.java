package com.techasians.alertservice.entity;

import com.techasians.alertservice.enums.RptDirection;
import com.techasians.alertservice.enums.RptFileKind;
import com.techasians.alertservice.enums.RptNodeType;
import com.techasians.alertservice.enums.RptSourceMode;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Bảng rpt_definitions
 * Master định nghĩa nhóm báo cáo, file input và file output.
 * Ví dụ: "Báo cáo Lỗi Phát Sinh Tuần" (nhóm), "File Word Lỗi Phát Sinh" (file thực tế)
 */
@Entity
@Table(name = "rpt_definitions")
public class RptDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Tên hiển thị trên giao diện
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    // 0: input, 1: output
    @Column(name = "direction", nullable = false)
    @Enumerated(EnumType.ORDINAL)
    private RptDirection direction = RptDirection.INPUT;

    // 0: group (nhóm báo cáo), 1: file input/output thực tế
    @Column(name = "node_type", nullable = false)
    @Enumerated(EnumType.ORDINAL)
    private RptNodeType nodeType = RptNodeType.GROUP;

    // 0: import (user upload), 1: generate (hệ thống sinh)
    @Column(name = "source_mode", nullable = false)
    @Enumerated(EnumType.ORDINAL)
    private RptSourceMode sourceMode = RptSourceMode.IMPORT;

    // 0: NONE, 1: WORD, 2: EXCEL
    @Column(name = "file_kind", nullable = false)
    @Enumerated(EnumType.ORDINAL)
    private RptFileKind fileKind = RptFileKind.NONE;

    // Extension cho phép, ví dụ: docx, xlsx
    @Column(name = "allowed_extension", nullable = false, length = 20)
    private String allowedExtension = "";

    // Đường dẫn tương đối tới template (dùng cho output GENERATE)
    @Column(name = "template_relative_path", length = 1000)
    private String templateRelativePath;

    // Thứ tự hiển thị
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    // 1: đang sử dụng, 0: không sử dụng
    @Column(name = "status", nullable = false)
    private Integer status = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy = "";

    @PrePersist
    protected void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.createdBy == null) {
            this.createdBy = "";
        }
    }

    public Long getId() {
        return id;
    }

    public RptDefinition setId(Long id) {
        this.id = id;
        return this;
    }


    public String getName() {
        return name;
    }

    public RptDefinition setName(String name) {
        this.name = name;
        return this;
    }

    public RptDirection getDirection() {
        return direction;
    }

    public RptDefinition setDirection(RptDirection direction) {
        this.direction = direction;
        return this;
    }

    public RptNodeType getNodeType() {
        return nodeType;
    }

    public RptDefinition setNodeType(RptNodeType nodeType) {
        this.nodeType = nodeType;
        return this;
    }

    public RptSourceMode getSourceMode() {
        return sourceMode;
    }

    public RptDefinition setSourceMode(RptSourceMode sourceMode) {
        this.sourceMode = sourceMode;
        return this;
    }

    public RptFileKind getFileKind() {
        return fileKind;
    }

    public RptDefinition setFileKind(RptFileKind fileKind) {
        this.fileKind = fileKind;
        return this;
    }

    public String getAllowedExtension() {
        return allowedExtension;
    }

    public RptDefinition setAllowedExtension(String allowedExtension) {
        this.allowedExtension = allowedExtension;
        return this;
    }

    public String getTemplateRelativePath() {
        return templateRelativePath;
    }

    public RptDefinition setTemplateRelativePath(String templateRelativePath) {
        this.templateRelativePath = templateRelativePath;
        return this;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public RptDefinition setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public RptDefinition setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public RptDefinition setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public RptDefinition setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    // Helper: kiểm tra có phải file input không
    public boolean isInput() {
        return direction == RptDirection.INPUT;
    }

    // Helper: kiểm tra có phải nhóm không
    public boolean isGroup() {
        return nodeType == RptNodeType.GROUP;
    }

    // Helper: kiểm tra đang hoạt động
    public boolean isActive() {
        return Integer.valueOf(1).equals(status);
    }
}
