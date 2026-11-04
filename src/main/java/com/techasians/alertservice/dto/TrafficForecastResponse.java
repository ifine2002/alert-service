package com.techasians.alertservice.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Response chứa kết quả dự trù lưu lượng cho một region-channel.
 *
 * value trong từng forecast day đã round scale=0, HALF_UP.
 */
public class TrafficForecastResponse {

    private Long regionChannelId;
    private Long regionId;
    private String regionName;
    private Integer channelId;
    private String channelName;
    private String metricName;
    private LocalDate actualCutoff;
    private LocalDate calculationStart;
    private String dataQuality;
    private String dataQualityNote;
    private Integer gapDays;
    private List<TrafficForecastDayResponse> forecasts;

    public Long getRegionChannelId() {
        return regionChannelId;
    }

    public TrafficForecastResponse setRegionChannelId(Long regionChannelId) {
        this.regionChannelId = regionChannelId;
        return this;
    }

    public Long getRegionId() {
        return regionId;
    }

    public TrafficForecastResponse setRegionId(Long regionId) {
        this.regionId = regionId;
        return this;
    }

    public String getRegionName() {
        return regionName;
    }

    public TrafficForecastResponse setRegionName(String regionName) {
        this.regionName = regionName;
        return this;
    }

    public Integer getChannelId() {
        return channelId;
    }

    public TrafficForecastResponse setChannelId(Integer channelId) {
        this.channelId = channelId;
        return this;
    }

    public String getChannelName() {
        return channelName;
    }

    public TrafficForecastResponse setChannelName(String channelName) {
        this.channelName = channelName;
        return this;
    }

    public String getMetricName() {
        return metricName;
    }

    public TrafficForecastResponse setMetricName(String metricName) {
        this.metricName = metricName;
        return this;
    }

    public LocalDate getActualCutoff() {
        return actualCutoff;
    }

    public TrafficForecastResponse setActualCutoff(LocalDate actualCutoff) {
        this.actualCutoff = actualCutoff;
        return this;
    }

    public LocalDate getCalculationStart() {
        return calculationStart;
    }

    public TrafficForecastResponse setCalculationStart(LocalDate calculationStart) {
        this.calculationStart = calculationStart;
        return this;
    }

    public String getDataQuality() {
        return dataQuality;
    }

    public TrafficForecastResponse setDataQuality(String dataQuality) {
        this.dataQuality = dataQuality;
        return this;
    }

    public String getDataQualityNote() {
        return dataQualityNote;
    }

    public TrafficForecastResponse setDataQualityNote(String dataQualityNote) {
        this.dataQualityNote = dataQualityNote;
        return this;
    }

    public Integer getGapDays() {
        return gapDays;
    }

    public TrafficForecastResponse setGapDays(Integer gapDays) {
        this.gapDays = gapDays;
        return this;
    }

    public List<TrafficForecastDayResponse> getForecasts() {
        return forecasts;
    }

    public TrafficForecastResponse setForecasts(List<TrafficForecastDayResponse> forecasts) {
        this.forecasts = forecasts;
        return this;
    }
}
