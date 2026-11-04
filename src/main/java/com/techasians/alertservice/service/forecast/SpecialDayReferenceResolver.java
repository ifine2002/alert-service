package com.techasians.alertservice.service.forecast;

import com.techasians.alertservice.dto.forecast.EventCalendar;
import com.techasians.alertservice.dto.forecast.ForecastContext;
import com.techasians.alertservice.dto.forecast.ForecastDay;
import com.techasians.alertservice.dto.forecast.ForecastSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Tìm cặp tham chiếu cho ngày đặc biệt: Thứ 2, Thứ 7, Chủ nhật.
 */
@Component
public class SpecialDayReferenceResolver {

    private static final int MAX_EVENT_FREE_PAIRS = 12;
    private static final int MAX_FALLBACK_PAIRS = 52;

    public static class SpecialDayReference {
        private final LocalDate refA;
        private final LocalDate refB;

        public SpecialDayReference(LocalDate refA, LocalDate refB) {
            this.refA = refA;
            this.refB = refB;
        }

        public LocalDate getRefA() { return refA; }
        public LocalDate getRefB() { return refB; }
    }

    public boolean isSpecialDayRuleApplicable(LocalDate date, boolean weekendAffected, boolean mondayAffected) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.MONDAY) {
            return mondayAffected;
        }
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return weekendAffected;
        }
        return false;
    }

    public Optional<SpecialDayReference> findReferencePair(LocalDate dateN, ForecastContext context, EventCalendar calendar, Integer channelId) {
        Optional<SpecialDayReference> actualReference = findActualEventFreeReference(dateN, context, calendar, channelId);
        if (actualReference.isPresent()) {
            return actualReference;
        }

        LocalDate refA = dateN.minusWeeks(1);
        LocalDate refB = refA.minusDays(1);
        for (int count = 0; count < MAX_FALLBACK_PAIRS; count++) {
            if (hasValidData(refA, refB, context)) {
                return Optional.of(new SpecialDayReference(refA, refB));
            }
            refA = refA.minusWeeks(1);
            refB = refB.minusWeeks(1);
        }

        return Optional.empty();
    }

    private Optional<SpecialDayReference> findActualEventFreeReference(LocalDate dateN,
                                                                       ForecastContext context,
                                                                       EventCalendar calendar,
                                                                       Integer channelId) {
        LocalDate refA = dateN.minusWeeks(1);
        LocalDate refB = refA.minusDays(1);

        for (int count = 0; count < MAX_EVENT_FREE_PAIRS; count++) {
            if (!calendar.hasEvent(refA, channelId) && !calendar.hasEvent(refB, channelId)
                    && hasActualData(refA, refB, context)) {
                return Optional.of(new SpecialDayReference(refA, refB));
            }
            refA = refA.minusWeeks(1);
            refB = refB.minusWeeks(1);
        }

        return Optional.empty();
    }

    private boolean hasActualData(LocalDate refA, LocalDate refB, ForecastContext context) {
        ForecastDay dayA = context.get(refA);
        ForecastDay dayB = context.get(refB);
        return hasValidData(dayA, dayB)
                && dayA.getSource() == ForecastSource.ACTUAL
                && dayB.getSource() == ForecastSource.ACTUAL;
    }

    private boolean hasValidData(LocalDate refA, LocalDate refB, ForecastContext context) {
        ForecastDay dayA = context.get(refA);
        ForecastDay dayB = context.get(refB);
        return hasValidData(dayA, dayB);
    }

    private boolean hasValidData(ForecastDay dayA, ForecastDay dayB) {
        return dayA != null
                && dayA.getValue() != null
                && dayB != null
                && dayB.getValue() != null
                && dayB.getValue().compareTo(BigDecimal.ZERO) > 0;
    }
}

