package com.techasians.alertservice.dto.forecast;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Projection để load actual traffic metric "Cuộc gọi" từ DB.
 * Tránh lazy loading và N+1 khi dùng trực tiếp entity.
 *
 * Dùng trong query JOIN:
 *   traffic_histories → traffic_history_metric_values → traffic_channel_metrics → traffic_metrics
 */
public interface TrafficActualProjection {

    Long getRegionChannelId();

    LocalDate getTrafficDate();

    BigDecimal getMetricValue();

    // 0 = không có event, 1 = có event (từ traffic_histories.has_event)
    Integer getHasEvent();
}
