package com.techasians.alertservice.service.forecast;

import com.techasians.alertservice.dto.forecast.EventCalendar;
import com.techasians.alertservice.dto.forecast.EventChannelKey;
import com.techasians.alertservice.dto.forecast.EventImpactPair;
import com.techasians.alertservice.dto.forecast.EventImpactResult;
import com.techasians.alertservice.dto.forecast.ForecastContext;
import com.techasians.alertservice.dto.forecast.ForecastEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tính % impact của một sự kiện cho ngày cần forecast.
 *
 * Rule mới:
 * - Tìm occurrence qua EventOccurrenceResolver, không phụ thuộc EventCalendar build quá xa.
 * - Ưu tiên occurrence mà ngày đó chỉ có một mình event đang xét.
 * - Nếu không có single-event pair, fallback về pair gần nhất dù ngày đó có nhiều event.
 * - Nếu không có pair hợp lệ, dùng impact_rate cấu hình.
 */
@Component
public class EventImpactResolver {

    private static final Logger log = LoggerFactory.getLogger(EventImpactResolver.class);

    private static final int INTERNAL_SCALE = 12;
    private static final RoundingMode INTERNAL_ROUNDING = RoundingMode.HALF_UP;

    private final EventOccurrenceResolver occurrenceResolver;
    private final ImpactPairBuilder impactPairBuilder;

    public EventImpactResolver(EventOccurrenceResolver occurrenceResolver,
                               ImpactPairBuilder impactPairBuilder) {
        this.occurrenceResolver = occurrenceResolver;
        this.impactPairBuilder = impactPairBuilder;
    }

    public EventImpactResult resolveImpact(ForecastEvent event, LocalDate dateN, Integer channelId,
                                           Long regionChannelId,
                                           ForecastContext context, EventCalendar calendar,
                                           Map<EventChannelKey, BigDecimal> impactRates) {
        List<LocalDate> occurrences = occurrenceResolver.findPreviousOccurrences(
                event, dateN, regionChannelId, context, calendar
        );

        EventImpactPair fallbackPair = null;
        int validPairCount = 0;

        for (LocalDate occurrenceDate : occurrences) {
            Optional<EventImpactPair> optionalPair = impactPairBuilder.build(
                    event.getId(), occurrenceDate, regionChannelId, channelId, context, calendar
            );

            if (!optionalPair.isPresent()) {
                continue;
            }

            EventImpactPair pair = optionalPair.get();
            validPairCount++;

            if (fallbackPair == null) {
                fallbackPair = pair;
            }

            if (pair.isSingleEventOnly()) {
                BigDecimal impact = calculateImpact(pair, dateN);
                if (log.isDebugEnabled()) {
                    log.debug("[IMPACT] event='{}' | occurrence={} | SINGLE-EVENT | impact={}",
                            event.getName(), occurrenceDate, impact);
                }
                return new EventImpactResult(impact, false);
            }
        }

        if (fallbackPair != null) {
            BigDecimal impact = calculateImpact(fallbackPair, dateN);
            if (log.isDebugEnabled()) {
                log.debug("[IMPACT] event='{}' | occurrence={} | MULTI-EVENT FALLBACK | impact={}",
                        event.getName(), fallbackPair.getOccurrenceDate(), impact);
            }
            return new EventImpactResult(impact, true);
        }

        if (log.isDebugEnabled()) {
            log.debug("[IMPACT] event='{}' | dateN={} | NO PAIR FOUND → dùng impact_rate cấu hình",
                    event.getName(), dateN);
        }
        return new EventImpactResult(getConfiguredImpactRate(event, channelId, impactRates, dateN), false);
    }

    private BigDecimal calculateImpact(EventImpactPair pair, LocalDate dateN) {
        BigDecimal baseline = pair.getBaselineValue();
        if (baseline.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[IMPACT-SKIP] occurrence={} | baseline <= 0 | dateN={}", pair.getOccurrenceDate(), dateN);
            return BigDecimal.ZERO;
        }

        BigDecimal diff = pair.getOccurrenceValue().subtract(baseline);
        return diff.divide(baseline, INTERNAL_SCALE, INTERNAL_ROUNDING);
    }

    private BigDecimal getConfiguredImpactRate(ForecastEvent event, Integer channelId,
                                                 Map<EventChannelKey, BigDecimal> impactRates,
                                                 LocalDate dateN) {
        EventChannelKey key = new EventChannelKey(event.getId(), channelId);
        BigDecimal rate = impactRates.get(key);

        if (rate == null) {
            if (event.getConfiguredImpactRate() != null) {
                return event.getConfiguredImpactRate();
            }
            log.warn("[IMPACT-CONFIG-MISSING] event='{}' | channelId={} | dateN={} | dùng impact=0",
                    event.getName(), channelId, dateN);
            return BigDecimal.ZERO;
        }

        return rate;
    }
}
