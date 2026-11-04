package com.techasians.alertservice.service.forecast;

import com.techasians.alertservice.dto.forecast.ForecastContext;
import com.techasians.alertservice.dto.forecast.ForecastDay;
import com.techasians.alertservice.dto.forecast.ForecastSource;
import com.techasians.alertservice.repository.TrafficHistoryRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Load th?m d? li?u actual t? DB v?o m?t ForecastContext t?m th?i,
 * d?ng khi c?n t?nh baseline cho occurrence n?m ngo?i kho?ng context ch?nh.
 *
 * Ch? load khi th?c s? c?n (lazy load), kh?ng load tr??c.
 */
@Component
public class OccurrenceContextLoader {

    // Số tháng lấy trước occurrenceDate để có đủ dữ liệu tính baseline cho occurrence event
    private static final int LOOKBACK_MONTHS = 36;

    private final TrafficHistoryRepository historyRepository;

    public OccurrenceContextLoader(TrafficHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    /**
     * T?o m?t ForecastContext t?m th?i ?? d? li?u cho ng?y occurrenceDate.
     *
     * Chi?n l??c:
     * - B?t ??u t? occurrenceDate - LOOKBACK_MONTHS th?ng.
     * - K?t th?c t?i occurrenceDate (bao g?m c? ng?y occurrence ?? c? gi? tr? traffic ng?y ??).
     * - Ch? l?y cho regionChannelId c? th?.
     * - Merge d? li?u n?y v?o existingContext ?? context m? r?ng c? ?? data.
     *
     * @param occurrenceDate ng?y occurrence c?n t?nh baseline
     * @param regionChannelId region-channel c?n d? li?u
     * @param existingContext context hi?n t?i (s? kh?ng b? thay ??i)
     * @return context m?i ?? merge th?m d? li?u c? v?o
     */
    public ForecastContext loadAndMerge(LocalDate occurrenceDate,
                                         Long regionChannelId,
                                         ForecastContext existingContext) {
        LocalDate fromDate = occurrenceDate.minusMonths(LOOKBACK_MONTHS);
        LocalDate toDate = occurrenceDate;

        List<Object[]> projections = historyRepository.findActualCallTraffic(
                fromDate, toDate, Collections.singletonList(regionChannelId)
        );

        // Tạo context mới và copy toàn bộ data hiện có vào
        ForecastContext mergedContext = new ForecastContext();
        for (ForecastDay day : existingContext.getAllDays()) {
            mergedContext.put(day);
        }

        // Thêm dữ liệu mới load (chỉ thêm nếu chưa có, tránh ghi đè forecast đã tính)
        for (Object[] row : projections) {
            LocalDate date = ((java.sql.Date) row[1]).toLocalDate();
            if (!mergedContext.contains(date)) {
                BigDecimal value = (BigDecimal) row[2];
                Number hasEventNum = (Number) row[3];
                boolean hasEvent = hasEventNum != null && hasEventNum.intValue() == 1;
                
                mergedContext.put(new ForecastDay(
                        date,
                        value,
                        ForecastSource.ACTUAL,
                        hasEvent,
                        null
                ));
            }
        }

        return mergedContext;
    }
}
