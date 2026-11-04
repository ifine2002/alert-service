package com.techasians.alertservice.dto.forecast;

/**
 * Projection để load region-channel cùng thông tin weekend_affected.
 * JOIN traffic_region_channels → traffic_channels → traffic_regions.
 */
public interface RegionChannelConfigProjection {

    Long getRegionChannelId();

    Integer getChannelId();

    Long getRegionId();

    String getRegionName();

    String getChannelName();

    // 0 = không áp dụng TH6, 1 = áp dụng TH6 cho T7/CN
    Integer getWeekendAffected();

    // 0 = không áp dụng rule Thứ 2, 1 = áp dụng rule Thứ 2
    Integer getMondayAffected();
}
