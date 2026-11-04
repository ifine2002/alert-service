package com.techasians.alertservice.service.forecast;

import com.techasians.alertservice.dto.forecast.EventCalendar;
import com.techasians.alertservice.dto.forecast.EventChannelKey;
import com.techasians.alertservice.dto.forecast.EventConfig;
import com.techasians.alertservice.dto.forecast.EventScheduleConfig;
import com.techasians.alertservice.dto.forecast.ForecastEvent;
import com.techasians.alertservice.dto.forecast.ForecastInputData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dựng EventCalendar từ danh sách EventConfig.
 *
 * Materialize (phát sinh ra ngày cụ thể) cho từng loại event trong khoảng [rangeStart, rangeEnd]:
 * - PHÁT SINH / Định kỳ không quy luật (patternType=2): exact_date
 * - MONTHLY (repeatType=1): mỗi tháng vào day_of_month
 * - YEARLY (repeatType=2): mỗi năm vào day_of_month/month_of_year
 * - QUARTERLY (repeatType=3): mỗi +3 tháng từ tháng bắt đầu vào day_of_month
 *
 * EventCalendar chứa cả sự kiện lịch sử lẫn tương lai để:
 * - Biết ngày nào trong range forecast có event
 * - Tìm được previous occurrence khi tính impact
 */
@Component
public class EventCalendarBuilder {

    // eventType = 1: Định kỳ
    private static final Integer EVENT_TYPE_PERIODIC = 1;
    // eventType = 2: Phát sinh
    private static final Integer EVENT_TYPE_INCIDENT = 2;

    // patternType = 1: Có quy luật
    private static final Integer PATTERN_TYPE_REGULAR = 1;
    // patternType = 2: Không có quy luật
    private static final Integer PATTERN_TYPE_IRREGULAR = 2;

    // repeatType: 1=Hằng tháng, 2=Hằng năm, 3=Hằng quý
    private static final Integer REPEAT_MONTHLY = 1;
    private static final Integer REPEAT_YEARLY = 2;
    private static final Integer REPEAT_QUARTERLY = 3;

    /**
     * Build EventCalendar cho khoảng [rangeStart, rangeEnd].
     *
     * @param input      ForecastInputData chứa events và impactRates
     * @param rangeStart ngày bắt đầu (thường là actualCutoff - 12 months để đủ lookup lịch sử)
     * @param rangeEnd   ngày kết thúc (request.toDate)
     * @return EventCalendar dùng trong vòng forecast
     */
    public EventCalendar build(ForecastInputData input, LocalDate rangeStart, LocalDate rangeEnd) {
        Map<LocalDate, List<ForecastEvent>> calendarMap = new HashMap<>();

        for (EventConfig eventConfig : input.getEvents()) {
            // Tìm configured impact rate cho event này (trung bình các channel, hoặc null)
            // ForecastEvent.configuredImpactRate là fallback khi không tìm được theo channelId
            BigDecimal fallbackRate = findFallbackRate(eventConfig, input.getImpactRates());

            ForecastEvent forecastEvent = toForecastEvent(eventConfig, fallbackRate);

            // Materialize ra các ngày cụ thể
            List<LocalDate> occurrenceDates = materialize(eventConfig, rangeStart, rangeEnd);

            // Thêm vào calendar
            for (LocalDate date : occurrenceDates) {
                calendarMap.computeIfAbsent(date, k -> new ArrayList<>()).add(forecastEvent);
            }
        }

        return new EventCalendar(calendarMap);
    }

    /**
     * Chuyển EventConfig thành ForecastEvent (không có JPA, thuần Java).
     */
    private ForecastEvent toForecastEvent(EventConfig config, BigDecimal fallbackRate) {
        return new ForecastEvent()
                .setId(config.getId())
                .setName(config.getName())
                .setEventType(config.getEventType())
                .setRepeatType(config.getRepeatType())
                .setPatternType(config.getPatternType())
                .setConfiguredImpactRate(fallbackRate)
                .setChannelIds(config.getChannelIds());
    }

    /**
     * Materialize event ra danh sách ngày cụ thể trong [rangeStart, rangeEnd].
     */
    private List<LocalDate> materialize(EventConfig event, LocalDate rangeStart, LocalDate rangeEnd) {
        List<LocalDate> result = new ArrayList<>();

        for (EventScheduleConfig schedule : event.getSchedules()) {
            if (schedule.getExactDate() != null) {
                LocalDate date = schedule.getExactDate();
                if (!date.isBefore(rangeStart) && !date.isAfter(rangeEnd)) {
                    result.add(date);
                }
            }
        }

        if (!result.isEmpty()) {
            return result;
        }

        if (EVENT_TYPE_PERIODIC.equals(event.getEventType())
                && PATTERN_TYPE_REGULAR.equals(event.getPatternType())) {
            for (EventScheduleConfig schedule : event.getSchedules()) {
                result.addAll(materializeRegularSchedule(event.getRepeatType(), schedule, rangeStart, rangeEnd));
            }
        }

        return result;
    }

    /**
     * Materialize schedule có quy luật (MONTHLY / YEARLY / QUARTERLY).
     */
    private List<LocalDate> materializeRegularSchedule(Integer repeatType, EventScheduleConfig schedule,
                                                         LocalDate rangeStart, LocalDate rangeEnd) {
        List<LocalDate> result = new ArrayList<>();

        if (REPEAT_MONTHLY.equals(repeatType)) {
            // Hằng tháng: mỗi tháng trong range vào day_of_month
            result.addAll(materializeMonthly(schedule.getDayOfMonth(), rangeStart, rangeEnd));

        } else if (REPEAT_YEARLY.equals(repeatType)) {
            // Hằng năm: mỗi năm vào day_of_month/month_of_year
            result.addAll(materializeYearly(schedule.getDayOfMonth(), schedule.getMonthOfYear(),
                    rangeStart, rangeEnd));

        } else if (REPEAT_QUARTERLY.equals(repeatType)) {
            // Hằng quý: month_of_year = tháng bắt đầu chu kỳ, mỗi +3 tháng
            result.addAll(materializeQuarterly(schedule.getDayOfMonth(), schedule.getMonthOfYear(),
                    rangeStart, rangeEnd));
        }

        return result;
    }

    /**
     * Hằng tháng: vào ngày day_of_month của mỗi tháng trong [rangeStart, rangeEnd].
     * Bỏ qua tháng không có ngày đó (ví dụ: 31/02).
     */
    private List<LocalDate> materializeMonthly(Integer dayOfMonth, LocalDate rangeStart, LocalDate rangeEnd) {
        List<LocalDate> result = new ArrayList<>();
        if (dayOfMonth == null) return result;

        YearMonth startMonth = YearMonth.from(rangeStart);
        YearMonth endMonth = YearMonth.from(rangeEnd);

        YearMonth current = startMonth;
        while (!current.isAfter(endMonth)) {
            if (dayOfMonth <= current.lengthOfMonth()) {
                LocalDate date = current.atDay(dayOfMonth);
                if (!date.isBefore(rangeStart) && !date.isAfter(rangeEnd)) {
                    result.add(date);
                }
            }
            // Bỏ qua nếu tháng không có ngày đó (ví dụ: 31/02)
            current = current.plusMonths(1);
        }

        return result;
    }

    /**
     * Hằng năm: vào ngày day_of_month/month_of_year của mỗi năm trong [rangeStart, rangeEnd].
     */
    private List<LocalDate> materializeYearly(Integer dayOfMonth, Integer monthOfYear,
                                               LocalDate rangeStart, LocalDate rangeEnd) {
        List<LocalDate> result = new ArrayList<>();
        if (dayOfMonth == null || monthOfYear == null) return result;

        int startYear = rangeStart.getYear();
        int endYear = rangeEnd.getYear();

        for (int year = startYear; year <= endYear; year++) {
            YearMonth ym = YearMonth.of(year, monthOfYear);
            if (dayOfMonth <= ym.lengthOfMonth()) {
                LocalDate date = ym.atDay(dayOfMonth);
                if (!date.isBefore(rangeStart) && !date.isAfter(rangeEnd)) {
                    result.add(date);
                }
            }
        }

        return result;
    }

    /**
     * Hằng quý: month_of_year là tháng bắt đầu chu kỳ.
     * Ví dụ: day=15, month=2 → 15/02, 15/05, 15/08, 15/11 (mỗi +3 tháng)
     */
    private List<LocalDate> materializeQuarterly(Integer dayOfMonth, Integer startMonth,
                                                   LocalDate rangeStart, LocalDate rangeEnd) {
        List<LocalDate> result = new ArrayList<>();
        if (dayOfMonth == null || startMonth == null) return result;

        // Bắt đầu từ năm của rangeStart, dò tháng đầu tiên thuộc chu kỳ
        int startYear = rangeStart.getYear() - 1; // lùi 1 năm để không bỏ sót
        YearMonth current = YearMonth.of(startYear, startMonth);

        // Giới hạn tối đa 20 năm để tránh vòng lặp vô hạn
        LocalDate limit = rangeEnd.plusYears(1);

        while (true) {
            if (dayOfMonth <= current.lengthOfMonth()) {
                LocalDate date = current.atDay(dayOfMonth);
                if (date.isAfter(rangeEnd) || date.isAfter(limit)) break;
                if (!date.isBefore(rangeStart)) {
                    result.add(date);
                }
            }
            current = current.plusMonths(3);
            if (current.atDay(1).isAfter(limit)) break;
        }

        return result;
    }

    /**
     * Tìm fallback impact rate cho event (dùng khi event chưa có lịch sử).
     * Vì ForecastEvent không biết channelId tại thời điểm build calendar,
     * lưu một giá trị bất kỳ làm fallback (EventImpactResolver sẽ tra theo channelId).
     * → Trả về null, để EventImpactResolver tự tra theo impactRates map.
     */
    private BigDecimal findFallbackRate(EventConfig eventConfig,
                                         Map<EventChannelKey, BigDecimal> impactRates) {
        // Không set fallback ở đây vì cần channelId cụ thể.
        // EventImpactResolver sẽ tra impactRates theo (eventId, channelId) khi cần.
        return null;
    }
}
