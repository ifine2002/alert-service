package com.techasians.alertservice.dto.forecast;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dữ liệu thực tế (actual) của một ngày cho một region-channel, metric "Cuộc gọi".
 * Được load từ DB trước khi bắt đầu vòng forecast.
 */
public class TrafficActualData {

    private Long regionChannelId;
    private LocalDate trafficDate;

    // Giá trị metric "Cuộc gọi" - giữ nguyên precision từ DB
    private BigDecimal metricValue;

    // Ngày này có sự kiện không (từ traffic_histories.has_event)
    private boolean hasEvent;

    public TrafficActualData() {
    }

    public TrafficActualData(Long regionChannelId, LocalDate trafficDate,
                              BigDecimal metricValue, boolean hasEvent) {
        this.regionChannelId = regionChannelId;
        this.trafficDate = trafficDate;
        this.metricValue = metricValue;
        this.hasEvent = hasEvent;
    }

    public Long getRegionChannelId() {
        return regionChannelId;
    }

    public TrafficActualData setRegionChannelId(Long regionChannelId) {
        this.regionChannelId = regionChannelId;
        return this;
    }

    public LocalDate getTrafficDate() {
        return trafficDate;
    }

    public TrafficActualData setTrafficDate(LocalDate trafficDate) {
        this.trafficDate = trafficDate;
        return this;
    }

    public BigDecimal getMetricValue() {
        return metricValue;
    }

    public TrafficActualData setMetricValue(BigDecimal metricValue) {
        this.metricValue = metricValue;
        return this;
    }

    public boolean isHasEvent() {
        return hasEvent;
    }

    public TrafficActualData setHasEvent(boolean hasEvent) {
        this.hasEvent = hasEvent;
        return this;
    }
}
