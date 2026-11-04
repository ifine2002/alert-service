package com.techasians.alertservice.dto.forecast;

import java.math.BigDecimal;

/**
 * Projection để load impact_rate của event theo channel từ traffic_event_channels.
 *
 * impactRate: giá trị lưu trong DB dạng 0-100 (ví dụ: 20% → 20).
 * Khi đưa vào ForecastInputData.impactRates cần chia 100.
 */
public interface EventImpactProjection {

    Integer getEventId();

    Integer getChannelId();

    // Giá trị 0-100 từ DB (chưa chia 100)
    BigDecimal getImpactRate();
}
