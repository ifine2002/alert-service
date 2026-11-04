package com.techasians.alertservice.entity;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity ánh xạ bảng service_quality_results.
 * Lưu trữ kết quả KPI hàng ngày theo từng cặp (metric, org).
 *
 * Trường type phân biệt loại dữ liệu:
 *   type = 1 → KPI thực tế (Call Center Outsourcing) - so sánh với ngưỡng
 *   type = 0 → Target KPI (mục tiêu tháng)           - dùng để quy đổi threshold
 *
 * Ví dụ nghiệp vụ:
 *   Ca 1 chạy lúc 08:00, hệ thống lấy bản ghi type=1 mới nhất
 *   từ 17:31 hôm qua đến 08:00 hôm nay để biết KPI thực tế.
 *   Sau đó lấy bản ghi type=0 mới nhất trong tháng để biết Target KPI.
 */
@Entity
@Table(name = "service_quality_results")
public class ServiceQualityResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "metric_org_id", nullable = false)
    private Integer metricOrgId;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "report_month", nullable = false)
    private Integer reportMonth;

    @Column(name = "report_year", nullable = false)
    private Integer reportYear;

    // Giá trị KPI (thực tế hoặc target tùy theo trường type)
    @Column(name = "total", nullable = false, precision = 18, scale = 4)
    private BigDecimal total = BigDecimal.ZERO;

    // 1 = Hợp lệ, 0 = Lỗi
    @Column(name = "status", nullable = false)
    private Integer status = 1;

    /**
     * Phân loại dữ liệu:
     *   1 = KPI thực tế (dùng để so sánh với ngưỡng)
     *   0 = Target KPI  (dùng để quy đổi threshold %)
     */
    @Column(name = "type", nullable = false)
    private Integer type = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy = "";

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy = "";

    // --- Getters and Fluent Setters ---

    public Long getId() {
        return id;
    }

    public Integer getMetricOrgId() {
        return metricOrgId;
    }

    public ServiceQualityResult setMetricOrgId(Integer metricOrgId) {
        this.metricOrgId = metricOrgId;
        return this;
    }

    public LocalDate getReportDate() {
        return reportDate;
    }

    public ServiceQualityResult setReportDate(LocalDate reportDate) {
        this.reportDate = reportDate;
        return this;
    }

    public Integer getReportMonth() {
        return reportMonth;
    }

    public ServiceQualityResult setReportMonth(Integer reportMonth) {
        this.reportMonth = reportMonth;
        return this;
    }

    public Integer getReportYear() {
        return reportYear;
    }

    public ServiceQualityResult setReportYear(Integer reportYear) {
        this.reportYear = reportYear;
        return this;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public ServiceQualityResult setTotal(BigDecimal total) {
        this.total = total;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public ServiceQualityResult setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public Integer getType() {
        return type;
    }

    public ServiceQualityResult setType(Integer type) {
        this.type = type;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
