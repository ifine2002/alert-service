package com.techasians.alertservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO đại diện cho 1 vi phạm KPI được phát hiện sau khi kiểm tra.
 *
 * Ví dụ:
 *   Ca 1 chạy lúc 08:00, phát hiện chỉ số "Tỷ lệ kết nối" (CCR) tại kênh "VTT-Di động"
 *   thực tế chỉ đạt 82%, trong khi target là 90%, threshold là 5%.
 *   → comparisonValue = 90 × 0.95 = 85.5 → 82 < 85.5 → Vi phạm mức 1.
 */
public class AlertViolationDto {

    // ID của cặp (metric, org) bị vi phạm → dùng để truy vấn người nhận
    private Integer metricOrgId;

    // Tên chỉ số KPI, VD: "Tỷ lệ kết nối thành công (CCR)"
    private String metricName;

    // Tên kênh / đơn vị vận hành, VD: "VTT-Di động"
    private String orgName;

    // ID của kênh / đơn vị vận hành
    private Integer orgId;

    // KPI thực tế [X] — lấy từ service_quality_results.total (type=1, bản ghi mới nhất)
    private BigDecimal actualValue;

    // Target KPI [Z] — lấy từ service_quality_results.total (type=0, bản ghi mới nhất trong tháng)
    private BigDecimal targetKpiValue;

    // Độ lệch [Y] = |targetKpiValue - actualValue|
    private BigDecimal deviationValue;

    // Mức vi phạm cao nhất bị kích hoạt: 1, 2 hoặc 3
    private Integer violatedLevelNumber;

    // ID của alert_level bị vi phạm → dùng để lấy danh sách người nhận (alert_level_recipients)
    private Integer violatedLevelId;

    // Tên ca: "Ca 1", "Ca 2", "Ca 3", hoặc "Thủ công" khi gọi qua API
    private String shiftName;

    // Thời điểm hệ thống thực hiện kiểm tra — dùng cho template [HH:mm dd/MM/yyyy]
    private LocalDateTime checkTime;

    private Integer metricId;
    private Integer categoryId;
    private String categoryName;
    private Integer parentCategoryId;

    // --- Getters and Fluent Setters ---

    public Integer getMetricOrgId() {
        return metricOrgId;
    }

    public AlertViolationDto setMetricOrgId(Integer metricOrgId) {
        this.metricOrgId = metricOrgId;
        return this;
    }

    public String getMetricName() {
        return metricName;
    }

    public AlertViolationDto setMetricName(String metricName) {
        this.metricName = metricName;
        return this;
    }

    public String getOrgName() {
        return orgName;
    }

    public AlertViolationDto setOrgName(String orgName) {
        this.orgName = orgName;
        return this;
    }

    public Integer getOrgId() {
        return orgId;
    }

    public AlertViolationDto setOrgId(Integer orgId) {
        this.orgId = orgId;
        return this;
    }

    public BigDecimal getActualValue() {
        return actualValue;
    }

    public AlertViolationDto setActualValue(BigDecimal actualValue) {
        this.actualValue = actualValue;
        return this;
    }

    public BigDecimal getTargetKpiValue() {
        return targetKpiValue;
    }

    public AlertViolationDto setTargetKpiValue(BigDecimal targetKpiValue) {
        this.targetKpiValue = targetKpiValue;
        return this;
    }

    public BigDecimal getDeviationValue() {
        return deviationValue;
    }

    public AlertViolationDto setDeviationValue(BigDecimal deviationValue) {
        this.deviationValue = deviationValue;
        return this;
    }

    public Integer getViolatedLevelNumber() {
        return violatedLevelNumber;
    }

    public AlertViolationDto setViolatedLevelNumber(Integer violatedLevelNumber) {
        this.violatedLevelNumber = violatedLevelNumber;
        return this;
    }

    public Integer getViolatedLevelId() {
        return violatedLevelId;
    }

    public AlertViolationDto setViolatedLevelId(Integer violatedLevelId) {
        this.violatedLevelId = violatedLevelId;
        return this;
    }

    public String getShiftName() {
        return shiftName;
    }

    public AlertViolationDto setShiftName(String shiftName) {
        this.shiftName = shiftName;
        return this;
    }

    public LocalDateTime getCheckTime() {
        return checkTime;
    }

    public AlertViolationDto setCheckTime(LocalDateTime checkTime) {
        this.checkTime = checkTime;
        return this;
    }

    public Integer getMetricId() { return metricId; }
    public AlertViolationDto setMetricId(Integer metricId) { this.metricId = metricId; return this; }

    public Integer getCategoryId() { return categoryId; }
    public AlertViolationDto setCategoryId(Integer categoryId) { this.categoryId = categoryId; return this; }

    public String getCategoryName() { return categoryName; }
    public AlertViolationDto setCategoryName(String categoryName) { this.categoryName = categoryName; return this; }

    public Integer getParentCategoryId() { return parentCategoryId; }
    public AlertViolationDto setParentCategoryId(Integer parentCategoryId) { this.parentCategoryId = parentCategoryId; return this; }
}
