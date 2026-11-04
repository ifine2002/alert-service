package com.techasians.alertservice.dto;

import java.math.BigDecimal;
import java.util.List;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/**
 * DTO dùng để nhận dữ liệu của từng mức cảnh báo từ Frontend gửi lên.
 * Ví dụ: Mức 1 - Nhỏ hơn 90%, nhận SMS và Email, đầu mối nhận là nhóm A.
 */
public class AlertLevelRequest {

    // Mức cảnh báo: chỉ được nhận giá trị 1, 2, hoặc 3
    @NotNull(message = "Mức cảnh báo không được để trống")
    @Min(value = 1, message = "Mức cảnh báo tối thiểu là 1")
    @Max(value = 3, message = "Mức cảnh báo tối đa là 3")
    private Integer levelNumber;

    // Phép so sánh: chỉ nhận giá trị từ 1 đến 6
    // 1=Nhỏ hơn, 2=Nhỏ hơn hoặc bằng, 3=Lớn hơn,
    // 4=Lớn hơn hoặc bằng, 5=Bằng, 6=Trong khoảng
    @NotNull(message = "Phép so sánh không được để trống")
    @Min(value = 1, message = "Phép so sánh không hợp lệ")
    @Max(value = 6, message = "Phép so sánh không hợp lệ")
    private Integer conditionOperator;

    // Giá trị ngưỡng thứ nhất (VD: 90 -> nghĩa là 90%)
    // @DecimalMin: giá trị phải >= 0
    @NotNull(message = "Giá trị ngưỡng không được để trống")
    @DecimalMin(value = "0", message = "Giá trị ngưỡng phải lớn hơn hoặc bằng 0")
    private BigDecimal thresholdValue;

    // Giá trị ngưỡng thứ hai, chỉ dùng khi phép so sánh là "Trong khoảng"
    // Không bắt buộc (nullable) nên chỉ validate khi có giá trị
    @DecimalMin(value = "0", message = "Giá trị ngưỡng thứ hai phải lớn hơn hoặc bằng 0")
    private BigDecimal thresholdValueTo;

    // Danh sách ID kênh nhận (VD: [1, 2] -> ID của SMS và Email)
    // Bắt buộc, người dùng phải chọn ít nhất 1 kênh
    @NotEmpty(message = "Kênh nhận cảnh báo bắt buộc chọn")
    private List<@NotNull(message = "Kênh nhận cảnh báo không hợp lệ") Integer> channelIds;

    // Danh sách ID nhóm đầu mối nhận (VD: [3, 5] -> ID các Alert Group)
    // Bắt buộc, người dùng phải chọn ít nhất 1 nhóm
    @NotEmpty(message = "Mã đầu mối bắt buộc chọn")
    private List<@NotNull(message = "Mã đầu mối không hợp lệ") Integer> groupIds;

    public Integer getLevelNumber() {
        return levelNumber;
    }

    public AlertLevelRequest setLevelNumber(Integer levelNumber) {
        this.levelNumber = levelNumber;
        return this;
    }

    public Integer getConditionOperator() {
        return conditionOperator;
    }

    public AlertLevelRequest setConditionOperator(Integer conditionOperator) {
        this.conditionOperator = conditionOperator;
        return this;
    }

    public BigDecimal getThresholdValue() {
        return thresholdValue;
    }

    public AlertLevelRequest setThresholdValue(BigDecimal thresholdValue) {
        this.thresholdValue = thresholdValue;
        return this;
    }

    public BigDecimal getThresholdValueTo() {
        return thresholdValueTo;
    }

    public AlertLevelRequest setThresholdValueTo(BigDecimal thresholdValueTo) {
        this.thresholdValueTo = thresholdValueTo;
        return this;
    }

    public List<Integer> getChannelIds() {
        return channelIds;
    }

    public AlertLevelRequest setChannelIds(List<Integer> channelIds) {
        this.channelIds = channelIds;
        return this;
    }

    public List<Integer> getGroupIds() {
        return groupIds;
    }

    public AlertLevelRequest setGroupIds(List<Integer> groupIds) {
        this.groupIds = groupIds;
        return this;
    }
}
