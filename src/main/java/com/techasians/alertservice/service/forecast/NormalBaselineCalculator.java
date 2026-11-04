package com.techasians.alertservice.service.forecast;

import com.techasians.alertservice.dto.forecast.ForecastDay;
import com.techasians.alertservice.exception.ForecastDataException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Tính giá trị baseline ngày thường = trung bình cộng của tập normal days.
 *
 * Giữ full precision trong nội bộ, dùng INTERNAL_SCALE khi chia.
 * KHÔNG round ở đây - chỉ round ở ForecastResponseMapper cuối cùng.
 */
@Component
public class NormalBaselineCalculator {

    // Scale nội bộ để tránh ArithmeticException khi chia không hết
    private static final int INTERNAL_SCALE = 12;
    private static final RoundingMode INTERNAL_ROUNDING = RoundingMode.HALF_UP;

    /**
     * Tính AVG của danh sách normal days.
     *
     * @param normalDays danh sách ngày thường tham chiếu (đã lọc bỏ ngày có event)
     * @return giá trị baseline với full precision, hoặc null nếu không có ngày tham chiếu
     */
    public BigDecimal calculate(List<ForecastDay> normalDays) {
        if (normalDays == null || normalDays.isEmpty()) {
            return null;
        }

        // Tính tổng
        BigDecimal sum = BigDecimal.ZERO;
        for (ForecastDay day : normalDays) {
            if (day.getValue() == null) {
                throw new ForecastDataException(
                        "Ngày " + day.getDate() + " thiếu giá trị lưu lượng."
                );
            }
            sum = sum.add(day.getValue());
        }

        // Chia lấy trung bình - giữ INTERNAL_SCALE để không mất precision
        return sum.divide(BigDecimal.valueOf(normalDays.size()), INTERNAL_SCALE, INTERNAL_ROUNDING);
    }
}
