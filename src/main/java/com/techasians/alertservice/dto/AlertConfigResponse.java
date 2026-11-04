package com.techasians.alertservice.dto;

/**
 * DTO dùng để trả dữ liệu chi tiết của 1 Cấu hình cảnh báo xuống Frontend.
 * Dùng trong các API: Thêm mới, Sửa, Lấy chi tiết.
 */
public class AlertConfigResponse {

    private Integer id;
    private String name;
    private Integer categoryId;
    private String categoryName;
    private Integer metricId;
    private String metricName;
    private Integer status;
    private Integer type;
    private String createdBy;

    // ---- Getters & Setters ----

    public Integer getId() {
        return id;
    }

    public AlertConfigResponse setId(Integer id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public AlertConfigResponse setName(String name) {
        this.name = name;
        return this;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public AlertConfigResponse setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
        return this;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public AlertConfigResponse setCategoryName(String categoryName) {
        this.categoryName = categoryName;
        return this;
    }

    public Integer getMetricId() {
        return metricId;
    }

    public AlertConfigResponse setMetricId(Integer metricId) {
        this.metricId = metricId;
        return this;
    }

    public String getMetricName() {
        return metricName;
    }

    public AlertConfigResponse setMetricName(String metricName) {
        this.metricName = metricName;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public AlertConfigResponse setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public Integer getType() {
        return type;
    }

    public AlertConfigResponse setType(Integer type) {
        this.type = type;
        return this;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public AlertConfigResponse setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }
}
