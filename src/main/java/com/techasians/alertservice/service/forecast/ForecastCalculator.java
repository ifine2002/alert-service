package com.techasians.alertservice.service.forecast;

import com.techasians.alertservice.dto.forecast.EventCalendar;
import com.techasians.alertservice.dto.forecast.EventChannelKey;
import com.techasians.alertservice.dto.forecast.EventImpactResult;
import com.techasians.alertservice.dto.forecast.ForecastBranch;
import com.techasians.alertservice.dto.forecast.ForecastContext;
import com.techasians.alertservice.dto.forecast.ForecastDay;
import com.techasians.alertservice.dto.forecast.ForecastEvent;
import com.techasians.alertservice.dto.forecast.ForecastSource;
import com.techasians.alertservice.dto.forecast.RegionChannelConfig;
import com.techasians.alertservice.exception.ForecastDataException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quyết định nhánh tính và thực hiện tính forecast cho mỗi ngày.
 *
 * Thứ tự ưu tiên:
 * 1. EVENT → forecastEventDay() (Nhánh B)
 * 2. SPECIAL DAY → forecastSpecialDay() (T2/T7/CN theo cấu hình channel)
 * 3. NORMAL → forecastNormalDay() (Nhánh A)
 *
 * Ví dụ: CN có event → đi nhánh EVENT, không đi rule ngày đặc biệt
 * Ví dụ: Thứ 2 + mondayAffected = true → đi rule ngày đặc biệt
 */
@Component
public class ForecastCalculator {

    private static final Logger log = LoggerFactory.getLogger(ForecastCalculator.class);

    private static final int INTERNAL_SCALE = 12;
    private static final RoundingMode INTERNAL_ROUNDING = RoundingMode.HALF_UP;

    private final NormalDayResolver normalDayResolver;
    private final NormalBaselineCalculator baselineCalculator;
    private final SpecialDayReferenceResolver specialDayReferenceResolver;
    private final EventImpactResolver eventImpactResolver;

    public ForecastCalculator(NormalDayResolver normalDayResolver,
            NormalBaselineCalculator baselineCalculator,
            SpecialDayReferenceResolver specialDayReferenceResolver,
            EventImpactResolver eventImpactResolver) {
        this.normalDayResolver = normalDayResolver;
        this.baselineCalculator = baselineCalculator;
        this.specialDayReferenceResolver = specialDayReferenceResolver;
        this.eventImpactResolver = eventImpactResolver;
    }

    /**
     * Tính forecast cho ngày N của một region-channel.
     *
     * @param date        ngày cần forecast
     * @param config      cấu hình region-channel (weekendAffected, channelId...)
     * @param context     ForecastContext chứa actual + forecast đã tính
     * @param calendar    EventCalendar biết ngày nào có event
     * @param impactRates Map (eventId, channelId) → impact rate đã chia 100
     * @return ForecastDay đã tính với value full precision (chưa round)
     */
    public ForecastDay calculate(LocalDate date, RegionChannelConfig config,
            ForecastContext context, EventCalendar calendar,
            Map<EventChannelKey, BigDecimal> impactRates) {

        List<ForecastEvent> events = calendar.getEvents(date, config.getChannelId());

        // Ưu tiên 1: Có event → Nhánh B
        if (!events.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("[FORECAST] {} | rcId={} | Nhánh B: EVENT ({})",
                        date, config.getRegionChannelId(),
                        events.stream().map(ForecastEvent::getName).collect(java.util.stream.Collectors.joining(", ")));
            }
            return forecastEventDay(date, events, config, context, calendar, impactRates);
        }

        // Ưu tiên 2: T2/T7/CN và channel có cấu hình áp dụng rule đặc biệt
        if (specialDayReferenceResolver.isSpecialDayRuleApplicable(
                date, config.isWeekendAffected(), config.isMondayAffected())) {
            if (log.isDebugEnabled()) {
                log.debug("[FORECAST] {} | rcId={} | Nhánh đặc biệt: SPECIAL DAY ({})",
                        date, config.getRegionChannelId(), date.getDayOfWeek());
            }
            return forecastSpecialDay(date, config, context, calendar);
        }

        // Ưu tiên 3: Ngày thường → Nhánh A
        if (log.isDebugEnabled()) {
            log.debug("[FORECAST] {} | rcId={} | Nhánh A: NORMAL", date, config.getRegionChannelId());
        }
        return forecastNormalDay(date, config, context, calendar);
    }

    // ======== NHÁNH A: Ngày thường ========

    private ForecastDay forecastNormalDay(LocalDate date, RegionChannelConfig config,
            ForecastContext context, EventCalendar calendar) {
        BigDecimal baseline;
        int normalDayCount = 0;
        try {
            List<ForecastDay> normalDays = normalDayResolver.resolve(date, context, calendar, config.getChannelId());
            normalDayCount = normalDays.size();
            for (ForecastDay normalDay : normalDays) {
                log.debug(
                        "[NORMAL-DAY-USED] targetDate={} | rcId={} | usedDate={} | value={} | source={} | hasEvent={}",
                        date,
                        config.getRegionChannelId(),
                        normalDay.getDate(),
                        normalDay.getValue(),
                        normalDay.getSource(),
                        normalDay.isHasEvent());
            }
            baseline = baselineCalculator.calculate(normalDays);
        } catch (ForecastDataException e) {
            log.warn("[NORMAL-FALLBACK] {} | rcId={} | reason={}", date, config.getRegionChannelId(), e.getMessage());
            return fallback(date, config, context, null, false, Collections.emptyList());
        }

        if (baseline == null) {
            return fallback(date, config, context, null, false, Collections.emptyList());
        }

        BigDecimal value = baseline.max(BigDecimal.ZERO);
        if (log.isDebugEnabled()) {
            log.debug("[NORMAL] {} | rcId={} | baseline={} | normalDays={}",
                    date, config.getRegionChannelId(), value, normalDayCount);
        }

        return new ForecastDay(date, value, ForecastSource.FORECAST, false, Collections.emptyList())
                .setBaselineValue(baseline)
                .setBranch(ForecastBranch.NORMAL);
    }

    // ======== Ngày đặc biệt: T2/T7/CN ========

    private ForecastDay forecastSpecialDay(LocalDate date, RegionChannelConfig config,
            ForecastContext context, EventCalendar calendar) {
        DayOfWeek dow = date.getDayOfWeek();
        LocalDate baseDate = date.minusDays(1);
        ForecastDay baseDay = context.get(baseDate);
        if (baseDay == null || baseDay.getValue() == null) {
            return fallback(date, config, context, null, false, Collections.emptyList());
        }

        Optional<SpecialDayReferenceResolver.SpecialDayReference> optionalRef = specialDayReferenceResolver
                .findReferencePair(date, context, calendar, config.getChannelId());
        if (!optionalRef.isPresent()) {
            return fallback(date, config, context, baseDay.getValue(), false, Collections.emptyList());
        }
        SpecialDayReferenceResolver.SpecialDayReference ref = optionalRef.get();

        ForecastDay refA = context.get(ref.getRefA());
        ForecastDay refB = context.get(ref.getRefB());
        if (refA == null || refA.getValue() == null || refB == null || refB.getValue() == null
                || refB.getValue().compareTo(BigDecimal.ZERO) <= 0) {
            return fallback(date, config, context, baseDay.getValue(), false, Collections.emptyList());
        }

        BigDecimal baseValue = resolveSpecialBaseValue(baseDay, config, date);
        BigDecimal rate = refA.getValue().subtract(refB.getValue())
                .divide(refB.getValue(), INTERNAL_SCALE, INTERNAL_ROUNDING);
        BigDecimal clampedRate = clampImpactRate(rate);
        BigDecimal value = baseValue.multiply(BigDecimal.ONE.add(clampedRate));

        if (log.isDebugEnabled()) {
            log.debug(
                    "[SPECIAL] {} | rcId={} | {} | refA={} ({}) | refB={} ({}) | rate={} | clampedRate={} | base={} | result={}",
                    date, config.getRegionChannelId(), dow,
                    ref.getRefA(), refA.getValue(), ref.getRefB(), refB.getValue(),
                    rate, clampedRate, baseValue, value);
        }

        return new ForecastDay(date, value, ForecastSource.FORECAST, false, Collections.emptyList())
                .setBaselineValue(baseValue)
                .setImpactRate(clampedRate)
                .setBranch(ForecastBranch.SPECIAL);
    }

    private BigDecimal resolveSpecialBaseValue(ForecastDay baseDay, RegionChannelConfig config, LocalDate date) {
        if (baseDay.isHasEvent()) {
            if (baseDay.getBaselineValue() != null) {
                return baseDay.getBaselineValue();
            }
            log.warn("[SPECIAL-BASE] {} | rcId={} | base day has event but baselineValue is null",
                    date, config.getRegionChannelId());
        }
        return baseDay.getValue();
    }

    // ======== NHÁNH B: Ngày có sự kiện ========

    private ForecastDay forecastEventDay(LocalDate date, List<ForecastEvent> events,
            RegionChannelConfig config,
            ForecastContext context, EventCalendar calendar,
            Map<EventChannelKey, BigDecimal> impactRates) {
        List<Integer> eventIds = events.stream()
                .map(ForecastEvent::getId)
                .collect(Collectors.toList());

        BigDecimal baseline;
        try {
            List<ForecastDay> normalDays = normalDayResolver.resolve(date, context, calendar, config.getChannelId());
            baseline = baselineCalculator.calculate(normalDays);
        } catch (ForecastDataException e) {
            log.warn("[EVENT-FALLBACK] {} | rcId={} | reason={}", date, config.getRegionChannelId(), e.getMessage());
            return fallback(date, config, context, null, true, eventIds);
        }

        if (baseline == null) {
            return fallback(date, config, context, null, true, eventIds);
        }

        BigDecimal totalImpact = BigDecimal.ZERO;
        boolean fallbackOccurrenceUsed = false;
        for (ForecastEvent event : events) {
            EventImpactResult impactResult = eventImpactResolver.resolveImpact(
                    event, date, config.getChannelId(), config.getRegionChannelId(), context, calendar, impactRates);
            BigDecimal impact = impactResult.getImpactRate();
            if (impactResult.isFallbackUsed()) {
                fallbackOccurrenceUsed = true;
            }
            if (log.isDebugEnabled()) {
                log.debug("[EVENT] {} | rcId={} | event='{}' | impact={}",
                        date, config.getRegionChannelId(), event.getName(), impact);
            }
            totalImpact = totalImpact.add(impact);
        }

        BigDecimal clampedTotalImpact = clampImpactRate(totalImpact);
        BigDecimal value = baseline.multiply(BigDecimal.ONE.add(clampedTotalImpact));

        if (log.isDebugEnabled()) {
            log.debug("[EVENT] {} | rcId={} | baseline={} | totalImpact={} | clampedTotalImpact={} | result={}",
                    date, config.getRegionChannelId(), baseline, totalImpact, clampedTotalImpact, value);
        }

        ForecastSource source = fallbackOccurrenceUsed ? ForecastSource.FALLBACK : ForecastSource.FORECAST;
        return new ForecastDay(date, value, source, true, eventIds)
                .setBaselineValue(baseline)
                .setImpactRate(clampedTotalImpact)
                .setBranch(ForecastBranch.EVENT);
    }

    private BigDecimal clampImpactRate(BigDecimal impactRate) {
        if (impactRate.compareTo(BigDecimal.valueOf(-1)) <= 0) {
            return BigDecimal.ZERO;
        }
        return impactRate;
    }

    private ForecastDay fallback(LocalDate date, RegionChannelConfig config,
            ForecastContext context, BigDecimal preferredValue,
            boolean hasEvent, List<Integer> eventIds) {
        BigDecimal value = preferredValue;
        if (value == null) {
            ForecastDay previousDay = context.get(date.minusDays(1));
            if (previousDay != null) {
                value = previousDay.getValue();
            }
        }

        if (value == null) {
            log.error("[FALLBACK-SKIP] {} | rcId={} | Không có baseline hoặc ngày N-1", date,
                    config.getRegionChannelId());
            return null;
        }

        value = value.max(BigDecimal.ZERO);
        log.warn("[FALLBACK] {} | rcId={} | value={}", date, config.getRegionChannelId(), value);

        return new ForecastDay(date, value, ForecastSource.FALLBACK, hasEvent, eventIds)
                .setBaselineValue(value)
                .setImpactRate(BigDecimal.ZERO);
    }
}
