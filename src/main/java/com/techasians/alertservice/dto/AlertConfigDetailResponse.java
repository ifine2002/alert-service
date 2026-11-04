package com.techasians.alertservice.dto;

import java.util.List;

/**
 * DTO dùng để trả dữ liệu chi tiết của 1 Cấu hình cảnh báo xuống Frontend.
 * Dùng trong các API: Thêm mới, Sửa, Lấy chi tiết.
 */
public class AlertConfigDetailResponse {

    private Integer id;
    private String name;
    private Integer categoryId;
    private String categoryName;
    private Integer metricId;
    private String metricName;
    private Integer status;
    private List<AlertLevelResponse> levels;

    // ---- Getters & Setters ----

    public Integer getId() {
        return id;
    }

    public AlertConfigDetailResponse setId(Integer id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public AlertConfigDetailResponse setName(String name) {
        this.name = name;
        return this;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public AlertConfigDetailResponse setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
        return this;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public AlertConfigDetailResponse setCategoryName(String categoryName) {
        this.categoryName = categoryName;
        return this;
    }

    public Integer getMetricId() {
        return metricId;
    }

    public AlertConfigDetailResponse setMetricId(Integer metricId) {
        this.metricId = metricId;
        return this;
    }

    public String getMetricName() {
        return metricName;
    }

    public AlertConfigDetailResponse setMetricName(String metricName) {
        this.metricName = metricName;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public AlertConfigDetailResponse setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public List<AlertLevelResponse> getLevels() {
        return levels;
    }

    public AlertConfigDetailResponse setLevels(List<AlertLevelResponse> levels) {
        this.levels = levels;
        return this;
    }
}
