package com.techasians.alertservice.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class TrafficChannelKpiImportRow {
    private int rowIndex;
    private Integer channelId;
    private BigDecimal kpiRate;
    private BigDecimal productivityValue;
    private boolean isSkip;
    private List<String> errorMessages = new ArrayList<>();

    public int getRowIndex() {
        return rowIndex;
    }

    public void setRowIndex(int rowIndex) {
        this.rowIndex = rowIndex;
    }

    public Integer getChannelId() {
        return channelId;
    }

    public void setChannelId(Integer channelId) {
        this.channelId = channelId;
    }

    public BigDecimal getKpiRate() {
        return kpiRate;
    }

    public void setKpiRate(BigDecimal kpiRate) {
        this.kpiRate = kpiRate;
    }

    public BigDecimal getProductivityValue() {
        return productivityValue;
    }

    public void setProductivityValue(BigDecimal productivityValue) {
        this.productivityValue = productivityValue;
    }

    public boolean isSkip() {
        return isSkip;
    }

    public void setSkip(boolean skip) {
        isSkip = skip;
    }

    public List<String> getErrorMessages() {
        return errorMessages;
    }

    public void addError(String error) {
        this.errorMessages.add(error);
    }

    public boolean hasErrors() {
        return !this.errorMessages.isEmpty();
    }
}
