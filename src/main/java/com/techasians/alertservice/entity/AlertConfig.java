package com.techasians.alertservice.entity;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name = "alert_configs")
public class AlertConfig extends BaseAuditEntity<AlertConfig> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_quality_metric_id", nullable = false)
    private ServiceQualityMetric serviceQualityMetric;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_quality_category_id", nullable = false)
    private ServiceQualityCategory serviceQualityCategory;

    @Column(name = "name", nullable = false)
    private String name = "";

    @Column(name = "status", nullable = false)
    private Integer status = 1;

    // Soft delete: null = chưa xóa, có giá trị = đã xóa
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by", length = 100)
    private String deletedBy;

    // Field boolean cho soft delete — dùng chung với deletedAt
    // Khi deleted=true: set cả deletedAt để tương thích với các query dùng JPQL/Spring Data
    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    public Integer getId() {
        return id;
    }

    public AlertConfig setId(Integer id) {
        this.id = id;
        return this;
    }

    public ServiceQualityMetric getServiceQualityMetric() {
        return serviceQualityMetric;
    }

    public AlertConfig setServiceQualityMetric(ServiceQualityMetric serviceQualityMetric) {
        this.serviceQualityMetric = serviceQualityMetric;
        return this;
    }

    public ServiceQualityCategory getServiceQualityCategory() {
        return serviceQualityCategory;
    }

    public AlertConfig setServiceQualityCategory(ServiceQualityCategory serviceQualityCategory) {
        this.serviceQualityCategory = serviceQualityCategory;
        return this;
    }

    public String getName() {
        return name;
    }

    public AlertConfig setName(String name) {
        this.name = name;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public AlertConfig setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public AlertConfig setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
        return this;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    public AlertConfig setDeletedBy(String deletedBy) {
        this.deletedBy = deletedBy;
        return this;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public AlertConfig setDeleted(boolean deleted) {
        this.deleted = deleted;
        return this;
    }
}
