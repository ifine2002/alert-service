package com.techasians.alertservice.dto;

import java.math.BigDecimal;
import javax.validation.constraints.NotNull;

public class TrafficChannelKpiRequest {

    @NotNull(message = "ID kênh không được để trống")
    private Integer channelId;

    @NotNull(message = "Tỷ lệ KPI không được để trống")
    private BigDecimal kpiRate;

    @NotNull(message = "Giá trị năng suất không được để trống")
    private BigDecimal productivityValue;

    public Integer getChannelId() {
        return channelId;
    }

    public TrafficChannelKpiRequest setChannelId(Integer channelId) {
        this.channelId = channelId;
        return this;
    }

    public BigDecimal getKpiRate() {
        return kpiRate;
    }

    public TrafficChannelKpiRequest setKpiRate(BigDecimal kpiRate) {
        this.kpiRate = kpiRate;
        return this;
    }

    public BigDecimal getProductivityValue() {
        return productivityValue;
    }

    public TrafficChannelKpiRequest setProductivityValue(BigDecimal productivityValue) {
        this.productivityValue = productivityValue;
        return this;
    }
}
