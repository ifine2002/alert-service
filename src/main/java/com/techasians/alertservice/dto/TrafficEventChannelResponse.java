package com.techasians.alertservice.dto;

import java.math.BigDecimal;

/**
 * Response DTO cho 1 kênh trong event.
 */
public class TrafficEventChannelResponse {

    private Integer id;
    private Integer channelId;
    private String channelName;
    private BigDecimal impactRate;

    public Integer getId() {
        return id;
    }

    public TrafficEventChannelResponse setId(Integer id) {
        this.id = id;
        return this;
    }

    public Integer getChannelId() {
        return channelId;
    }

    public TrafficEventChannelResponse setChannelId(Integer channelId) {
        this.channelId = channelId;
        return this;
    }

    public String getChannelName() {
        return channelName;
    }

    public TrafficEventChannelResponse setChannelName(String channelName) {
        this.channelName = channelName;
        return this;
    }

    public BigDecimal getImpactRate() {
        return impactRate;
    }

    public TrafficEventChannelResponse setImpactRate(BigDecimal impactRate) {
        this.impactRate = impactRate;
        return this;
    }
}
