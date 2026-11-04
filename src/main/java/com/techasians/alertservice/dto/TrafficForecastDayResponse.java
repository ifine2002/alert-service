package com.techasians.alertservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Response chứa kết quả dự trù lưu lượng cho một ngày.
 *
 * value đã round scale=0, HALF_UP.
 */
public class TrafficForecastDayResponse {

    private LocalDate date;
    private BigDecimal value;
    private String source;
    private boolean hasEvent;
    private List<String> eventNames;
    private BigDecimal impactRate;

    public LocalDate getDate() {
        return date;
    }

    public TrafficForecastDayResponse setDate(LocalDate date) {
        this.date = date;
        return this;
    }

    public BigDecimal getValue() {
        return value;
    }

    public TrafficForecastDayResponse setValue(BigDecimal value) {
        this.value = value;
        return this;
    }

    public String getSource() {
        return source;
    }

    public TrafficForecastDayResponse setSource(String source) {
        this.source = source;
        return this;
    }

    public boolean isHasEvent() {
        return hasEvent;
    }

    public TrafficForecastDayResponse setHasEvent(boolean hasEvent) {
        this.hasEvent = hasEvent;
        return this;
    }

    public List<String> getEventNames() {
        return eventNames;
    }

    public TrafficForecastDayResponse setEventNames(List<String> eventNames) {
        this.eventNames = eventNames;
        return this;
    }

    public BigDecimal getImpactRate() {
        return impactRate;
    }

    public TrafficForecastDayResponse setImpactRate(BigDecimal impactRate) {
        this.impactRate = impactRate;
        return this;
    }
}
