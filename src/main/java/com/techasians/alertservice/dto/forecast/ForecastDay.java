package com.techasians.alertservice.dto.forecast;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Đại diện dữ liệu lưu lượng của một ngày, dùng trong quá trình tính forecast.
 *
 * Quan trọng:
 * - value giữ full precision trong suốt quá trình tính (KHÔNG round trung gian).
 * - Chỉ round khi build response cuối cùng.
 * - hasEvent = true thì ngày này KHÔNG được dùng làm normal day baseline.
 */
public class ForecastDay {

    private LocalDate date;
    private BigDecimal value;
    private ForecastSource source;
    private boolean hasEvent;
    private List<Integer> eventIds;
    private BigDecimal baselineValue;
    private BigDecimal impactRate;
    private ForecastBranch branch;

    public ForecastDay() {
    }

    public ForecastDay(LocalDate date, BigDecimal value, ForecastSource source,
                       boolean hasEvent, List<Integer> eventIds) {
        this.date = date;
        this.value = value;
        this.source = source;
        this.hasEvent = hasEvent;
        this.eventIds = eventIds != null ? eventIds : new ArrayList<>();
    }

    public LocalDate getDate() {
        return date;
    }

    public ForecastDay setDate(LocalDate date) {
        this.date = date;
        return this;
    }

    public BigDecimal getValue() {
        return value;
    }

    public ForecastDay setValue(BigDecimal value) {
        this.value = value;
        return this;
    }

    public ForecastSource getSource() {
        return source;
    }

    public ForecastDay setSource(ForecastSource source) {
        this.source = source;
        return this;
    }

    public boolean isHasEvent() {
        return hasEvent;
    }

    public ForecastDay setHasEvent(boolean hasEvent) {
        this.hasEvent = hasEvent;
        return this;
    }

    public List<Integer> getEventIds() {
        return eventIds;
    }

    public ForecastDay setEventIds(List<Integer> eventIds) {
        this.eventIds = eventIds != null ? eventIds : new ArrayList<>();
        return this;
    }

    public BigDecimal getBaselineValue() {
        return baselineValue;
    }

    public ForecastDay setBaselineValue(BigDecimal baselineValue) {
        this.baselineValue = baselineValue;
        return this;
    }

    public BigDecimal getImpactRate() {
        return impactRate;
    }

    public ForecastDay setImpactRate(BigDecimal impactRate) {
        this.impactRate = impactRate;
        return this;
    }

    public ForecastBranch getBranch() {
        return branch;
    }

    public ForecastDay setBranch(ForecastBranch branch) {
        this.branch = branch;
        return this;
    }
}
