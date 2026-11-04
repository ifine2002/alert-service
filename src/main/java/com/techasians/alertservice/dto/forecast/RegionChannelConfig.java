package com.techasians.alertservice.dto.forecast;

import java.time.LocalDate;

/**
 * Thông tin cấu hình của một region-channel dùng trong forecast.
 * Giữ các field cần thiết để calculator quyết định áp dụng rule nào.
 */
public class RegionChannelConfig {

    private Long regionChannelId;
    private Integer channelId;
    private Long regionId;
    private String regionName;
    private String channelName;

    // true nếu channel.weekend_affected = 1 (áp dụng rule T7/CN)
    private boolean weekendAffected;

    // true nếu channel.monday_affected = 1 (áp dụng rule Thứ 2)
    private boolean mondayAffected;

    private LocalDate actualCutoff;
    private LocalDate calculationStart;
    private String dataQuality;
    private String dataQualityNote;
    private Integer gapDays;
    private boolean skipped;

    public RegionChannelConfig() {
    }

    public Long getRegionChannelId() {
        return regionChannelId;
    }

    public RegionChannelConfig setRegionChannelId(Long regionChannelId) {
        this.regionChannelId = regionChannelId;
        return this;
    }

    public Integer getChannelId() {
        return channelId;
    }

    public RegionChannelConfig setChannelId(Integer channelId) {
        this.channelId = channelId;
        return this;
    }

    public Long getRegionId() {
        return regionId;
    }

    public RegionChannelConfig setRegionId(Long regionId) {
        this.regionId = regionId;
        return this;
    }

    public String getRegionName() {
        return regionName;
    }

    public RegionChannelConfig setRegionName(String regionName) {
        this.regionName = regionName;
        return this;
    }

    public String getChannelName() {
        return channelName;
    }

    public RegionChannelConfig setChannelName(String channelName) {
        this.channelName = channelName;
        return this;
    }

    public boolean isWeekendAffected() {
        return weekendAffected;
    }

    public RegionChannelConfig setWeekendAffected(boolean weekendAffected) {
        this.weekendAffected = weekendAffected;
        return this;
    }

    public boolean isMondayAffected() {
        return mondayAffected;
    }

    public RegionChannelConfig setMondayAffected(boolean mondayAffected) {
        this.mondayAffected = mondayAffected;
        return this;
    }

    public LocalDate getActualCutoff() {
        return actualCutoff;
    }

    public RegionChannelConfig setActualCutoff(LocalDate actualCutoff) {
        this.actualCutoff = actualCutoff;
        return this;
    }

    public LocalDate getCalculationStart() {
        return calculationStart;
    }

    public RegionChannelConfig setCalculationStart(LocalDate calculationStart) {
        this.calculationStart = calculationStart;
        return this;
    }

    public String getDataQuality() {
        return dataQuality;
    }

    public RegionChannelConfig setDataQuality(String dataQuality) {
        this.dataQuality = dataQuality;
        return this;
    }

    public String getDataQualityNote() {
        return dataQualityNote;
    }

    public RegionChannelConfig setDataQualityNote(String dataQualityNote) {
        this.dataQualityNote = dataQualityNote;
        return this;
    }

    public Integer getGapDays() {
        return gapDays;
    }

    public RegionChannelConfig setGapDays(Integer gapDays) {
        this.gapDays = gapDays;
        return this;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public RegionChannelConfig setSkipped(boolean skipped) {
        this.skipped = skipped;
        return this;
    }
}
