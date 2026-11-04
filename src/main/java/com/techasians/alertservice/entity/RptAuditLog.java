package com.techasians.alertservice.entity;

import com.techasians.alertservice.enums.RptAuditAction;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "rpt_audit_logs")
public class RptAuditLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "week_start_date")
    private LocalDate weekStartDate;

    @Column(name = "report_instance_id")
    private Long reportInstanceId;

    @Column(name = "file_id")
    private Long fileId;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "action", nullable = false)
    @Enumerated(EnumType.ORDINAL)
    private RptAuditAction action;

    @Column(name = "actor", nullable = false, length = 100)
    private String actor = "";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (actor == null) actor = "";
    }

    public Long getId() { return id; }
    public RptAuditLog setId(Long value) { this.id = value; return this; }
    public LocalDate getWeekStartDate() { return weekStartDate; }
    public RptAuditLog setWeekStartDate(LocalDate value) { this.weekStartDate = value; return this; }
    public Long getReportInstanceId() { return reportInstanceId; }
    public RptAuditLog setReportInstanceId(Long value) { this.reportInstanceId = value; return this; }
    public Long getFileId() { return fileId; }
    public RptAuditLog setFileId(Long value) { this.fileId = value; return this; }
    public String getReason() { return reason; }
    public RptAuditLog setReason(String value) { this.reason = value; return this; }
    public RptAuditAction getAction() { return action; }
    public RptAuditLog setAction(RptAuditAction value) { this.action = value; return this; }
    public String getActor() { return actor; }
    public RptAuditLog setActor(String value) { this.actor = value; return this; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public RptAuditLog setCreatedAt(LocalDateTime value) { this.createdAt = value; return this; }
}
