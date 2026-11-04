package com.techasians.alertservice.service.forecast;

import com.techasians.alertservice.dto.TrafficForecastRequest;
import com.techasians.alertservice.dto.forecast.EventChannelKey;
import com.techasians.alertservice.dto.forecast.EventConfig;
import com.techasians.alertservice.dto.forecast.EventImpactProjection;
import com.techasians.alertservice.dto.forecast.EventScheduleConfig;
import com.techasians.alertservice.dto.forecast.ForecastInputData;
import com.techasians.alertservice.dto.forecast.RegionChannelConfig;
import com.techasians.alertservice.dto.forecast.RegionChannelConfigProjection;
import com.techasians.alertservice.dto.forecast.TrafficActualData;
import com.techasians.alertservice.entity.TrafficEvent;
import com.techasians.alertservice.entity.TrafficEventSchedule;
import com.techasians.alertservice.repository.TrafficEventChannelRepository;
import com.techasians.alertservice.repository.TrafficEventRepository;
import com.techasians.alertservice.repository.TrafficEventScheduleRepository;
import com.techasians.alertservice.repository.TrafficHistoryRepository;
import com.techasians.alertservice.repository.TrafficChannelKpiRepository;
import com.techasians.alertservice.repository.TrafficRegionChannelRepository;
import com.techasians.alertservice.entity.TrafficChannelKpi;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Load toàn bộ dữ liệu cần thiết từ DB trước khi bắt đầu vòng forecast.
 *
 * Chỉ thực hiện 5 query, toàn bộ vòng lặp tính toán sẽ dùng data trong memory.
 * KHÔNG query DB trong vòng lặp forecast từng ngày.
 *
 * Thứ tự 5 queries:
 * 1. Region-channel config + weekendAffected (filter theo regionIds,
 * channelIds)
 * 2. Actual traffic "Cuộc gọi" 36 tháng gần nhất
 * 3. Tất cả event đang active
 * 4. Schedules của các event (batch, tránh N+1)
 * 5. Impact rates (event, channel) → đã chia 100 khi đưa vào map
 */
@Component
public class ForecastDataLoader {

    private static final Logger log = LoggerFactory.getLogger(ForecastDataLoader.class);

    private static final int INTERNAL_SCALE = 12;
    private static final RoundingMode INTERNAL_ROUNDING = RoundingMode.HALF_UP;

    // Scale DB lưu impact_rate dạng 0-100
    private static final BigDecimal IMPACT_RATE_DIVISOR = BigDecimal.valueOf(100);

    private final TrafficRegionChannelRepository regionChannelRepo;
    private final TrafficHistoryRepository historyRepo;
    private final TrafficEventRepository eventRepo;
    private final TrafficEventScheduleRepository scheduleRepo;
    private final TrafficEventChannelRepository eventChannelRepo;
    private final TrafficChannelKpiRepository kpiRepo;

    public ForecastDataLoader(TrafficRegionChannelRepository regionChannelRepo,
            TrafficHistoryRepository historyRepo,
            TrafficEventRepository eventRepo,
            TrafficEventScheduleRepository scheduleRepo,
            TrafficEventChannelRepository eventChannelRepo,
            TrafficChannelKpiRepository kpiRepo) {
        this.regionChannelRepo = regionChannelRepo;
        this.historyRepo = historyRepo;
        this.eventRepo = eventRepo;
        this.scheduleRepo = scheduleRepo;
        this.eventChannelRepo = eventChannelRepo;
        this.kpiRepo = kpiRepo;
    }

    /**
     * Load toàn bộ dữ liệu cần thiết cho forecast.
     *
     * @param request request gốc từ user (chứa regionIds, channelIds)
     * @return ForecastInputData chứa toàn bộ data, sẵn sàng cho vòng tính
     */
    public ForecastInputData load(TrafficForecastRequest request) {
        long q0 = System.nanoTime();

        // ===== Query 1: Region-channel config =====
        List<RegionChannelConfigProjection> rcProjections = regionChannelRepo.findActiveForForecast(
                request.getRegionIds(),
                request.getChannelIds());
        List<RegionChannelConfig> regionChannels = toRegionChannelConfigs(rcProjections);
        log.warn("[PERF-DB] Q1 findActiveForForecast: {}ms ({} rows)",
                (System.nanoTime() - q0) / 1_000_000, regionChannels.size());

        if (regionChannels.isEmpty()) {
            return new ForecastInputData()
                    .setRegionChannels(regionChannels)
                    .setHistoricalTraffic(new ArrayList<>())
                    .setEvents(new ArrayList<>())
                    .setImpactRates(new HashMap<>());
        }

        List<Long> regionChannelIds = regionChannels.stream()
                .map(RegionChannelConfig::getRegionChannelId)
                .collect(Collectors.toList());

        log.warn("[PERF-Q2] regionChannelIds size: {}", regionChannelIds.size());
        long q2RepositoryStart = System.nanoTime();
        List<Object[]> cutoffRaw = historyRepo.findActualCutoffByRegionChannelIds(regionChannelIds);
        long q2RepositoryMs = (System.nanoTime() - q2RepositoryStart) / 1_000_000;
        long q2MappingStart = System.nanoTime();
        Map<Long, LocalDate> cutoffByRegionChannelId = new HashMap<>();
        for (Object[] row : cutoffRaw) {
            Long rcId = ((Number) row[0]).longValue();
            LocalDate date = ((java.sql.Date) row[1]).toLocalDate();
            cutoffByRegionChannelId.put(rcId, date);
        }
        long q2MappingMs = (System.nanoTime() - q2MappingStart) / 1_000_000;
        log.warn("[PERF-Q2] thread={}, repository={}ms, mapping={}ms, rawRows={}, mapRows={}",
                Thread.currentThread().getName(),
                q2RepositoryMs,
                q2MappingMs,
                cutoffRaw.size(),
                cutoffByRegionChannelId.size());

        applyDataQuality(regionChannels, cutoffByRegionChannelId, request.getFromDate());

        long q3 = System.nanoTime();
        LocalDate earliestActualCutoff = findEarliestActualCutoff(regionChannels);
        List<TrafficActualData> historicalTraffic = new ArrayList<>();
        if (earliestActualCutoff != null) {
            LocalDate historyFrom = earliestActualCutoff.minusMonths(12);
            LocalDate historyTo = findLatestActualCutoff(regionChannels);
            log.warn("[PERF-DB] Q3 findActualCallTraffic range: {} -> {} ({} rcIds)",
                    historyFrom, historyTo, regionChannelIds.size());

            // Đo riêng: thời gian DB thực thi query
            long q3db = System.nanoTime();
            List<Object[]> trafficProjections = historyRepo.findActualCallTraffic(
                    historyFrom, historyTo, regionChannelIds);
            log.warn("[PERF-DB] Q3 DB execution (ra kết quả từ DB): {}ms ({} rows raw)",
                    (System.nanoTime() - q3db) / 1_000_000, trafficProjections.size());

            // Đo riêng: thời gian Java map rows -> object
            long q3map = System.nanoTime();
            historicalTraffic = toTrafficActualData(trafficProjections);
            log.warn("[PERF-DB] Q3 Java mapping ({} rows -> TrafficActualData): {}ms",
                    historicalTraffic.size(), (System.nanoTime() - q3map) / 1_000_000);

            log.warn("[PERF-DB] Q3 TỔNG: {}ms", (System.nanoTime() - q3) / 1_000_000);
        }

        // ===== Query 3: Tất cả event đang active =====
        // Dùng method có sẵn: status=1, chưa bị xóa mềm
        long q4 = System.nanoTime();
        List<TrafficEvent> events = eventRepo.findByStatusAndDeletedAtIsNull(1);
        List<Integer> eventIds = events.stream()
                .map(TrafficEvent::getId)
                .collect(Collectors.toList());
        log.warn("[PERF-DB] Q4 findEvents: {}ms ({} events)",
                (System.nanoTime() - q4) / 1_000_000, events.size());

        // ===== Query 4: Schedules batch (tránh N+1) =====
        long q5 = System.nanoTime();
        List<TrafficEventSchedule> schedules = new ArrayList<>();
        if (!eventIds.isEmpty()) {
            schedules = scheduleRepo.findByEventIdIn(eventIds);
        }
        log.warn("[PERF-DB] Q5 findSchedules: {}ms ({} rows)",
                (System.nanoTime() - q5) / 1_000_000, schedules.size());

        // Group schedules theo eventId để assemble EventConfig
        Map<Integer, List<TrafficEventSchedule>> schedulesByEventId = schedules.stream()
                .collect(Collectors.groupingBy(s -> s.getEvent().getId()));

        // ===== Query 5: Impact rates + event-channel mapping =====
        long q6 = System.nanoTime();
        List<EventImpactProjection> impactProjections = eventChannelRepo.findAllImpactRates();
        Map<EventChannelKey, BigDecimal> impactRates = toImpactRateMap(impactProjections);
        log.warn("[PERF-DB] Q6 findAllImpactRates: {}ms ({} rows)",
                (System.nanoTime() - q6) / 1_000_000, impactProjections.size());

        Map<Integer, List<Integer>> channelIdsByEventId = buildChannelIdsByEventId(impactProjections);
        List<EventConfig> eventConfigs = toEventConfigs(events, schedulesByEventId, channelIdsByEventId);

        // ===== Query 6: KPI theo channelId - dùng tính quân số dự kiến =====
        // channelIds lấy từ request, lọc bỏ null, load 1 lần dùng cho toàn bộ kênh
        long q7 = System.nanoTime();
        List<TrafficChannelKpi> kpiList = kpiRepo.findByChannelIdInAndDeletedAtIsNull(request.getChannelIds());
        Map<Integer, TrafficChannelKpi> kpiByChannelId = new HashMap<>();
        for (TrafficChannelKpi kpi : kpiList) {
            kpiByChannelId.put(kpi.getChannelId(), kpi);
        }
        log.warn("[PERF-DB] Q7 findKpi: {}ms ({} rows)",
                (System.nanoTime() - q7) / 1_000_000, kpiList.size());

        log.warn("[PERF-DB] TỔNG load(): {}ms", (System.nanoTime() - q0) / 1_000_000);

        // ===== Assemble =====
        return new ForecastInputData()
                .setActualCutoff(findLatestActualCutoff(regionChannels))
                .setRegionChannels(regionChannels)
                .setHistoricalTraffic(historicalTraffic)
                .setEvents(eventConfigs)
                .setImpactRates(impactRates)
                .setKpiByChannelId(kpiByChannelId);
    }

    // ======== Convert methods ========

    private List<RegionChannelConfig> toRegionChannelConfigs(List<RegionChannelConfigProjection> projections) {
        List<RegionChannelConfig> result = new ArrayList<>();
        for (RegionChannelConfigProjection p : projections) {
            result.add(new RegionChannelConfig()
                    .setRegionChannelId(p.getRegionChannelId())
                    .setChannelId(p.getChannelId())
                    .setRegionId(p.getRegionId())
                    .setRegionName(p.getRegionName())
                    .setChannelName(p.getChannelName())
                    .setWeekendAffected(Integer.valueOf(1).equals(p.getWeekendAffected()))
                    .setMondayAffected(Integer.valueOf(1).equals(p.getMondayAffected())));
        }
        return result;
    }

    private List<TrafficActualData> toTrafficActualData(List<Object[]> rawList) {
        List<TrafficActualData> result = new ArrayList<>(rawList.size());
        for (Object[] row : rawList) {
            Long rcId = ((Number) row[0]).longValue();
            LocalDate date = ((java.sql.Date) row[1]).toLocalDate();
            BigDecimal value = (BigDecimal) row[2];
            Number hasEventNum = (Number) row[3];
            boolean hasEvent = hasEventNum != null && hasEventNum.intValue() == 1;

            result.add(new TrafficActualData(rcId, date, value, hasEvent));
        }
        return result;
    }

    private List<EventConfig> toEventConfigs(List<TrafficEvent> events,
            Map<Integer, List<TrafficEventSchedule>> schedulesByEventId,
            Map<Integer, List<Integer>> channelIdsByEventId) {
        List<EventConfig> result = new ArrayList<>();
        for (TrafficEvent event : events) {
            List<TrafficEventSchedule> eventSchedules = schedulesByEventId.getOrDefault(event.getId(),
                    new ArrayList<>());
            List<EventScheduleConfig> scheduleConfigs = toScheduleConfigs(eventSchedules);

            result.add(new EventConfig()
                    .setId(event.getId())
                    .setName(event.getName())
                    .setEventType(event.getEventType())
                    .setRepeatType(event.getRepeatType())
                    .setPatternType(event.getPatternType())
                    .setSchedules(scheduleConfigs)
                    .setChannelIds(channelIdsByEventId.getOrDefault(event.getId(), new ArrayList<>())));
        }
        return result;
    }

    private List<EventScheduleConfig> toScheduleConfigs(List<TrafficEventSchedule> schedules) {
        List<EventScheduleConfig> result = new ArrayList<>();
        for (TrafficEventSchedule s : schedules) {
            result.add(new EventScheduleConfig()
                    .setExactDate(s.getExactDate())
                    .setDayOfMonth(s.getDayOfMonth())
                    .setMonthOfYear(s.getMonthOfYear()));
        }
        return result;
    }

    private Map<EventChannelKey, BigDecimal> toImpactRateMap(List<EventImpactProjection> projections) {
        Map<EventChannelKey, BigDecimal> result = new HashMap<>();
        for (EventImpactProjection p : projections) {
            if (p.getImpactRate() != null) {
                // Chia 100 ngay khi load: DB lưu 0-100, dùng trong công thức cần 0.0-1.0
                BigDecimal rate = p.getImpactRate().divide(IMPACT_RATE_DIVISOR, INTERNAL_SCALE, INTERNAL_ROUNDING);
                result.put(new EventChannelKey(p.getEventId(), p.getChannelId()), rate);
            }
        }
        return result;
    }

    private Map<Integer, List<Integer>> buildChannelIdsByEventId(List<EventImpactProjection> projections) {
        Map<Integer, List<Integer>> result = new HashMap<>();
        for (EventImpactProjection p : projections) {
            result.computeIfAbsent(p.getEventId(), key -> new ArrayList<>()).add(p.getChannelId());
        }
        return result;
    }

    private void applyDataQuality(List<RegionChannelConfig> regionChannels,
            Map<Long, LocalDate> cutoffByRegionChannelId,
            LocalDate fromDate) {
        for (RegionChannelConfig config : regionChannels) {
            LocalDate actualCutoff = cutoffByRegionChannelId.get(config.getRegionChannelId());
            config.setActualCutoff(actualCutoff);

            if (actualCutoff == null) {
                config.setCalculationStart(null)
                        .setGapDays(null)
                        .setDataQuality("SKIPPED")
                        .setDataQualityNote("Không có dữ liệu actual cho metric Cuộc gọi")
                        .setSkipped(true);
                continue;
            }

            int gapDays = Math.max(0, (int) java.time.temporal.ChronoUnit.DAYS.between(actualCutoff, fromDate) - 1);
            config.setGapDays(gapDays);

            if (gapDays > 90) {
                config.setCalculationStart(null)
                        .setDataQuality("TOO_OLD")
                        .setDataQualityNote("Dữ liệu actual gần nhất cách fromDate quá 90 ngày")
                        .setSkipped(true);
                continue;
            }

            config.setCalculationStart(actualCutoff.plusDays(1))
                    .setSkipped(false);

            if (gapDays <= 7) {
                config.setDataQuality("GOOD")
                        .setDataQualityNote("Dữ liệu actual đủ gần với khoảng dự trù");
            } else if (gapDays <= 30) {
                config.setDataQuality("WARNING")
                        .setDataQualityNote("Dữ liệu actual bị thiếu nhiều ngày trước fromDate");
            } else {
                config.setDataQuality("LOW_CONFIDENCE")
                        .setDataQualityNote("Dữ liệu actual bị thiếu rất nhiều ngày trước fromDate");
            }
        }
    }

    private LocalDate findEarliestActualCutoff(List<RegionChannelConfig> regionChannels) {
        LocalDate earliest = null;
        for (RegionChannelConfig config : regionChannels) {
            if (config.isSkipped() || config.getActualCutoff() == null) {
                continue;
            }

            if (earliest == null || config.getActualCutoff().isBefore(earliest)) {
                earliest = config.getActualCutoff();
            }
        }
        return earliest;
    }

    private LocalDate findLatestActualCutoff(List<RegionChannelConfig> regionChannels) {
        LocalDate latest = null;
        for (RegionChannelConfig config : regionChannels) {
            if (config.getActualCutoff() == null) {
                continue;
            }

            if (latest == null || config.getActualCutoff().isAfter(latest)) {
                latest = config.getActualCutoff();
            }
        }
        return latest;
    }
}
