package com.techasians.alertservice.dto;

import com.techasians.alertservice.enums.RptAuditAction;
import com.techasians.alertservice.enums.RptInstanceStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Kết quả trả về sau khi import hoặc re-import thành công.
 * Chứa đủ thông tin để frontend cập nhật lại trạng thái mà không cần gọi thêm API list.
 */
public class ReportImportResponse {

    // ID của rpt_instance được tạo hoặc đã tồn tại
    private Long instanceId;

    // ID của rpt_definitions tương ứng
    private Long definitionId;

    // Tuần báo cáo (Thứ Hai bắt đầu tuần)
    private LocalDate weekStartDate;

    // Trạng thái sau khi import thành công (luôn là COMPLETED)
    private RptInstanceStatus status;

    // Tên file gốc người dùng upload (dùng khi download)
    private String fileName;

    // Kích thước file tính bằng byte
    private Long fileSize;

    // SHA-256 của file để kiểm tra toàn vẹn
    private String sha256;

    // IMPORT nếu là lần đầu; REIMPORT nếu đã có file trước đó
    private RptAuditAction action;

    // Thời điểm import thành công
    private LocalDateTime updatedAt;

    public Long getInstanceId() {
        return instanceId;
    }

    public ReportImportResponse setInstanceId(Long instanceId) {
        this.instanceId = instanceId;
        return this;
    }

    public Long getDefinitionId() {
        return definitionId;
    }

    public ReportImportResponse setDefinitionId(Long definitionId) {
        this.definitionId = definitionId;
        return this;
    }

    public LocalDate getWeekStartDate() {
        return weekStartDate;
    }

    public ReportImportResponse setWeekStartDate(LocalDate weekStartDate) {
        this.weekStartDate = weekStartDate;
        return this;
    }

    public RptInstanceStatus getStatus() {
        return status;
    }

    public ReportImportResponse setStatus(RptInstanceStatus status) {
        this.status = status;
        return this;
    }

    public String getFileName() {
        return fileName;
    }

    public ReportImportResponse setFileName(String fileName) {
        this.fileName = fileName;
        return this;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public ReportImportResponse setFileSize(Long fileSize) {
        this.fileSize = fileSize;
        return this;
    }

    public String getSha256() {
        return sha256;
    }

    public ReportImportResponse setSha256(String sha256) {
        this.sha256 = sha256;
        return this;
    }

    public RptAuditAction getAction() {
        return action;
    }

    public ReportImportResponse setAction(RptAuditAction action) {
        this.action = action;
        return this;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public ReportImportResponse setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
}
