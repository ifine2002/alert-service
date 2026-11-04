package com.techasians.alertservice.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Một hàng trong bảng dự trù, đại diện cho một ngày cụ thể trong một khu vực.
 *
 * channelResults dùng regionChannelId để FE tra cứu thông tin kênh từ region.channels.
 * Sự kiện của ngày được đặt trong từng channelResult và đã lọc theo mapping kênh.
 */
public class TrafficForecastDayRow {

    /** Ngày đầy đủ dạng ISO, dùng để sort */
    private LocalDate date;

    /** Năm hiển thị, ví dụ: "N2026" */
    private String year;

    /** Quý hiển thị, ví dụ: "Q3/2026" */
    private String quarter;

    /** Tháng hiển thị, ví dụ: "T09" */
    private String month;

    /** Ngày trong tháng hiển thị, ví dụ: "N01" */
    private String day;

    /** Thứ trong tuần: "T2", "T3", ..., "T7", "CN" */
    private String dayOfWeek;

    /**
     * Kết quả dự trù của từng kênh trong ngày này.
     * Mỗi phần tử chứa kết quả tính toán và các sự kiện thực sự ảnh hưởng đến kênh.
     * FE tra cứu metadata kênh (channelId, channelName) từ region.channels.
     */
    private List<TrafficForecastDayChannelResult> channelResults;

    public LocalDate getDate() {
        return date;
    }

    public TrafficForecastDayRow setDate(LocalDate date) {
        this.date = date;
        return this;
    }

    public String getYear() {
        return year;
    }

    public TrafficForecastDayRow setYear(String year) {
        this.year = year;
        return this;
    }

    public String getQuarter() {
        return quarter;
    }

    public TrafficForecastDayRow setQuarter(String quarter) {
        this.quarter = quarter;
        return this;
    }

    public String getMonth() {
        return month;
    }

    public TrafficForecastDayRow setMonth(String month) {
        this.month = month;
        return this;
    }

    public String getDay() {
        return day;
    }

    public TrafficForecastDayRow setDay(String day) {
        this.day = day;
        return this;
    }

    public String getDayOfWeek() {
        return dayOfWeek;
    }

    public TrafficForecastDayRow setDayOfWeek(String dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
        return this;
    }

    public List<TrafficForecastDayChannelResult> getChannelResults() {
        return channelResults;
    }

    public TrafficForecastDayRow setChannelResults(List<TrafficForecastDayChannelResult> channelResults) {
        this.channelResults = channelResults;
        return this;
    }
}
