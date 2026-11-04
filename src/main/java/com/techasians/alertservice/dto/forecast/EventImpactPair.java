package com.techasians.alertservice.dto.forecast;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cặp dữ liệu dùng để tính impact thực tế của một occurrence event.
 */
public class EventImpactPair {

    private LocalDate occurrenceDate;
    private BigDecimal occurrenceValue;
    private BigDecimal baselineValue;
    private boolean singleEventOnly;

    public LocalDate getOccurrenceDate() {
        return occurrenceDate;
    }

    public EventImpactPair setOccurrenceDate(LocalDate occurrenceDate) {
        this.occurrenceDate = occurrenceDate;
        return this;
    }

    public BigDecimal getOccurrenceValue() {
        return occurrenceValue;
    }

    public EventImpactPair setOccurrenceValue(BigDecimal occurrenceValue) {
        this.occurrenceValue = occurrenceValue;
        return this;
    }

    public BigDecimal getBaselineValue() {
        return baselineValue;
    }

    public EventImpactPair setBaselineValue(BigDecimal baselineValue) {
        this.baselineValue = baselineValue;
        return this;
    }

    public boolean isSingleEventOnly() {
        return singleEventOnly;
    }

    public EventImpactPair setSingleEventOnly(boolean singleEventOnly) {
        this.singleEventOnly = singleEventOnly;
        return this;
    }
}
