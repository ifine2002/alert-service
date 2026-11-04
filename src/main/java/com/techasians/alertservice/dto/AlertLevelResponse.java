package com.techasians.alertservice.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO đại diện cho từng mức cảnh báo, dùng để trả dữ liệu xuống Frontend.
 */
public class AlertLevelResponse {

    private Integer id;
    private Integer levelNumber;
    private Integer conditionOperator;
    private BigDecimal thresholdValue;
    private BigDecimal thresholdValueTo;
    private Integer status;
    private List<Integer> channelIds;
    private List<AlertGroupSumaryResponse> groups;

    public Integer getId() {
        return id;
    }

    public AlertLevelResponse setId(Integer id) {
        this.id = id;
        return this;
    }

    public Integer getLevelNumber() {
        return levelNumber;
    }

    public AlertLevelResponse setLevelNumber(Integer levelNumber) {
        this.levelNumber = levelNumber;
        return this;
    }

    public Integer getConditionOperator() {
        return conditionOperator;
    }

    public AlertLevelResponse setConditionOperator(Integer conditionOperator) {
        this.conditionOperator = conditionOperator;
        return this;
    }

    public BigDecimal getThresholdValue() {
        return thresholdValue;
    }

    public AlertLevelResponse setThresholdValue(BigDecimal thresholdValue) {
        this.thresholdValue = thresholdValue;
        return this;
    }

    public BigDecimal getThresholdValueTo() {
        return thresholdValueTo;
    }

    public AlertLevelResponse setThresholdValueTo(BigDecimal thresholdValueTo) {
        this.thresholdValueTo = thresholdValueTo;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public AlertLevelResponse setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public List<Integer> getChannelIds() {
        return channelIds;
    }

    public AlertLevelResponse setChannelIds(List<Integer> channelIds) {
        this.channelIds = channelIds;
        return this;
    }

    public List<AlertGroupSumaryResponse> getGroups() {
        return groups;
    }

    public AlertLevelResponse setGroups(List<AlertGroupSumaryResponse> groups) {
        this.groups = groups;
        return this;
    }
}
