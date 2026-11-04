package com.techasians.alertservice.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Một hàng trong bảng dự trù lưu lượng, đại diện cho một ngày cụ thể.
 *
 * Cấu trúc này khớp với layout bảng FE (như ảnh):
 *   - Cột cố định: Năm, Quý, Tháng, Ngày, Thứ, Sự kiện định kỳ, Sự kiện phát sinh
 *   - Cột động: mỗi kênh có impactRate, value (lưu lượng dự kiến), personnelCount (quân số)
 *
 * FE sẽ nhận List<TrafficForecastRowResponse> và render từng hàng theo ngày.
 */
public class TrafficForecastRowResponse {

    /** Ngày đầy đủ, dùng để sort và debug */
    private LocalDate date;

    /**
     * Năm hiển thị, ví dụ: "N2026"
     * Cách tính: "N" + year
     */
    private String year;

    /**
     * Quý hiển thị, ví dụ: "Q3/2026"
     * Cách tính: "Q" + quarter + "/" + year
     */
    private String quarter;

    /**
     * Tháng hiển thị, ví dụ: "T09"
     * Cách tính: "T" + String.format("%02d", month)
     */
    private String month;

    /**
     * Ngày trong tháng hiển thị, ví dụ: "N01", "N15"
     * Cách tính: "N" + String.format("%02d", dayOfMonth)
     */
    private String day;

    /**
     * Thứ trong tuần hiển thị, ví dụ: "T2", "T3", ..., "T7", "CN"
     * CN = Chủ Nhật, T2 = Thứ Hai, ..., T7 = Thứ Bảy
     */
    private String dayOfWeek;

    /**
     * Danh sách tên sự kiện định kỳ (eventType = 1) diễn ra trong ngày này.
     * Ví dụ: ["Chu kỳ cước", "Ngày cuối tuần"]
     * Rỗng nếu không có sự kiện định kỳ.
     */
    private List<String> periodicEvents;

    /**
     * Danh sách tên sự kiện phát sinh (eventType = 2) diễn ra trong ngày này.
     * Ví dụ: ["Ngày Quốc khánh"]
     * Rỗng nếu không có sự kiện phát sinh.
     */
    private List<String> arisingEvents;

    /**
     * Kết quả dự trù theo từng kênh trong ngày này.
     * Mỗi phần tử trong list là 1 kênh, chứa impactRate, value, personnelCount.
     * FE dùng channelId hoặc channelName để biết cột nào của bảng.
     */
    private List<TrafficForecastChannelResult> channelResults;

    public LocalDate getDate() {
        return date;
    }

    public TrafficForecastRowResponse setDate(LocalDate date) {
        this.date = date;
        return this;
    }

    public String getYear() {
        return year;
    }

    public TrafficForecastRowResponse setYear(String year) {
        this.year = year;
        return this;
    }

    public String getQuarter() {
        return quarter;
    }

    public TrafficForecastRowResponse setQuarter(String quarter) {
        this.quarter = quarter;
        return this;
    }

    public String getMonth() {
        return month;
    }

    public TrafficForecastRowResponse setMonth(String month) {
        this.month = month;
        return this;
    }

    public String getDay() {
        return day;
    }

    public TrafficForecastRowResponse setDay(String day) {
        this.day = day;
        return this;
    }

    public String getDayOfWeek() {
        return dayOfWeek;
    }

    public TrafficForecastRowResponse setDayOfWeek(String dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
        return this;
    }

    public List<String> getPeriodicEvents() {
        return periodicEvents;
    }

    public TrafficForecastRowResponse setPeriodicEvents(List<String> periodicEvents) {
        this.periodicEvents = periodicEvents;
        return this;
    }

    public List<String> getArisingEvents() {
        return arisingEvents;
    }

    public TrafficForecastRowResponse setArisingEvents(List<String> arisingEvents) {
        this.arisingEvents = arisingEvents;
        return this;
    }

    public List<TrafficForecastChannelResult> getChannelResults() {
        return channelResults;
    }

    public TrafficForecastRowResponse setChannelResults(List<TrafficForecastChannelResult> channelResults) {
        this.channelResults = channelResults;
        return this;
    }
}
