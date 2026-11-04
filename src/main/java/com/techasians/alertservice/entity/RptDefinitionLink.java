package com.techasians.alertservice.entity;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Bảng rpt_definition_links
 * Quan hệ cha-con giữa các definition.
 * Ví dụ: nhóm "Báo cáo Lỗi Phát Sinh Tuần" (cha) chứa "File Word" và "File Excel" (con).
 */
@Entity
@Table(name = "rpt_definition_links")
public class RptDefinitionLink implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ID definition nhóm cha (bảng rpt_definitions, node_type = 1)
    @Column(name = "parent_definition_id", nullable = false)
    private Long parentDefinitionId;

    // ID definition file con (bảng rpt_definitions, node_type = 2)
    @Column(name = "child_definition_id", nullable = false)
    private Long childDefinitionId;


    // Thứ tự hiển thị các file con trong nhóm
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

    public RptDefinitionLink setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getParentDefinitionId() {
        return parentDefinitionId;
    }

    public RptDefinitionLink setParentDefinitionId(Long parentDefinitionId) {
        this.parentDefinitionId = parentDefinitionId;
        return this;
    }

    public Long getChildDefinitionId() {
        return childDefinitionId;
    }

    public RptDefinitionLink setChildDefinitionId(Long childDefinitionId) {
        this.childDefinitionId = childDefinitionId;
        return this;
    }


    public Integer getSortOrder() {
        return sortOrder;
    }

    public RptDefinitionLink setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public RptDefinitionLink setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public RptDefinitionLink setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public RptDefinitionLink setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }

}
