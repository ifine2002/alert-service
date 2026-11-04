package com.techasians.alertservice.service.forecast;

import com.techasians.alertservice.dto.forecast.EventCalendar;
import com.techasians.alertservice.dto.forecast.EventImpactPair;
import com.techasians.alertservice.dto.forecast.ForecastContext;
import com.techasians.alertservice.dto.forecast.ForecastDay;
import com.techasians.alertservice.exception.ForecastDataException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kiểm tra một occurrence có đủ dữ liệu để tính impact hay không.
 *
 * Nếu context hiện tại không đủ dữ liệu để tính baseline
 * (ví dụ occurrence nằm ngoài 12 tháng đã load), sẽ tự động load thêm từ DB.
 */
@Component
public class ImpactPairBuilder {

    private static final Logger log = LoggerFactory.getLogger(ImpactPairBuilder.class);

    private final NormalDayResolver normalDayResolver;
    private final NormalBaselineCalculator baselineCalculator;
    private final OccurrenceContextLoader contextLoader;

    public ImpactPairBuilder(NormalDayResolver normalDayResolver,
                             NormalBaselineCalculator baselineCalculator,
                             OccurrenceContextLoader contextLoader) {
        this.normalDayResolver = normalDayResolver;
        this.baselineCalculator = baselineCalculator;
        this.contextLoader = contextLoader;
    }

    /**
     * @param eventId         id event (không dùng trực tiếp ở đây nhưng để signature rõ ràng)
     * @param occurrenceDate  ngày occurrence cần tính
     * @param regionChannelId id region-channel để load dữ liệu bổ sung nếu cần
     * @param context         context chính của vòng forecast
     * @param calendar        EventCalendar
     */
    public Optional<EventImpactPair> build(Integer eventId,
                                           LocalDate occurrenceDate,
                                           Long regionChannelId,
                                           Integer channelId,
                                           ForecastContext context,
                                           EventCalendar calendar) {
        // Bước 1: kiểm tra có dữ liệu traffic cho ngày occurrence không
        ForecastDay occurrenceDay = context.get(occurrenceDate);
        boolean needsLoad = (occurrenceDay == null || occurrenceDay.getValue() == null);
        List<ForecastDay> normalDays = null;

        // Bước 2: thử tính baseline bằng context hiện có nếu đã có occurrence
        if (!needsLoad) {
            normalDays = tryResolveNormalDays(occurrenceDate, context, calendar, channelId);
            if (normalDays == null) {
                needsLoad = true;
            }
        }

        // Bước 3: nếu thiếu dữ liệu (occurrence hoặc normal days), load thêm từ DB
        if (needsLoad) {
            log.debug("[PAIR] {} | rcId={} | Thiếu dữ liệu (occurrence/normal days) → load thêm từ DB",
                    occurrenceDate, regionChannelId);
            context = contextLoader.loadAndMerge(occurrenceDate, regionChannelId, context);
            occurrenceDay = context.get(occurrenceDate);

            // Nếu load xong vẫn không có → DB thực sự không có data ngày này
            if (occurrenceDay == null || occurrenceDay.getValue() == null) {
                return Optional.empty();
            }
            normalDays = tryResolveNormalDays(occurrenceDate, context, calendar, channelId);
        }

        // Bước 4: vẫn không đủ sau khi load thêm → bỏ qua occurrence này
        if (normalDays == null || normalDays.isEmpty()) {
            return Optional.empty();
        }

        // Bước 5: tính baseline
        BigDecimal baseline;
        try {
            baseline = baselineCalculator.calculate(normalDays);
        } catch (ForecastDataException e) {
            return Optional.empty();
        }

        if (baseline == null || baseline.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        int eventCount = calendar.getEvents(occurrenceDate, channelId).size();
        boolean singleEventOnly = eventCount == 1;

        log.debug("[PAIR] {} | rcId={} | channelId={} | baseline={} | singleEvent={} | channelEventCount={}",
                occurrenceDate, regionChannelId, channelId, baseline, singleEventOnly, eventCount);

        return Optional.of(new EventImpactPair()
                .setOccurrenceDate(occurrenceDate)
                .setOccurrenceValue(occurrenceDay.getValue())
                .setBaselineValue(baseline)
                .setSingleEventOnly(singleEventOnly));
    }

    /**
     * Thử gọi NormalDayResolver, trả về null nếu không đủ dữ liệu.
     */
    private List<ForecastDay> tryResolveNormalDays(LocalDate occurrenceDate,
                                                    ForecastContext context,
                                                    EventCalendar calendar,
                                                    Integer channelId) {
        try {
            return normalDayResolver.resolve(occurrenceDate, context, calendar, channelId);
        } catch (ForecastDataException e) {
            return null;
        }
    }
}

