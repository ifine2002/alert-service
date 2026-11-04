package com.techasians.alertservice.service.forecast;

import com.techasians.alertservice.dto.forecast.EventCalendar;
import com.techasians.alertservice.dto.forecast.ForecastContext;
import com.techasians.alertservice.dto.forecast.ForecastDay;
import com.techasians.alertservice.dto.forecast.ForecastSource;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tìm danh sách ngày thường để tính baseline cho ngày N.
 *
 * Ngày thường là ngày không có sự kiện và có dữ liệu trong ForecastContext.
 * Bao gồm cả actual và forecast trước đó nếu ngày đó không có event.
 */
@Component
public class NormalDayResolver {

    private static final int MIN_NORMAL_DAYS = 7;

    /**
     * Rule chính:
     * - Ưu tiên lấy ngày thường từ đầu tháng N đến N-1.
     * - Nếu trong tháng N có >= 7 ngày thường hợp lệ thì lấy tất cả các ngày đó.
     * - Nếu chưa đủ 7 thì tiếp tục lùi từng ngày trong tối đa 12 tháng dương lịch.
     * - Nếu vẫn chưa đủ 7 nhưng có dữ liệu thì dùng số ngày hiện có.
     * - Nếu không có ngày nào thì trả list rỗng để caller fallback.
     */
    public List<ForecastDay> resolve(LocalDate dateN, ForecastContext context, EventCalendar calendar,
            Integer channelId) {
        List<ForecastDay> currentMonthDays = collectCurrentMonthNormalDays(dateN, context, calendar, channelId);
        if (currentMonthDays.size() >= MIN_NORMAL_DAYS) {
            return currentMonthDays;
        }

        List<ForecastDay> result = new ArrayList<>(currentMonthDays);
        LocalDate current = YearMonth.from(dateN).atDay(1).minusDays(1);
        LocalDate stopDate = dateN.minusMonths(12);

        while (result.size() < MIN_NORMAL_DAYS && !current.isBefore(stopDate)) {
            ForecastDay day = context.get(current);
            if (isValidNormalDay(current, day, calendar, channelId)) {
                result.add(0, day);
            }
            current = current.minusDays(1);
        }

        return result;
    }

    /**
     * Giữ method này để các nơi khác vẫn có thể kiểm tra tháng nhiều sự kiện nếu
     * cần.
     */
    public boolean isHighEventMonth(YearMonth month, EventCalendar calendar) {
        int daysInMonth = month.lengthOfMonth();
        int eventDayCount = calendar.countEventDaysInMonth(month.getYear(), month.getMonthValue());
        return (daysInMonth - eventDayCount) < MIN_NORMAL_DAYS;
    }

    private List<ForecastDay> collectCurrentMonthNormalDays(LocalDate dateN,
            ForecastContext context,
            EventCalendar calendar,
            Integer channelId) {
        if (dateN.getDayOfMonth() == 1) {
            return Collections.emptyList();
        }

        List<ForecastDay> result = new ArrayList<>();
        LocalDate current = dateN.withDayOfMonth(1);
        LocalDate end = dateN.minusDays(1);

        while (!current.isAfter(end)) {
            ForecastDay day = context.get(current);
            if (isValidNormalDay(current, day, calendar, channelId)) {
                result.add(day);
            }
            current = current.plusDays(1);
        }

        return result;
    }

    private boolean isValidNormalDay(LocalDate date, ForecastDay day, EventCalendar calendar, Integer channelId) {
        if (day == null) {
            return false;
        }
        if (day.getValue() == null) {
            return false;
        }
        if (ForecastSource.ACTUAL.equals(day.getSource())) {
            if (!day.isHasEvent()) {
                return true;
            }
            return !calendar.hasEvent(date, channelId);
        }
        return !calendar.hasEvent(date, channelId);
    }
}
