package com.techasians.alertservice.entity;

import com.techasians.alertservice.enums.RptInstanceStatus;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Bảng rpt_instance
 * Instance báo cáo theo tuần – mỗi (definition, week_start_date) có 1 instance riêng.
 * Được lazy-create khi có thao tác thực tế (import, generate...).
 * Kế thừa BaseAuditEntity vì bảng có đủ created_at/by và updated_at/by.
 */
@Entity
@Table(name = "rpt_instances")
public class RptInstance extends BaseAuditEntity<RptInstance> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ID definition (bảng rpt_definitions) – file con, không phải nhóm cha
    @Column(name = "definition_id", nullable = false)
    private Long definitionId;

    // Ngày Thứ Hai bắt đầu tuần báo cáo; ngày kết thúc = +6 ngày
    @Column(name = "week_start_date", nullable = false)
    private java.time.LocalDate weekStartDate;

    // Trạng thái: 0: EMPTY, 1: PROCESSING, 2: COMPLETED, 3: REJECTED, 4: ERROR, 5: GENERATING, 6: STALE
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.ORDINAL)
    private RptInstanceStatus status = RptInstanceStatus.EMPTY;

    // Lý do từ chối (khi status = REJECTED)
    @Column(name = "rejected_reason", columnDefinition = "TEXT")
    private String rejectedReason;

    // Người từ chối
    @Column(name = "rejected_by", nullable = false, length = 100)
    private String rejectedBy = "";

    // Thời điểm từ chối
    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    // Phiên bản file hiện đang được sử dụng.
    @Column(name = "current_file_id")
    private Long currentFileId;

    public Long getId() {
        return id;
    }

    public RptInstance setId(Long id) {
        this.id = id;
        return this;
    }

    public Long getDefinitionId() {
        return definitionId;
    }

    public RptInstance setDefinitionId(Long definitionId) {
        this.definitionId = definitionId;
        return this;
    }

    public java.time.LocalDate getWeekStartDate() {
        return weekStartDate;
    }

    public RptInstance setWeekStartDate(java.time.LocalDate weekStartDate) {
        this.weekStartDate = weekStartDate;
        return this;
    }

    public RptInstanceStatus getStatus() {
        return status;
    }

    public RptInstance setStatus(RptInstanceStatus status) {
        this.status = status;
        return this;
    }

    public String getRejectedReason() {
        return rejectedReason;
    }

    public RptInstance setRejectedReason(String rejectedReason) {
        this.rejectedReason = rejectedReason;
        return this;
    }

    public String getRejectedBy() {
        return rejectedBy;
    }

    public RptInstance setRejectedBy(String rejectedBy) {
        this.rejectedBy = rejectedBy;
        return this;
    }

    public LocalDateTime getRejectedAt() {
        return rejectedAt;
    }

    public RptInstance setRejectedAt(LocalDateTime rejectedAt) {
        this.rejectedAt = rejectedAt;
        return this;
    }

    public Long getCurrentFileId() {
        return currentFileId;
    }

    public RptInstance setCurrentFileId(Long currentFileId) {
        this.currentFileId = currentFileId;
        return this;
    }

    // Helper: instance đã hoàn thành chưa
    public boolean isCompleted() {
        return status == RptInstanceStatus.COMPLETED;
    }

    // Helper: instance có bị từ chối không
    public boolean isRejected() {
        return status == RptInstanceStatus.REJECTED;
    }

    // Helper: instance đang trong trạng thái lỗi không
    public boolean isError() {
        return status == RptInstanceStatus.ERROR;
    }
}
