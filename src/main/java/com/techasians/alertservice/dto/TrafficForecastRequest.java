package com.techasians.alertservice.dto;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * Request tính dự trù lưu lượng metric "Cuộc gọi".
 *
 * fromDate / toDate: khoảng thời gian cần dự trù (bắt buộc).
 * Validate: toDate <= fromDate.plusMonths(3) - tối đa 3 tháng, KHÔNG dùng 90 ngày cố định.
 *
 * regionIds / channelIds: bắt buộc có giá trị.
 * Backend join traffic_region_channels để resolve thành danh sách regionChannelId.
 */
public class TrafficForecastRequest {

    @NotNull(message = "Từ ngày không được để trống")
    private LocalDate fromDate;

    @NotNull(message = "Đến ngày không được để trống")
    private LocalDate toDate;

    @NotEmpty(message = "regionIds không được để trống")
    private List<Long> regionIds;

    @NotEmpty(message = "channelIds không được để trống")
    private List<Integer> channelIds;

    public LocalDate getFromDate() {
        return fromDate;
    }

    public TrafficForecastRequest setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
        return this;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public TrafficForecastRequest setToDate(LocalDate toDate) {
        this.toDate = toDate;
        return this;
    }

    public List<Long> getRegionIds() {
        return regionIds;
    }

    public TrafficForecastRequest setRegionIds(List<Long> regionIds) {
        this.regionIds = regionIds;
        return this;
    }

    public List<Integer> getChannelIds() {
        return channelIds;
    }

    public TrafficForecastRequest setChannelIds(List<Integer> channelIds) {
        this.channelIds = channelIds;
        return this;
    }
}
