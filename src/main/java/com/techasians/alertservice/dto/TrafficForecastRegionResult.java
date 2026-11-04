package com.techasians.alertservice.dto;

import java.util.List;

/**
 * Kết quả dự trù lưu lượng của một khu vực.
 *
 * Cấu trúc:
 *   - channels: danh sách kênh trong khu vực (metadata, chỉ có 1 lần)
 *   - days: danh sách ngày, mỗi ngày chứa kết quả của từng kênh theo regionChannelId
 *
 * FE dùng: days[i].channelResults[j].regionChannelId → tra trong channels → lấy channelName
 */
public class TrafficForecastRegionResult {

    /** ID khu vực */
    private Long regionId;

    /** Tên khu vực */
    private String regionName;

    /**
     * Danh sách metadata kênh thuộc khu vực này.
     * FE dùng để build header cột bảng và tra cứu tên kênh.
     */
    private List<TrafficForecastChannelMeta> channels;

    /**
     * Danh sách hàng theo ngày, mỗi hàng = 1 ngày.
     * Đã sort tăng dần theo date.
     */
    private List<TrafficForecastDayRow> days;

    public Long getRegionId() {
        return regionId;
    }

    public TrafficForecastRegionResult setRegionId(Long regionId) {
        this.regionId = regionId;
        return this;
    }

    public String getRegionName() {
        return regionName;
    }

    public TrafficForecastRegionResult setRegionName(String regionName) {
        this.regionName = regionName;
        return this;
    }

    public List<TrafficForecastChannelMeta> getChannels() {
        return channels;
    }

    public TrafficForecastRegionResult setChannels(List<TrafficForecastChannelMeta> channels) {
        this.channels = channels;
        return this;
    }

    public List<TrafficForecastDayRow> getDays() {
        return days;
    }

    public TrafficForecastRegionResult setDays(List<TrafficForecastDayRow> days) {
        this.days = days;
        return this;
    }
}
