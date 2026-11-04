package com.techasians.alertservice.service.forecast;

import com.techasians.alertservice.dto.forecast.EventCalendar;
import com.techasians.alertservice.dto.forecast.ForecastContext;
import com.techasians.alertservice.dto.forecast.ForecastDay;
import com.techasians.alertservice.dto.forecast.ForecastEvent;
import com.techasians.alertservice.repository.TrafficHistoryEventRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tìm các lần event đã xảy ra trước ngày đang tính forecast.
 *
 * Actual occurrence lấy từ traffic_history_events + traffic_histories.
 * Forecast occurrence lấy từ ForecastContext + EventCalendar vì forecast không lưu DB.
 */
@Component
public class EventOccurrenceResolver {

    private static final int MAX_OCCURRENCES = 12;
    private static final int MAX_LOOKBACK_MONTHS = 36;

    private final TrafficHistoryEventRepository trafficHistoryEventRepository;

    public EventOccurrenceResolver(TrafficHistoryEventRepository trafficHistoryEventRepository) {
        this.trafficHistoryEventRepository = trafficHistoryEventRepository;
    }

    public List<LocalDate> findPreviousOccurrences(ForecastEvent event,
                                                    LocalDate beforeDate,
                                                    Long regionChannelId,
                                                    ForecastContext context,
                                                    EventCalendar calendar) {
        Set<LocalDate> dates = new LinkedHashSet<>();
        LocalDate earliestDate = beforeDate.minusMonths(MAX_LOOKBACK_MONTHS);

        List<java.sql.Date> actualDates = trafficHistoryEventRepository.findPreviousActualOccurrenceDates(
                event.getId(), regionChannelId, beforeDate, earliestDate, MAX_OCCURRENCES
        );
        for (java.sql.Date sqlDate : actualDates) {
            dates.add(sqlDate.toLocalDate());
        }

        for (ForecastDay day : context.getAllDays()) {
            if (day.getDate().isBefore(beforeDate)
                    && !day.getDate().isBefore(earliestDate)
                    && hasEvent(calendar, day.getDate(), event.getId())) {
                dates.add(day.getDate());
            }
        }

        List<LocalDate> result = new ArrayList<>(dates);
        result.sort(Comparator.reverseOrder());

        if (result.size() <= MAX_OCCURRENCES) {
            return result;
        }
        return new ArrayList<>(result.subList(0, MAX_OCCURRENCES));
    }

    private boolean hasEvent(EventCalendar calendar, LocalDate date, Integer eventId) {
        for (ForecastEvent event : calendar.getEvents(date)) {
            if (event.getId().equals(eventId)) {
                return true;
            }
        }
        return false;
    }
}
