package com.techasians.alertservice.dto;

import java.time.LocalDate;

/**
 * Metadata của một kênh trong một khu vực.
 *
 * Dùng trong TrafficForecastRegionResult.channels để FE biết kênh nào có regionChannelId nào.
 * FE tra cứu: channelResults[i].regionChannelId → channels[j].regionChannelId → channelName.
 */
public class TrafficForecastChannelMeta {

    /** ID region_channel (khoá nội bộ), khớp với channelResults trong mỗi ngày */
    private Long regionChannelId;

    /** ID kênh (ví dụ: 1 = Cố định, 2 = Di động) */
    private Integer channelId;

    /** Tên kênh hiển thị, ví dụ: "KÊNH CỐ ĐỊNH 18008119" */
    private String channelName;

    /** Tên metric, ví dụ: "Cuộc gọi" */
    private String metricName;

    /** Ngày actual mới nhất của kênh này, forecast tính từ ngày tiếp theo */
    private LocalDate actualCutoff;

    /** Ngày bắt đầu tính forecast (= actualCutoff + 1) */
    private LocalDate calculationStart;

    /**
     * Chất lượng dữ liệu của kênh này.
     * Ví dụ: "GOOD", "POOR", "NO_DATA"
     */
    private String dataQuality;

    /** Ghi chú về chất lượng dữ liệu, hiển thị tooltip cho FE */
    private String dataQualityNote;

    /** Số ngày bị thiếu dữ liệu actual */
    private Integer gapDays;

    public Long getRegionChannelId() {
        return regionChannelId;
    }

    public TrafficForecastChannelMeta setRegionChannelId(Long regionChannelId) {
        this.regionChannelId = regionChannelId;
        return this;
    }

    public Integer getChannelId() {
        return channelId;
    }

    public TrafficForecastChannelMeta setChannelId(Integer channelId) {
        this.channelId = channelId;
        return this;
    }

    public String getChannelName() {
        return channelName;
    }

    public TrafficForecastChannelMeta setChannelName(String channelName) {
        this.channelName = channelName;
        return this;
    }

    public String getMetricName() {
        return metricName;
    }

    public TrafficForecastChannelMeta setMetricName(String metricName) {
        this.metricName = metricName;
        return this;
    }

    public LocalDate getActualCutoff() {
        return actualCutoff;
    }

    public TrafficForecastChannelMeta setActualCutoff(LocalDate actualCutoff) {
        this.actualCutoff = actualCutoff;
        return this;
    }

    public LocalDate getCalculationStart() {
        return calculationStart;
    }

    public TrafficForecastChannelMeta setCalculationStart(LocalDate calculationStart) {
        this.calculationStart = calculationStart;
        return this;
    }

    public String getDataQuality() {
        return dataQuality;
    }

    public TrafficForecastChannelMeta setDataQuality(String dataQuality) {
        this.dataQuality = dataQuality;
        return this;
    }

    public String getDataQualityNote() {
        return dataQualityNote;
    }

    public TrafficForecastChannelMeta setDataQualityNote(String dataQualityNote) {
        this.dataQualityNote = dataQualityNote;
        return this;
    }

    public Integer getGapDays() {
        return gapDays;
    }

    public TrafficForecastChannelMeta setGapDays(Integer gapDays) {
        this.gapDays = gapDays;
        return this;
    }
}

