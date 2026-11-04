package com.techasians.alertservice.dto.forecast;

import java.math.BigDecimal;

/**
 * Kết quả tính impact của một event.
 *
 * fallbackUsed = true khi impact lấy từ occurrence nhiều event,
 * không phải occurrence đơn thuần của event đó.
 */
public class EventImpactResult {

    private final BigDecimal impactRate;
    private final boolean fallbackUsed;

    public EventImpactResult(BigDecimal impactRate, boolean fallbackUsed) {
        this.impactRate = impactRate;
        this.fallbackUsed = fallbackUsed;
    }

    public BigDecimal getImpactRate() {
        return impactRate;
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }
}