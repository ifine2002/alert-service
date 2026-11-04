package com.techasians.alertservice.dto.forecast;

import java.time.LocalDate;

/**
 * Projection lấy ngày actual cuối cùng theo từng region-channel.
 */
public interface RegionChannelActualCutoffProjection {

    Long getRegionChannelId();

    LocalDate getActualCutoff();
}
