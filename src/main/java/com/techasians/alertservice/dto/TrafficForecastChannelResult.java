package com.techasians.alertservice.dto;

import java.math.BigDecimal;

/**
 * Kết quả dự trù lưu lượng của một kênh trong một ngày cụ thể.
 *
 * Dùng trong TrafficForecastRowResponse để FE render từng cột kênh.
 *
 * Ví dụ: Kênh Cố Định 18008119 có impactRate = +4.5%, value = 15200, personnelCount = 0
 */
public class TrafficForecastChannelResult {

    /** ID của bản ghi region_channel (khoá nội bộ) */
    private Long regionChannelId;

    /** ID kênh (ví dụ: 1 = Cố định, 2 = Di động...) */
    private Integer channelId;

    /** Tên kênh hiển thị (ví dụ: "KÊNH CỐ ĐỊNH 18008119") */
    private String channelName;

    /** ID khu vực */
    private Long regionId;

    /** Tên khu vực */
    private String regionName;

    /**
     * % Tăng giảm lưu lượng so với baseline.
     * Giá trị dạng thập phân đã nhân 100 để hiển thị phần trăm.
     * Ví dụ: 4.5 nghĩa là +4.5%, -1.2 nghĩa là -1.2%.
     * null nếu ngày này không có impact (không có sự kiện tác động).
     */
    private BigDecimal impactRate;

    /**
     * Lưu lượng dự kiến (số cuộc gọi dự trù), đã round scale=0.
     * null nếu không có dữ liệu.
     */
    private BigDecimal value;

    /**
     * Quân số dự kiến.
     * Hiện tại mặc định = 0, sẽ tính thực tế khi có nghiệp vụ.
     */
    private Integer personnelCount = 0;

    public Long getRegionChannelId() {
        return regionChannelId;
    }

    public TrafficForecastChannelResult setRegionChannelId(Long regionChannelId) {
        this.regionChannelId = regionChannelId;
        return this;
    }

    public Integer getChannelId() {
        return channelId;
    }

    public TrafficForecastChannelResult setChannelId(Integer channelId) {
        this.channelId = channelId;
        return this;
    }

    public String getChannelName() {
        return channelName;
    }

    public TrafficForecastChannelResult setChannelName(String channelName) {
        this.channelName = channelName;
        return this;
    }

    public Long getRegionId() {
        return regionId;
    }

    public TrafficForecastChannelResult setRegionId(Long regionId) {
        this.regionId = regionId;
        return this;
    }

    public String getRegionName() {
        return regionName;
    }

    public TrafficForecastChannelResult setRegionName(String regionName) {
        this.regionName = regionName;
        return this;
    }

    public BigDecimal getImpactRate() {
        return impactRate;
    }

    public TrafficForecastChannelResult setImpactRate(BigDecimal impactRate) {
        this.impactRate = impactRate;
        return this;
    }

    public BigDecimal getValue() {
        return value;
    }

    public TrafficForecastChannelResult setValue(BigDecimal value) {
        this.value = value;
        return this;
    }

    public Integer getPersonnelCount() {
        return personnelCount;
    }

    public TrafficForecastChannelResult setPersonnelCount(Integer personnelCount) {
        this.personnelCount = personnelCount;
        return this;
    }
}
