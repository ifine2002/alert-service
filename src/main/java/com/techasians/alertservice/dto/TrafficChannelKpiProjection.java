package com.techasians.alertservice.dto;

import java.math.BigDecimal;

public interface TrafficChannelKpiProjection {

    Integer getId();

    Integer getChannelId();

    BigDecimal getKpiRate();

    BigDecimal getProductivityValue();

    String getChannelName();

    java.time.LocalDateTime getCreatedAt();

    String getCreatedBy();

    java.time.LocalDateTime getUpdatedAt();

    String getUpdatedBy();
}
