package com.techasians.alertservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TrafficChannelKpiResponse {

    private Integer id;
    private Integer channelId;
    private String channelName;
    private BigDecimal kpiRate;
    private BigDecimal productivityValue;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;

    public Integer getId() {
        return id;
    }

    public TrafficChannelKpiResponse setId(Integer id) {
        this.id = id;
        return this;
    }

    public Integer getChannelId() {
        return channelId;
    }

    public TrafficChannelKpiResponse setChannelId(Integer channelId) {
        this.channelId = channelId;
        return this;
    }

    public String getChannelName() {
        return channelName;
    }

    public TrafficChannelKpiResponse setChannelName(String channelName) {
        this.channelName = channelName;
        return this;
    }

    public BigDecimal getKpiRate() {
        return kpiRate;
    }

    public TrafficChannelKpiResponse setKpiRate(BigDecimal kpiRate) {
        this.kpiRate = kpiRate;
        return this;
    }

    public BigDecimal getProductivityValue() {
        return productivityValue;
    }

    public TrafficChannelKpiResponse setProductivityValue(BigDecimal productivityValue) {
        this.productivityValue = productivityValue;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public TrafficChannelKpiResponse setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public TrafficChannelKpiResponse setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public TrafficChannelKpiResponse setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public TrafficChannelKpiResponse setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
        return this;
    }
}
