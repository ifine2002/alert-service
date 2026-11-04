package com.techasians.alertservice.entity;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Bảng rpt_excel_sheet_defs
 * Định nghĩa các sheet Excel thuộc một definition.
 * Dùng để validate file Excel khi user import:
 * - Kiểm tra sheet có tồn tại không.
 */
@Entity
@Table(name = "rpt_excel_sheet_defs")
public class RptExcelSheetDef implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ID của definition (bảng rpt_definitions)
    @Column(name = "definition_id", nullable = false)
    private Long definitionId;


    // Tên sheet thực tế trong file Excel
    @Column(name = "sheet_name", nullable = false, length = 255)
    private String sheetName;


    // Thứ tự cấu hình/hiển thị
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

    public RptExcelSheetDef setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getDefinitionId() {
        return definitionId;
    }

    public RptExcelSheetDef setDefinitionId(Long definitionId) {
        this.definitionId = definitionId;
        return this;
    }


    public String getSheetName() {
        return sheetName;
    }

    public RptExcelSheetDef setSheetName(String sheetName) {
        this.sheetName = sheetName;
        return this;
    }


    public Integer getSortOrder() {
        return sortOrder;
    }

    public RptExcelSheetDef setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public RptExcelSheetDef setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public RptExcelSheetDef setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public RptExcelSheetDef setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }

}
