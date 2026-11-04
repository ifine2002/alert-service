package com.techasians.alertservice.entity;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Bảng rpt_excel_header_defs
 * Định nghĩa tên header bắt buộc trong một sheet Excel.
 * Liên kết với rpt_excel_sheet_defs (sheet_def_id).
 *
 * header_name phải xuất hiện nguyên tên trong một ô bất kỳ của sheet.
 * Không lưu tọa độ để cho phép dịch chuyển bảng và thay đổi thứ tự cột.
 */
@Entity
@Table(name = "rpt_excel_header_defs")
public class RptExcelHeaderDef implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Tham chiếu tới sheet chứa header này
    @Column(name = "sheet_def_id", nullable = false)
    private Long sheetDefId;

    // Tên header bắt buộc phải xuất hiện trong sheet
    @Column(name = "header_name", nullable = false, length = 500)
    private String headerName;

    // 1: bắt buộc validate, 0: bỏ qua
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

    public RptExcelHeaderDef setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getSheetDefId() {
        return sheetDefId;
    }

    public RptExcelHeaderDef setSheetDefId(Long sheetDefId) {
        this.sheetDefId = sheetDefId;
        return this;
    }

    public String getHeaderName() {
        return headerName;
    }

    public RptExcelHeaderDef setHeaderName(String headerName) {
        this.headerName = headerName;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public RptExcelHeaderDef setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public RptExcelHeaderDef setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public RptExcelHeaderDef setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }
}
