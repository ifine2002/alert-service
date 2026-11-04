package com.techasians.alertservice.dto.forecast;

import com.techasians.alertservice.entity.TrafficChannelKpi;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Đầu vào tổng hợp cho quá trình tính forecast.
 * Được load một lần từ DB trước vòng lặp tính toán.
 * Toàn bộ vòng tính forecast chỉ đọc từ object này, KHÔNG query DB thêm.
 */
public class ForecastInputData {

    // Ngày actual cuối cùng trong hệ thống - forecast bắt đầu từ ngày hôm sau
    private LocalDate actualCutoff;

    // Danh sách region-channel cần forecast (đã filter theo request)
    private List<RegionChannelConfig> regionChannels;

    // Dữ liệu actual 12 tháng gần nhất (metric "Cuộc gọi")
    private List<TrafficActualData> historicalTraffic;

    // Cấu hình sự kiện + schedules
    private List<EventConfig> events;

    // Impact rate theo (eventId, channelId) - đã chia 100, dạng 0.0-1.0
    // Dùng khi event chưa từng có historical occurrence
    private Map<EventChannelKey, BigDecimal> impactRates;

    // KPI theo channelId - dùng để tính quân số dự kiến: (value * kpiRate) / productivityValue
    private Map<Integer, TrafficChannelKpi> kpiByChannelId;

    public ForecastInputData() {
    }

    public LocalDate getActualCutoff() {
        return actualCutoff;
    }

    public ForecastInputData setActualCutoff(LocalDate actualCutoff) {
        this.actualCutoff = actualCutoff;
        return this;
    }

    public List<RegionChannelConfig> getRegionChannels() {
        return regionChannels;
    }

    public ForecastInputData setRegionChannels(List<RegionChannelConfig> regionChannels) {
        this.regionChannels = regionChannels;
        return this;
    }

    public List<TrafficActualData> getHistoricalTraffic() {
        return historicalTraffic;
    }

    public ForecastInputData setHistoricalTraffic(List<TrafficActualData> historicalTraffic) {
        this.historicalTraffic = historicalTraffic;
        return this;
    }

    public List<EventConfig> getEvents() {
        return events;
    }

    public ForecastInputData setEvents(List<EventConfig> events) {
        this.events = events;
        return this;
    }

    public Map<EventChannelKey, BigDecimal> getImpactRates() {
        return impactRates;
    }

    public ForecastInputData setImpactRates(Map<EventChannelKey, BigDecimal> impactRates) {
        this.impactRates = impactRates;
        return this;
    }

    public Map<Integer, TrafficChannelKpi> getKpiByChannelId() {
        return kpiByChannelId;
    }

    public ForecastInputData setKpiByChannelId(Map<Integer, TrafficChannelKpi> kpiByChannelId) {
        this.kpiByChannelId = kpiByChannelId;
        return this;
    }
}
