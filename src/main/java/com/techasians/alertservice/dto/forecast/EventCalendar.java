package com.techasians.alertservice.dto.forecast;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lịch sự kiện dạng Map<ngày, danh sách event>.
 *
 * Được build một lần trước vòng forecast, chứa cả:
 * - Sự kiện lịch sử (để tìm occurrence gần nhất khi tính impact)
 * - Sự kiện tương lai (để biết ngày nào có event trong range forecast)
 *
 * EventCalendar lưu toàn bộ event theo ngày.
 * Khi forecast từng kênh, caller phải lấy event bằng channelId để loại event không mapping channel đó.
 */
public class EventCalendar {

    private final Map<LocalDate, List<ForecastEvent>> calendar;

    public EventCalendar(Map<LocalDate, List<ForecastEvent>> calendar) {
        this.calendar = calendar != null ? calendar : new HashMap<>();
    }

    // Lấy danh sách sự kiện của một ngày (trả về list rỗng nếu không có)
    public List<ForecastEvent> getEvents(LocalDate date) {
        return calendar.getOrDefault(date, Collections.emptyList());
    }

    // Lấy danh sách sự kiện có mapping với channel hiện tại.
    public List<ForecastEvent> getEvents(LocalDate date, Integer channelId) {
        List<ForecastEvent> events = getEvents(date);
        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        List<ForecastEvent> result = new ArrayList<>();
        for (ForecastEvent event : events) {
            if (event.getChannelIds().contains(channelId)) {
                result.add(event);
            }
        }
        return result;
    }

    // Kiểm tra ngày có event không
    public boolean hasEvent(LocalDate date) {
        List<ForecastEvent> events = calendar.get(date);
        return events != null && !events.isEmpty();
    }

    // Kiểm tra ngày có event áp dụng cho channel hiện tại không.
    public boolean hasEvent(LocalDate date, Integer channelId) {
        return !getEvents(date, channelId).isEmpty();
    }

    // Lấy tất cả ngày có event trong một tháng cụ thể
    public List<LocalDate> getEventDatesInMonth(int year, int month) {
        List<LocalDate> result = new ArrayList<>();
        for (LocalDate date : calendar.keySet()) {
            if (date.getYear() == year && date.getMonthValue() == month) {
                result.add(date);
            }
        }
        return result;
    }

    // Đếm số ngày có event trong một tháng - dùng để xác định "tháng nhiều sự kiện"
    public int countEventDaysInMonth(int year, int month) {
        return getEventDatesInMonth(year, month).size();
    }
}
