package com.techasians.alertservice.dto.forecast;

import java.time.LocalDate;
import java.util.Collection;
import java.util.TreeMap;

/**
 * Context chứa toàn bộ dữ liệu lưu lượng (actual + forecast) của một region-channel.
 *
 * Dùng TreeMap để tự động sắp xếp theo ngày tăng dần.
 * Mỗi region-channel có một ForecastContext riêng biệt.
 *
 * Luồng sử dụng:
 * 1. Nạp tất cả actual data vào khi khởi tạo (source = ACTUAL).
 * 2. Sau khi tính xong forecast ngày N → put(N) vào context.
 * 3. Khi tính ngày N+1 → context đã có N để sử dụng.
 */
public class ForecastContext {

    // TreeMap tự sort theo ngày - quan trọng để lùi tháng/tuần tìm baseline
    private final TreeMap<LocalDate, ForecastDay> days = new TreeMap<>();

    // Lấy dữ liệu ngày cụ thể (trả về null nếu không có)
    public ForecastDay get(LocalDate date) {
        return days.get(date);
    }

    // Lưu một ngày vào context (actual hoặc forecast)
    public void put(ForecastDay day) {
        days.put(day.getDate(), day);
    }

    // Kiểm tra đã có dữ liệu ngày đó chưa
    public boolean contains(LocalDate date) {
        return days.containsKey(date);
    }

    // Lấy tất cả ngày (đã sắp xếp tăng dần)
    public Collection<ForecastDay> getAllDays() {
        return days.values();
    }

    // Lấy ngày gần nhất trước date (dùng để lùi tìm occurrence của event)
    public LocalDate getPreviousDate(LocalDate date) {
        return days.lowerKey(date);
    }
}
