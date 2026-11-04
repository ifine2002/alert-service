package com.techasians.alertservice.dto.forecast;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Thông tin sự kiện dùng trong quá trình tính forecast.
 * Không dùng trực tiếp JPA Entity - tách biệt để calculator không phụ thuộc DB.
 *
 * eventType:  1 = Định kỳ, 2 = Phát sinh
 * repeatType: 1 = Hằng tháng, 2 = Hằng năm, 3 = Hằng quý (chỉ có khi eventType=1)
 * patternType: 1 = Có quy luật, 2 = Không có quy luật (chỉ có khi eventType=1)
 *
 * configuredImpactRate: tỷ lệ impact từ traffic_event_channels (đã chia 100, dạng 0.0-1.0).
 * Chỉ dùng khi event chưa từng có occurrence lịch sử trước ngày cần forecast.
 */
public class ForecastEvent {

    private Integer id;
    private String name;

    // 1=Định kỳ, 2=Phát sinh
    private Integer eventType;

    // 1=Hằng tháng, 2=Hằng năm, 3=Hằng quý - chỉ có giá trị khi eventType=1
    private Integer repeatType;

    // 1=Có quy luật, 2=Không có quy luật - chỉ có giá trị khi eventType=1
    private Integer patternType;

    // Impact rate đã chia 100 (0.0 - 1.0) - dùng khi chưa có historical occurrence
    private BigDecimal configuredImpactRate;

    // Event chỉ được coi là áp dụng cho các channel nằm trong mapping này.
    private List<Integer> channelIds = new ArrayList<>();

    public ForecastEvent() {
    }

    public Integer getId() {
        return id;
    }

    public ForecastEvent setId(Integer id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public ForecastEvent setName(String name) {
        this.name = name;
        return this;
    }

    public Integer getEventType() {
        return eventType;
    }

    public ForecastEvent setEventType(Integer eventType) {
        this.eventType = eventType;
        return this;
    }

    public Integer getRepeatType() {
        return repeatType;
    }

    public ForecastEvent setRepeatType(Integer repeatType) {
        this.repeatType = repeatType;
        return this;
    }

    public Integer getPatternType() {
        return patternType;
    }

    public ForecastEvent setPatternType(Integer patternType) {
        this.patternType = patternType;
        return this;
    }

    public BigDecimal getConfiguredImpactRate() {
        return configuredImpactRate;
    }

    public ForecastEvent setConfiguredImpactRate(BigDecimal configuredImpactRate) {
        this.configuredImpactRate = configuredImpactRate;
        return this;
    }

    public List<Integer> getChannelIds() {
        return channelIds;
    }

    public ForecastEvent setChannelIds(List<Integer> channelIds) {
        this.channelIds = channelIds != null ? channelIds : new ArrayList<>();
        return this;
    }
}
