package com.techasians.alertservice.service.forecast;

import com.techasians.alertservice.dto.TrafficForecastChannelMeta;
import com.techasians.alertservice.dto.TrafficForecastDayChannelResult;
import com.techasians.alertservice.dto.TrafficForecastDayResponse;
import com.techasians.alertservice.dto.TrafficForecastDayRow;
import com.techasians.alertservice.dto.TrafficForecastRegionResult;
import com.techasians.alertservice.dto.TrafficForecastResponse;
import com.techasians.alertservice.dto.TrafficForecastTableResponse;
import com.techasians.alertservice.dto.forecast.EventCalendar;
import com.techasians.alertservice.dto.forecast.ForecastEvent;
import com.techasians.alertservice.entity.TrafficChannelKpi;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Chuyển đổi kết quả forecast (flat list group theo kênh) thành dạng bảng group theo Region → Days.
 *
 * Cấu trúc output:
 *   TrafficForecastTableResponse
 *     └─ regions[]
 *          ├─ regionId, regionName
 *          ├─ channels[] (metadata: regionChannelId, channelId, channelName)
 *          └─ days[]
 *               ├─ date, year, quarter, month, day, dayOfWeek
 *               └─ channelResults[] (regionChannelId, events[], impactRate, value, personnelCount)
 */
@Component
public class ForecastRowResponseMapper {

    /**
     * Chuyển đổi List<TrafficForecastResponse> thành TrafficForecastTableResponse.
     *
     * Luồng xử lý:
     *   1. Group các kênh theo regionId
     *   2. Với mỗi region: build danh sách channels (metadata) và danh sách days
     *   3. Với mỗi ngày: ghép channelResults từ tất cả kênh trong region đó
     *
     * @param channelResults kết quả forecast flat (output của ForecastResponseMapper)
     * @param calendar       lịch sự kiện để phân loại định kỳ / phát sinh
     * @param fromDate       ngày bắt đầu
     * @param toDate         ngày kết thúc
     */
    public TrafficForecastTableResponse mapToTable(
            List<TrafficForecastResponse> channelResults,
            EventCalendar calendar,
            Map<Integer, TrafficChannelKpi> kpiByChannelId,
            LocalDate fromDate,
            LocalDate toDate) {

        // Bước 1: Group các kênh theo regionId
        // Giữ thứ tự regionId xuất hiện đầu tiên trong danh sách
        Map<Long, List<TrafficForecastResponse>> channelsByRegion = new LinkedHashMap<>();
        for (TrafficForecastResponse channel : channelResults) {
            channelsByRegion
                    .computeIfAbsent(channel.getRegionId(), id -> new ArrayList<>())
                    .add(channel);
        }

        // Bước 2: Build từng region
        List<TrafficForecastRegionResult> regions = new ArrayList<>();
        for (Map.Entry<Long, List<TrafficForecastResponse>> entry : channelsByRegion.entrySet()) {
            Long regionId = entry.getKey();
            List<TrafficForecastResponse> regionChannels = entry.getValue();

            // Lấy tên region từ kênh đầu tiên (tất cả kênh cùng region có cùng tên)
            String regionName = regionChannels.get(0).getRegionName();

            // Build danh sách metadata kênh (chỉ có 1 lần, không lặp theo ngày)
            List<TrafficForecastChannelMeta> channelMetas = buildChannelMetas(regionChannels);

            // Build danh sách ngày với channelResults lồng bên trong
            List<TrafficForecastDayRow> days = buildDays(regionChannels, calendar, kpiByChannelId, fromDate, toDate);

            regions.add(new TrafficForecastRegionResult()
                    .setRegionId(regionId)
                    .setRegionName(regionName)
                    .setChannels(channelMetas)
                    .setDays(days));
        }

        return new TrafficForecastTableResponse().setRegions(regions);
    }

    /**
     * Build danh sách metadata kênh của một region.
     * Mỗi kênh chỉ xuất hiện 1 lần, FE dùng để build header bảng.
     */
    private List<TrafficForecastChannelMeta> buildChannelMetas(List<TrafficForecastResponse> regionChannels) {
        List<TrafficForecastChannelMeta> metas = new ArrayList<>();
        for (TrafficForecastResponse channel : regionChannels) {
            metas.add(new TrafficForecastChannelMeta()
                    .setRegionChannelId(channel.getRegionChannelId())
                    .setChannelId(channel.getChannelId())
                    .setChannelName(channel.getChannelName())
                    .setMetricName(channel.getMetricName())
                    .setActualCutoff(channel.getActualCutoff())
                    .setCalculationStart(channel.getCalculationStart())
                    .setDataQuality(channel.getDataQuality())
                    .setDataQualityNote(channel.getDataQualityNote())
                    .setGapDays(channel.getGapDays()));
        }
        return metas;
    }

    /**
     * Build danh sách hàng theo ngày cho một region.
     *
     * Với mỗi ngày trong [fromDate, toDate]:
     *   - Lấy thông tin ngày (Năm/Quý/Tháng/Ngày/Thứ/Sự kiện)
     *   - Ghép channelResults từ tất cả kênh trong region
     */
    private List<TrafficForecastDayRow> buildDays(
            List<TrafficForecastResponse> regionChannels,
            EventCalendar calendar,
            Map<Integer, TrafficChannelKpi> kpiByChannelId,
            LocalDate fromDate,
            LocalDate toDate) {

        // Tạo Map<date, dayRow> để tra cứu nhanh khi ghép channelResults
        // LinkedHashMap giữ thứ tự ngày tăng dần
        Map<LocalDate, TrafficForecastDayRow> dayRowByDate = new LinkedHashMap<>();
        for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
            dayRowByDate.put(date, buildEmptyDayRow(date));
        }

        // Ghép channelResults: duyệt từng kênh, từng ngày của kênh đó
        for (TrafficForecastResponse channel : regionChannels) {
            if (channel.getForecasts() == null) {
                continue;
            }

            for (TrafficForecastDayResponse dayResult : channel.getForecasts()) {
                TrafficForecastDayRow dayRow = dayRowByDate.get(dayResult.getDate());
                if (dayRow == null) {
                    // Ngày nằm ngoài range, bỏ qua
                    continue;
                }

                dayRow.getChannelResults().add(
                        buildDayChannelResult(channel, dayResult, calendar, kpiByChannelId));
            }
        }

        return new ArrayList<>(dayRowByDate.values());
    }

    /**
     * Tạo hàng ngày rỗng (chưa có channelResults) với thông tin cố định.
     * Sự kiện không nằm ở cấp ngày mà được lọc riêng khi tạo kết quả từng kênh.
     */
    private TrafficForecastDayRow buildEmptyDayRow(LocalDate date) {
        return new TrafficForecastDayRow()
                .setDate(date)
                .setYear(formatYear(date))
                .setQuarter(formatQuarter(date))
                .setMonth(formatMonth(date))
                .setDay(formatDay(date))
                .setDayOfWeek(formatDayOfWeek(date))
                .setChannelResults(new ArrayList<>());
    }

    /**
     * Build kết quả của 1 kênh trong 1 ngày.
     *
     * impactRate: nhân 100 để ra %, scale=1 (ví dụ 0.045 → 4.5).
     * personnelCount: tính theo công thức (value * kpiRate) / productivityValue, làm tròn lên (CEILING).
     *   - kpiRate và productivityValue lấy từ TrafficChannelKpi theo channelId.
     *   - Nếu không có KPI cấu hình cho kênh này thì personnelCount = 0.
     *   - Nếu value = null thì personnelCount = 0.
     */
    private TrafficForecastDayChannelResult buildDayChannelResult(
            TrafficForecastResponse channel,
            TrafficForecastDayResponse dayResult,
            EventCalendar calendar,
            Map<Integer, TrafficChannelKpi> kpiByChannelId) {

        BigDecimal impactRatePercent = null;
        if (dayResult.getImpactRate() != null) {
            impactRatePercent = dayResult.getImpactRate()
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP);
        }

        List<String> events = calendar.getEvents(dayResult.getDate(), channel.getChannelId()).stream()
                .map(ForecastEvent::getName)
                .collect(Collectors.toList());

        Integer personnelCount = calculatePersonnelCount(dayResult.getValue(), channel.getChannelId(), kpiByChannelId);

        return new TrafficForecastDayChannelResult()
                .setRegionChannelId(channel.getRegionChannelId())
                .setEvents(events)
                .setImpactRate(impactRatePercent)
                .setValue(dayResult.getValue())
                .setPersonnelCount(personnelCount);
    }

    /**
     * Tính quân số dự kiến theo công thức:
     *   Quân số = CEILING(value * kpiRate / productivityValue)
     *
     * Ví dụ:
     *   value = 15200, kpiRate = 0.85, productivityValue = 200
     *   → 15200 * 0.85 / 200 = 64.6 → làm tròn lên = 65
     *
     * Trả về null nếu kênh chưa có bản ghi KPI (chưa cấu hình).
     * Trả về 0 nếu:
     *   - value = null
     *   - productivityValue = null hoặc = 0 (tránh chia 0)
     */
    private Integer calculatePersonnelCount(BigDecimal value, Integer channelId,
                                            Map<Integer, TrafficChannelKpi> kpiByChannelId) {
        TrafficChannelKpi kpi = (channelId != null) ? kpiByChannelId.get(channelId) : null;

        // Không có bản ghi KPI cho kênh này → chưa cấu hình, trả null
        if (kpi == null) {
            return null;
        }

        // Đã có KPI nhưng value = null → trả 0
        if (value == null) {
            return 0;
        }

        // KpiRate hoặc productivityValue chưa điền → trả null (cấu hình chưa đủ)
        if (kpi.getKpiRate() == null || kpi.getProductivityValue() == null) {
            return null;
        }

        // Tránh chia cho 0
        if (kpi.getProductivityValue().compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }

        // Công thức: value * (kpiRate / 100) / productivityValue, làm tròn lên (CEILING)
        // kpiRate lưu dạng 0-100 (ví dụ: 85 nghĩa là 85%), cần chia 100 trước khi tính
        BigDecimal kpiRateDecimal = kpi.getKpiRate().divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
        BigDecimal result = value
                .multiply(kpiRateDecimal)
                .divide(kpi.getProductivityValue(), 0, RoundingMode.CEILING);

        return result.intValue();
    }

    // ========================
    // Các hàm format hiển thị
    // ========================

    /** "N2026" */
    private String formatYear(LocalDate date) {
        return "N" + date.getYear();
    }

    /** "Q3/2026" */
    private String formatQuarter(LocalDate date) {
        int quarter = date.get(IsoFields.QUARTER_OF_YEAR);
        return "Q" + quarter + "/" + date.getYear();
    }

    /** "T09", "T12" */
    private String formatMonth(LocalDate date) {
        return "T" + String.format("%02d", date.getMonthValue());
    }

    /** "N01", "N15" */
    private String formatDay(LocalDate date) {
        return "N" + String.format("%02d", date.getDayOfMonth());
    }

    /**
     * "T2" (Thứ Hai) → "T7" (Thứ Bảy) → "CN" (Chủ Nhật)
     * DayOfWeek.getValue(): MONDAY=1, ..., SATURDAY=6, SUNDAY=7
     */
    private String formatDayOfWeek(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SUNDAY) {
            return "CN";
        }
        // MONDAY=1 → "T2", TUESDAY=2 → "T3", ..., SATURDAY=6 → "T7"
        return "T" + (dow.getValue() + 1);
    }
}
