package com.techasians.alertservice.dto.forecast;

import java.time.LocalDate;

/**
 * Thông tin một lịch (schedule) của sự kiện.
 *
 * Cách dùng tuỳ theo loại sự kiện:
 * - Phát sinh / Định kỳ không quy luật: exactDate
 * - Hằng tháng: dayOfMonth
 * - Hằng năm: dayOfMonth + monthOfYear
 * - Hằng quý: dayOfMonth + monthOfYear (tháng bắt đầu chu kỳ, mỗi +3 tháng)
 */
public class EventScheduleConfig {

    // Ngày chính xác - dùng cho phát sinh và định kỳ không quy luật
    private LocalDate exactDate;

    // Ngày trong tháng (1-31) - dùng cho định kỳ có quy luật
    private Integer dayOfMonth;

    // Tháng trong năm (1-12) - dùng thêm cho hằng năm và hằng quý
    private Integer monthOfYear;

    public EventScheduleConfig() {
    }

    public LocalDate getExactDate() {
        return exactDate;
    }

    public EventScheduleConfig setExactDate(LocalDate exactDate) {
        this.exactDate = exactDate;
        return this;
    }

    public Integer getDayOfMonth() {
        return dayOfMonth;
    }

    public EventScheduleConfig setDayOfMonth(Integer dayOfMonth) {
        this.dayOfMonth = dayOfMonth;
        return this;
    }

    public Integer getMonthOfYear() {
        return monthOfYear;
    }

    public EventScheduleConfig setMonthOfYear(Integer monthOfYear) {
        this.monthOfYear = monthOfYear;
        return this;
    }
}
