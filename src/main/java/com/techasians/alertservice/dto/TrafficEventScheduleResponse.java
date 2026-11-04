package com.techasians.alertservice.dto;

import java.time.LocalDate;

/**
 * Response DTO cho 1 lịch sự kiện.
 */
public class TrafficEventScheduleResponse {

    private Long id;
    private LocalDate exactDate;
    private Integer dayOfMonth;
    private Integer monthOfYear;

    public Long getId() {
        return id;
    }

    public TrafficEventScheduleResponse setId(Long id) {
        this.id = id;
        return this;
    }

    public LocalDate getExactDate() {
        return exactDate;
    }

    public TrafficEventScheduleResponse setExactDate(LocalDate exactDate) {
        this.exactDate = exactDate;
        return this;
    }

    public Integer getDayOfMonth() {
        return dayOfMonth;
    }

    public TrafficEventScheduleResponse setDayOfMonth(Integer dayOfMonth) {
        this.dayOfMonth = dayOfMonth;
        return this;
    }

    public Integer getMonthOfYear() {
        return monthOfYear;
    }

    public TrafficEventScheduleResponse setMonthOfYear(Integer monthOfYear) {
        this.monthOfYear = monthOfYear;
        return this;
    }
}
