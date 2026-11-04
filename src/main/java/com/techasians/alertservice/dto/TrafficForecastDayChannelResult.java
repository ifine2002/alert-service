package com.techasians.alertservice.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả dự trù lưu lượng của một kênh trong một ngày cụ thể.
 *
 * Chỉ chứa regionChannelId để FE tra cứu metadata kênh từ danh sách channels của region.
 * Không lặp lại channelName, regionName để giảm kích thước response.
 */
public class TrafficForecastDayChannelResult {

    /** ID region_channel, dùng để FE khớp với metadata trong region.channels */
    private Long regionChannelId;

    /**
     * Danh sách tên sự kiện diễn ra trong ngày và có mapping với kênh hiện tại
     * trong bảng traffic_event_channels.
     */
    private List<String> events = new ArrayList<>();

    /**
     * % Tăng giảm lưu lượng so với baseline, đã nhân 100.
     * Ví dụ: 4.5 nghĩa là +4.5%, -1.2 nghĩa là -1.2%.
     * null nếu ngày không có event tác động.
     */
    private BigDecimal impactRate;

    /**
     * Lưu lượng dự kiến (số cuộc gọi), đã round scale=0.
     * null nếu không có dữ liệu.
     */
    private BigDecimal value;

    /**
     * Quân số dự kiến = CEILING(value × kpiRate ÷ productivityValue).
     * null nếu kênh này chưa cấu hình KPI trong bảng traffic_channel_kpis.
     * 0 nếu value = null hoặc kết quả tính toán bằng 0.
     */
    private Integer personnelCount;

    public Long getRegionChannelId() {
        return regionChannelId;
    }

    public TrafficForecastDayChannelResult setRegionChannelId(Long regionChannelId) {
        this.regionChannelId = regionChannelId;
        return this;
    }

    public List<String> getEvents() {
        return events;
    }

    public TrafficForecastDayChannelResult setEvents(List<String> events) {
        this.events = events != null ? events : new ArrayList<>();
        return this;
    }

    public BigDecimal getImpactRate() {
        return impactRate;
    }

    public TrafficForecastDayChannelResult setImpactRate(BigDecimal impactRate) {
        this.impactRate = impactRate;
        return this;
    }

    public BigDecimal getValue() {
        return value;
    }

    public TrafficForecastDayChannelResult setValue(BigDecimal value) {
        this.value = value;
        return this;
    }

    public Integer getPersonnelCount() {
        return personnelCount;
    }

    public TrafficForecastDayChannelResult setPersonnelCount(Integer personnelCount) {
        this.personnelCount = personnelCount;
        return this;
    }
}
