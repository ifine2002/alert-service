package com.techasians.alertservice.dto;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Request DTO cho 1 kênh trong event.
 * impact_rate bắt buộc có giá trị nếu event_type=2 (phát sinh) — validate trong service.
 */
public class TrafficEventChannelRequest {

    @NotNull(message = "channel_id không được để trống")
    private Integer channelId;

    // NULL nếu định kỳ, bắt buộc NOT NULL nếu phát sinh
    private BigDecimal impactRate;

    public Integer getChannelId() {
        return channelId;
    }

    public TrafficEventChannelRequest setChannelId(Integer channelId) {
        this.channelId = channelId;
        return this;
    }

    public BigDecimal getImpactRate() {
        return impactRate;
    }

    public TrafficEventChannelRequest setImpactRate(BigDecimal impactRate) {
        this.impactRate = impactRate;
        return this;
    }
}
