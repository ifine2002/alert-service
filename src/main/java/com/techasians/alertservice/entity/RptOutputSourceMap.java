package com.techasians.alertservice.entity;

import com.techasians.alertservice.enums.RptSourceRole;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Bảng rpt_output_source_maps
 * Mapping từng block output tới file input nguồn, sheet Excel hoặc section Word cụ thể.
 * Một block có thể có nhiều source với sort_order khác nhau.
 */
@Entity
@Table(name = "rpt_output_source_maps")
public class RptOutputSourceMap implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ID block output sử dụng source này (bảng rpt_output_block_defs)
    @Column(name = "output_block_id", nullable = false)
    private Long outputBlockId;

    // ID definition file input nguồn (bảng rpt_definitions)
    @Column(name = "source_definition_id", nullable = false)
    private Long sourceDefinitionId;

    // Vai trò của source: 0: TEXT, 1: DATA, 2: CHART, 3: CHART_ACCUMULATED
    @Column(name = "source_role", nullable = false)
    @Enumerated(EnumType.ORDINAL)
    private RptSourceRole sourceRole = RptSourceRole.TEXT;

    // Tên sheet Excel nguồn cần đọc; NULL nếu handler dùng toàn bộ file hoặc source là Word
    @Column(name = "source_sheet_name", length = 255)
    private String sourceSheetName;

    // ID section Word nguồn (bảng rpt_section_defs); NULL nếu không đọc section cụ thể
    @Column(name = "source_section_def_id")
    private Long sourceSectionDefId;


    // Thứ tự source khi block có nhiều source (unique trong cùng 1 block)
    @Column(name = "sort_order", nullable = false)
    // CUSTOM_HANDLER: thứ tự có thể xác định chức năng nguồn; xem documents/report-phase5-word-source-maps.sql.
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

    public RptOutputSourceMap setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getOutputBlockId() {
        return outputBlockId;
    }

    public RptOutputSourceMap setOutputBlockId(Long outputBlockId) {
        this.outputBlockId = outputBlockId;
        return this;
    }

    public Long getSourceDefinitionId() {
        return sourceDefinitionId;
    }

    public RptOutputSourceMap setSourceDefinitionId(Long sourceDefinitionId) {
        this.sourceDefinitionId = sourceDefinitionId;
        return this;
    }

    public RptSourceRole getSourceRole() {
        return sourceRole;
    }

    public RptOutputSourceMap setSourceRole(RptSourceRole sourceRole) {
        this.sourceRole = sourceRole;
        return this;
    }

    public String getSourceSheetName() {
        return sourceSheetName;
    }

    public RptOutputSourceMap setSourceSheetName(String sourceSheetName) {
        this.sourceSheetName = sourceSheetName;
        return this;
    }

    public Long getSourceSectionDefId() {
        return sourceSectionDefId;
    }

    public RptOutputSourceMap setSourceSectionDefId(Long sourceSectionDefId) {
        this.sourceSectionDefId = sourceSectionDefId;
        return this;
    }


    public Integer getSortOrder() {
        return sortOrder;
    }

    public RptOutputSourceMap setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public RptOutputSourceMap setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public RptOutputSourceMap setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public RptOutputSourceMap setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }

}
