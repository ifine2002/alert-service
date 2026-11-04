package com.techasians.alertservice.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * DTO dùng để nhận toàn bộ dữ liệu của 1 Cấu hình cảnh báo từ Frontend.
 * Dùng cho cả hai trường hợp: Thêm mới (Create) và Cập nhật (Update).
 */
public class AlertConfigRequest {

    // @NotBlank: không được để trống, không được chỉ có dấu cách
    // @Size: giới hạn độ dài tối đa 255 ký tự (giống DB)
    @NotBlank(message = "Tên cấu hình cảnh báo không được để trống")
    @Size(max = 255, message = "Tên cấu hình cảnh báo không được vượt quá 255 ký tự")
    private String name;

    // @NotNull: bắt buộc phải truyền lên, không được bỏ qua
    @NotNull(message = "Loại chỉ số không được để trống")
    private Integer categoryId;

    @NotNull(message = "Chỉ số chi tiết không được để trống")
    private Integer metricId;

    private Integer status;

    @NotEmpty(message = "Phải có ít nhất 1 mức cảnh báo")
    @Valid
    private List<AlertLevelRequest> levels;

    public String getName() {
        return name;
    }

    public AlertConfigRequest setName(String name) {
        this.name = name;
        return this;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public AlertConfigRequest setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
        return this;
    }

    public Integer getMetricId() {
        return metricId;
    }

    public AlertConfigRequest setMetricId(Integer metricId) {
        this.metricId = metricId;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public AlertConfigRequest setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public List<AlertLevelRequest> getLevels() {
        return levels;
    }

    public AlertConfigRequest setLevels(List<AlertLevelRequest> levels) {
        this.levels = levels;
        return this;
    }
}
