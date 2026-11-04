package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.dto.TrafficForecastRequest;
import com.techasians.alertservice.dto.TrafficForecastResponse;
import com.techasians.alertservice.dto.TrafficForecastTableResponse;
import com.techasians.alertservice.dto.forecast.EventCalendar;
import com.techasians.alertservice.dto.forecast.ForecastContext;
import com.techasians.alertservice.dto.forecast.ForecastDay;
import com.techasians.alertservice.dto.forecast.ForecastInputData;
import com.techasians.alertservice.dto.forecast.ForecastSource;
import com.techasians.alertservice.dto.forecast.RegionChannelConfig;
import com.techasians.alertservice.dto.forecast.TrafficActualData;
import com.techasians.alertservice.repository.TrafficHistoryRepository;
import com.techasians.alertservice.service.TrafficForecastService;
import com.techasians.alertservice.service.forecast.EventCalendarBuilder;
import com.techasians.alertservice.service.forecast.ForecastCalculator;
import com.techasians.alertservice.service.forecast.ForecastDataLoader;
import com.techasians.alertservice.service.forecast.ForecastResponseMapper;
import com.techasians.alertservice.service.forecast.ForecastRowResponseMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation chính của chức năng dự trù lưu lượng.
 *
 * Flow:
 *   1. Validate request (fromDate, toDate, max 3 tháng)
 *   2. Load toàn bộ data từ DB (5 query, KHÔNG query trong vòng lặp)
 *   3. Build ForecastContext ban đầu từ actual data (mỗi region-channel có context riêng)
 *   4. Build EventCalendar (lùi 36 tháng để tìm occurrence)
 *   5. Tính forecast TUẦN TỰ từng ngày từ calculationStart → toDate
 *      (KHÔNG parallel - đảm bảo forecast-of-forecast đúng)
 *   6. Map response: group theo Region → channels metadata + days
 */
@Service
@Transactional(readOnly = true)
public class TrafficForecastServiceImpl implements TrafficForecastService {

    private static final Logger log = LoggerFactory.getLogger(TrafficForecastServiceImpl.class);

    private final TrafficHistoryRepository historyRepo;
    private final ForecastDataLoader forecastDataLoader;
    private final EventCalendarBuilder eventCalendarBuilder;
    private final ForecastCalculator forecastCalculator;
    private final ForecastResponseMapper responseMapper;
    private final ForecastRowResponseMapper rowResponseMapper;

    public TrafficForecastServiceImpl(TrafficHistoryRepository historyRepo,
                                       ForecastDataLoader forecastDataLoader,
                                       EventCalendarBuilder eventCalendarBuilder,
                                       ForecastCalculator forecastCalculator,
                                       ForecastResponseMapper responseMapper,
                                       ForecastRowResponseMapper rowResponseMapper) {
        this.historyRepo = historyRepo;
        this.forecastDataLoader = forecastDataLoader;
        this.eventCalendarBuilder = eventCalendarBuilder;
        this.forecastCalculator = forecastCalculator;
        this.responseMapper = responseMapper;
        this.rowResponseMapper = rowResponseMapper;
    }

    @Override
    public TrafficForecastTableResponse forecast(TrafficForecastRequest request) {
        long t0 = System.nanoTime();

        // Bước 1: Validate
        validateRequest(request);

        // Bước 2: Load data và tính actualCutoff riêng cho từng region-channel
        ForecastInputData input = forecastDataLoader.load(request);
        long t1 = System.nanoTime();
        log.warn("[PERF] Bước 2 - Load DB: {}ms", (t1 - t0) / 1_000_000);

        if (input.getRegionChannels().isEmpty()) {
            return new TrafficForecastTableResponse().setRegions(Collections.emptyList());
        }

        // Bước 3: Build ForecastContext ban đầu từ actual data
        Map<Long, ForecastContext> contexts = buildInitialContexts(input);
        long t2 = System.nanoTime();
        log.warn("[PERF] Bước 3 - Build contexts: {}ms", (t2 - t1) / 1_000_000);

        // Bước 4: Build EventCalendar
        // Lùi 36 tháng để tìm occurrence event gần nhất theo đặc tả
        LocalDate earliestCalculationStart = findEarliestCalculationStart(input.getRegionChannels());
        LocalDate calendarStart = earliestCalculationStart != null
                ? earliestCalculationStart.minusMonths(36)
                : request.getFromDate().minusMonths(36);
        EventCalendar calendar = eventCalendarBuilder.build(input, calendarStart, request.getToDate());
        long t3 = System.nanoTime();
        log.warn("[PERF] Bước 4 - Build EventCalendar: {}ms", (t3 - t2) / 1_000_000);

        // Bước 5: Tính song song từng kênh — mỗi kênh có ForecastContext riêng nên thread-safe.
        // Vòng lặp ngày bên trong VẪN tuần tự vì ngày N+1 phụ thuộc kết quả ngày N.
        input.getRegionChannels().parallelStream()
                .filter(config -> !config.isSkipped() && config.getCalculationStart() != null)
                .forEach(config -> {
                    ForecastContext context = contexts.get(config.getRegionChannelId());
                    for (LocalDate date = config.getCalculationStart();
                         !date.isAfter(request.getToDate());
                         date = date.plusDays(1)) {
                        ForecastDay forecastDay = forecastCalculator.calculate(
                                date, config, context, calendar, input.getImpactRates()
                        );
                        if (forecastDay != null) {
                            context.put(forecastDay);
                        }
                    }
                });
        long t4 = System.nanoTime();
        log.warn("[PERF] Bước 5 - Tính forecast ({} kênh): {}ms", input.getRegionChannels().size(), (t4 - t3) / 1_000_000);

        // Bước 6: Map sang flat list (group theo kênh, round scale=0)
        List<TrafficForecastResponse> channelResults = responseMapper.map(
                contexts, input, calendar, request.getFromDate(), request.getToDate()
        );
        long t5 = System.nanoTime();
        log.warn("[PERF] Bước 6 - Map response: {}ms", (t5 - t4) / 1_000_000);

        // Bước 7: Chuyển flat list → TrafficForecastTableResponse (group theo region → days)
        TrafficForecastTableResponse result = rowResponseMapper.mapToTable(
                channelResults,
                calendar,
                input.getKpiByChannelId(),
                request.getFromDate(),
                request.getToDate()
        );
        long t6 = System.nanoTime();
        log.warn("[PERF] Bước 7 - Map table: {}ms", (t6 - t5) / 1_000_000);
        log.warn("[PERF] TỔNG: {}ms", (t6 - t0) / 1_000_000);

        return result;
    }

    private LocalDate findEarliestCalculationStart(List<RegionChannelConfig> regionChannels) {
        LocalDate earliest = null;
        for (RegionChannelConfig config : regionChannels) {
            if (config.isSkipped() || config.getCalculationStart() == null) {
                continue;
            }

            if (earliest == null || config.getCalculationStart().isBefore(earliest)) {
                earliest = config.getCalculationStart();
            }
        }
        return earliest;
    }

    /**
     * Build ForecastContext ban đầu, nạp toàn bộ actual data vào.
     * Mỗi region-channel có một ForecastContext riêng biệt.
     */
    private Map<Long, ForecastContext> buildInitialContexts(ForecastInputData input) {
        // Tạo context rỗng cho mỗi region-channel
        Map<Long, ForecastContext> contexts = new HashMap<>();
        for (RegionChannelConfig config : input.getRegionChannels()) {
            contexts.put(config.getRegionChannelId(), new ForecastContext());
        }

        // Group actual data theo regionChannelId
        Map<Long, List<TrafficActualData>> dataByRc = input.getHistoricalTraffic().stream()
                .collect(Collectors.groupingBy(TrafficActualData::getRegionChannelId));

        // Nạp actual data vào từng context
        for (Map.Entry<Long, List<TrafficActualData>> entry : dataByRc.entrySet()) {
            Long regionChannelId = entry.getKey();
            ForecastContext context = contexts.get(regionChannelId);
            if (context == null) continue; // region-channel không nằm trong request filter

            for (TrafficActualData data : entry.getValue()) {
                ForecastDay day = new ForecastDay(
                        data.getTrafficDate(),
                        data.getMetricValue(),
                        ForecastSource.ACTUAL,
                        data.isHasEvent(),
                        new ArrayList<>() // eventIds không cần cho actual data khi tính baseline
                );
                context.put(day);
            }
        }

        return contexts;
    }

    /**
     * Validate request trước khi xử lý.
     *
     * Lưu ý: dùng fromDate.plusMonths(3), KHÔNG dùng 90 ngày cố định.
     * 3 tháng có thể là 89, 90, 91 hoặc 92 ngày tùy thời điểm.
     */
    private void validateRequest(TrafficForecastRequest request) {
        if (request.getFromDate() == null || request.getToDate() == null) {
            throw new IllegalArgumentException("Từ ngày và đến ngày không được để trống");
        }

        if (request.getRegionIds() == null || request.getRegionIds().isEmpty()) {
            throw new IllegalArgumentException("regionIds không được để trống");
        }

        if (request.getChannelIds() == null || request.getChannelIds().isEmpty()) {
            throw new IllegalArgumentException("channelIds không được để trống");
        }

        if (request.getToDate().isBefore(request.getFromDate())) {
            throw new IllegalArgumentException("Ngày bắt đầu không được lớn hơn ngày kết thúc");
        }

        if (request.getToDate().isAfter(request.getFromDate().plusMonths(3))) {
            throw new IllegalArgumentException("Chỉ hỗ trợ tối đa 3 tháng");
        }
    }
}
