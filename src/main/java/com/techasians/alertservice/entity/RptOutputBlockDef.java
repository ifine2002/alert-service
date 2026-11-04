package com.techasians.alertservice.entity;

import com.techasians.alertservice.enums.RptRenderType;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Bảng rpt_output_block_defs
 * Định nghĩa các block/section/sheet cấu thành file output.
 * Ví dụ: block "INCIDENT" ghi vào bookmark "INCIDENT_BLOCK" trong template Word VCX.
 */
@Entity
@Table(name = "rpt_output_block_defs")
public class RptOutputBlockDef implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ID definition của file output chứa block này (bảng rpt_definitions)
    @Column(name = "output_definition_id", nullable = false)
    private Long outputDefinitionId;

    // Mã block duy nhất trong output, ví dụ: INCIDENT, SOCIAL, INCIDENT_WEEK
    @Column(name = "block_code", nullable = false, length = 100)
    private String blockCode;

    // Tên nghiệp vụ của block
    @Column(name = "block_name", nullable = false, length = 255)
    private String blockName;

    // Vi tri dich trong output: bookmark neu Word, ten sheet neu Excel
    @Column(name = "target_name", length = 255)
    private String targetName;

    // 0: COPY_SECTION, 1: CUSTOM_HANDLER
    @Column(name = "render_type", nullable = false)
    @Enumerated(EnumType.ORDINAL)
    private RptRenderType renderType = RptRenderType.COPY_SECTION;

    // Thứ tự xử lý block trong file output
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

    public RptOutputBlockDef setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getOutputDefinitionId() {
        return outputDefinitionId;
    }

    public RptOutputBlockDef setOutputDefinitionId(Long outputDefinitionId) {
        this.outputDefinitionId = outputDefinitionId;
        return this;
    }

    public String getBlockCode() {
        return blockCode;
    }

    public RptOutputBlockDef setBlockCode(String blockCode) {
        this.blockCode = blockCode;
        return this;
    }

    public String getBlockName() {
        return blockName;
    }

    public RptOutputBlockDef setBlockName(String blockName) {
        this.blockName = blockName;
        return this;
    }

    public String getTargetName() {
        return targetName;
    }

    public RptOutputBlockDef setTargetName(String targetName) {
        this.targetName = targetName;
        return this;
    }

    public RptRenderType getRenderType() {
        return renderType;
    }

    public RptOutputBlockDef setRenderType(RptRenderType renderType) {
        this.renderType = renderType;
        return this;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public RptOutputBlockDef setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public RptOutputBlockDef setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public RptOutputBlockDef setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public RptOutputBlockDef setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    // Helper: block có dùng custom handler không
    public boolean isCustomHandler() {
        return renderType == RptRenderType.CUSTOM_HANDLER;
    }
}
