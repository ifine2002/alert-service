package com.techasians.alertservice.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity ánh xạ bảng service_quality_metric_orgs.
 * Mỗi bản ghi đại diện cho một cặp (chỉ số, đơn vị) đang được theo dõi.
 *
 * Ví dụ thực tế:
 *   metric = "Tỷ lệ kết nối (CCR)"
 *   org    = "VTT-Di động"
 *   → Hệ thống sẽ theo dõi chỉ số CCR tại kênh VTT-Di động.
 */
@Entity
@Table(
    name = "service_quality_metric_orgs",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_sq_metric_org", columnNames = {"org_id", "metric_id"})
    }
)
public class ServiceQualityMetricOrg {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "org_id", nullable = false)
    private Integer orgId;

    @Column(name = "metric_id", nullable = false)
    private Integer metricId;

    // 1 = Hoạt động, 0 = Khóa
    @Column(name = "status", nullable = false)
    private Integer status = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy = "";

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    // --- Getters and Fluent Setters ---

    public Integer getId() {
        return id;
    }

    public ServiceQualityMetricOrg setId(Integer id) {
        this.id = id;
        return this;
    }

    public Integer getOrgId(){
        return orgId;
    }

    public ServiceQualityMetricOrg setOrgId(Integer orgId) {
        this.orgId = orgId;
        return this;
    }

    public ServiceQualityMetricOrg setMetricId(Integer metricId) {
        this.metricId = metricId;
        return this;
    }

    public Integer getMetricId(){
        return metricId;
    }

    public Integer getStatus() {
        return status;
    }

    public ServiceQualityMetricOrg setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public ServiceQualityMetricOrg setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }
}
