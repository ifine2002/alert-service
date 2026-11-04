package com.techasians.alertservice.dto;

import java.time.LocalDate;

/**
 * Request DTO cho 1 lịch sự kiện.
 * Field nào được sử dụng phụ thuộc vào loại sự kiện (validate trong service):
 * - Phát sinh / Định kỳ không quy luật: exactDate
 * - Định kỳ có quy luật / Hằng tháng:  dayOfMonth
 * - Định kỳ có quy luật / Hằng năm:    dayOfMonth + monthOfYear
 * - Định kỳ có quy luật / Hằng quý:    dayOfMonth + monthOfYear
 */
public class TrafficEventScheduleRequest {

    // Ngày chính xác — dùng cho phát sinh và định kỳ không quy luật
    private LocalDate exactDate;

    // Ngày trong tháng (1–31) — dùng cho định kỳ có quy luật
    private Integer dayOfMonth;

    // Tháng trong năm (1–12) — dùng thêm khi chu kỳ hằng năm hoặc hằng quý có quy luật
    private Integer monthOfYear;

    public LocalDate getExactDate() {
        return exactDate;
    }

    public TrafficEventScheduleRequest setExactDate(LocalDate exactDate) {
        this.exactDate = exactDate;
        return this;
    }

    public Integer getDayOfMonth() {
        return dayOfMonth;
    }

    public TrafficEventScheduleRequest setDayOfMonth(Integer dayOfMonth) {
        this.dayOfMonth = dayOfMonth;
        return this;
    }

    public Integer getMonthOfYear() {
        return monthOfYear;
    }

    public TrafficEventScheduleRequest setMonthOfYear(Integer monthOfYear) {
        this.monthOfYear = monthOfYear;
        return this;
    }
}
