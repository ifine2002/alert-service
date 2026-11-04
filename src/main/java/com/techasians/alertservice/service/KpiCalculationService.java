package com.techasians.alertservice.service;

import com.techasians.alertservice.dto.AlertViolationDto;
import com.techasians.alertservice.entity.*;
import com.techasians.alertservice.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service phát hiện vi phạm KPI — tối ưu 6 query cố định, không N+1.
 *
 * ServiceQualityMetricOrg không còn dùng @ManyToOne (chỉ lưu orgId, metricId kiểu Integer).
 * Vì vậy tên org được load riêng qua Query 6 (batch) và tra cứu qua Map.
 *
 * Được chia thành các phương thức nhỏ:
 *   1. detectViolations()  — điều phối luồng chính
 *   2. loadBatchData()     — toàn bộ I/O với DB (6 query)
 *   3. buildViolations()   — xử lý thuần Java trên dữ liệu đã load
 *   4. checkOneMetricOrg() — kiểm tra vi phạm 1 cặp (metric, org)
 *   5. isViolated()        — kiểm tra 1 alert_level có bị vi phạm không
 */
@Service
public class KpiCalculationService {

    private static final Logger log = LoggerFactory.getLogger(KpiCalculationService.class);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final AlertConfigRepository alertConfigRepository;
    private final AlertLevelRepository alertLevelRepository;
    private final ServiceQualityMetricOrgRepository metricOrgRepository;
    private final ServiceQualityOrgRepository orgRepository;
    private final ServiceQualityResultRepository resultRepository;
    private final ServiceQualityCategoryRepository categoryRepository;

    public KpiCalculationService(
            AlertConfigRepository alertConfigRepository,
            AlertLevelRepository alertLevelRepository,
            ServiceQualityMetricOrgRepository metricOrgRepository,
            ServiceQualityOrgRepository orgRepository,
            ServiceQualityResultRepository resultRepository,
            ServiceQualityCategoryRepository categoryRepository) {
        this.alertConfigRepository = alertConfigRepository;
        this.alertLevelRepository = alertLevelRepository;
        this.metricOrgRepository = metricOrgRepository;
        this.orgRepository = orgRepository;
        this.resultRepository = resultRepository;
        this.categoryRepository = categoryRepository;
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Điểm vào duy nhất: phát hiện toàn bộ vi phạm KPI trong khung giờ cho trước.
     *
     * @param shiftName tên ca ("Ca 1", "Ca 2", "Ca 3", "Thủ công")
     * @param fromTime  bắt đầu khung giờ lấy dữ liệu actual KPI
     * @param toTime    kết thúc khung giờ lấy dữ liệu actual KPI
     * @return danh sách vi phạm, mỗi vi phạm chứa đủ thông tin để build email/SMS
     */
    public List<AlertViolationDto> detectViolations(
            String shiftName,
            LocalDateTime fromTime,
            LocalDateTime toTime) {

        BatchData data = loadBatchData(shiftName, fromTime, toTime);
        if (data == null) {
            return new ArrayList<>();
        }

        List<AlertViolationDto> violations = buildViolations(shiftName, fromTime, toTime, data);

        log.info("[{}] Kết quả: phát hiện {} vi phạm.", shiftName, violations.size());
        return violations;
    }

    // =========================================================================
    // PRIVATE — Container dữ liệu đã load
    // =========================================================================

    /**
     * Container lưu toàn bộ dữ liệu đã load từ DB.
     * Truyền qua lại giữa các bước bằng 1 object duy nhất.
     */
    private static class BatchData {
        final LocalDateTime now;
        final int currentMonth;
        final int currentYear;
        final List<AlertConfig> activeConfigs;
        // Map: metricId → List<MetricOrg>
        final Map<Integer, List<ServiceQualityMetricOrg>> metricOrgsByMetricId;
        // Map: configId → List<AlertLevel>
        final Map<Integer, List<AlertLevel>> levelsByConfigId;
        // Map: metricOrgId → bản ghi actual mới nhất
        final Map<Integer, ServiceQualityResult> latestActualByMetricOrgId;
        // Map: metricOrgId → bản ghi target mới nhất
        final Map<Integer, ServiceQualityResult> latestTargetByMetricOrgId;
        // Map: orgId → tên org (thay thế cho @ManyToOne đã bị xóa)
        final Map<Long, String> orgNameByOrgId;
        // Map: categoryId → ServiceQualityCategory
        final Map<Integer, ServiceQualityCategory> categoryById;

        BatchData(LocalDateTime now,
                  List<AlertConfig> activeConfigs,
                  Map<Integer, List<ServiceQualityMetricOrg>> metricOrgsByMetricId,
                  Map<Integer, List<AlertLevel>> levelsByConfigId,
                  Map<Integer, ServiceQualityResult> latestActualByMetricOrgId,
                  Map<Integer, ServiceQualityResult> latestTargetByMetricOrgId,
                  Map<Long, String> orgNameByOrgId,
                  Map<Integer, ServiceQualityCategory> categoryById) {
            this.now                         = now;
            this.currentMonth                = now.getMonthValue();
            this.currentYear                 = now.getYear();
            this.activeConfigs               = activeConfigs;
            this.metricOrgsByMetricId        = metricOrgsByMetricId;
            this.levelsByConfigId            = levelsByConfigId;
            this.latestActualByMetricOrgId   = latestActualByMetricOrgId;
            this.latestTargetByMetricOrgId   = latestTargetByMetricOrgId;
            this.orgNameByOrgId              = orgNameByOrgId;
            this.categoryById                = categoryById;
        }
    }

    // =========================================================================
    // PRIVATE — BƯỚC 1: Load dữ liệu từ DB (6 query batch)
    // =========================================================================

    /**
     * Thực hiện toàn bộ I/O với DB — 6 query batch, không N+1.
     *
     *   Query 1: Alert configs active
     *   Query 2: Metric orgs active (batch theo metricIds)
     *   Query 3: Tên org (batch theo orgIds, do MetricOrg chỉ còn Integer orgId)
     *   Query 4: Alert levels (batch theo configIds)
     *   Query 5: Actual KPI (batch theo metricOrgIds + khung giờ ca)
     *   Query 6: Target KPI (batch theo metricOrgIds + tháng/năm hiện tại)
     *
     * @return BatchData nếu đủ dữ liệu để xử lý, null nếu cần dừng sớm
     */
    private BatchData loadBatchData(String shiftName, LocalDateTime fromTime, LocalDateTime toTime) {

        // Query 1: alert_configs active
        List<AlertConfig> activeConfigs = alertConfigRepository.findAllActiveConfigs();

        if (activeConfigs.isEmpty()) {
            log.info("[{}] Không có cấu hình cảnh báo nào đang active.", shiftName);
            return null;
        }

        log.info("[{}] Load {} cấu hình cảnh báo active...", shiftName, activeConfigs.size());

        List<Integer> configIds = activeConfigs.stream()
                .map(AlertConfig::getId)
                .collect(Collectors.toList());

        List<Integer> metricIds = activeConfigs.stream()
                .map(c -> c.getServiceQualityMetric().getId())
                .distinct()
                .collect(Collectors.toList());

        // Query 2: metric_orgs active (dùng field metricId trực tiếp, không JOIN entity)
        List<ServiceQualityMetricOrg> allMetricOrgs =
                metricOrgRepository.findAllActiveByMetricIds(metricIds);

        if (allMetricOrgs.isEmpty()) {
            log.warn("[{}] Không có metric_org nào active.", shiftName);
            return null;
        }

        // Build Map: metricId → List<MetricOrg>
        // Dùng mo.getMetricId() thay vì mo.getServiceQualityMetric().getId()
        Map<Integer, List<ServiceQualityMetricOrg>> metricOrgsByMetricId = new HashMap<>();
        for (ServiceQualityMetricOrg mo : allMetricOrgs) {
            metricOrgsByMetricId
                    .computeIfAbsent(mo.getMetricId(), k -> new ArrayList<>())
                    .add(mo);
        }

        List<Integer> allMetricOrgIds = allMetricOrgs.stream()
                .map(ServiceQualityMetricOrg::getId)
                .collect(Collectors.toList());

        // Query 3: tên org theo batch (thay thế cho @ManyToOne đã bị xóa khỏi MetricOrg)
        // Lấy danh sách orgId duy nhất từ tất cả metric_orgs
        List<Long> orgIds = allMetricOrgs.stream()
                .map(mo -> mo.getOrgId().longValue())
                .distinct()
                .collect(Collectors.toList());

        List<ServiceQualityOrg> orgs = orgRepository.findAllByIdIn(orgIds);

        // Build Map: orgId → orgName để tra nhanh khi build violation
        Map<Long, String> orgNameByOrgId = new HashMap<>();
        for (ServiceQualityOrg org : orgs) {
            orgNameByOrgId.put(org.getId(), org.getName());
        }

        // Query 4: alert_levels của tất cả config
        List<AlertLevel> allLevels = alertLevelRepository.findByAlertConfigIdIn(configIds);

        Map<Integer, List<AlertLevel>> levelsByConfigId = new HashMap<>();
        for (AlertLevel level : allLevels) {
            Integer configId = level.getAlertConfig().getId();
            levelsByConfigId.computeIfAbsent(configId, k -> new ArrayList<>()).add(level);
        }

        // Query 5: actual KPI (type=1) trong khung giờ ca
        List<ServiceQualityResult> allActuals =
                resultRepository.findAllActualBatch(allMetricOrgIds, fromTime, toTime);

        // putIfAbsent: giữ bản ghi đầu tiên = mới nhất (query đã ORDER BY createdAt DESC)
        Map<Integer, ServiceQualityResult> latestActualByMetricOrgId = new HashMap<>();
        for (ServiceQualityResult result : allActuals) {
            latestActualByMetricOrgId.putIfAbsent(result.getMetricOrgId(), result);
        }

        // Query 6: target KPI (type=0) trong tháng hiện tại
        LocalDateTime now = LocalDateTime.now();
        List<ServiceQualityResult> allTargets = resultRepository.findAllTargetBatch(
                allMetricOrgIds, now.getMonthValue(), now.getYear());

        Map<Integer, ServiceQualityResult> latestTargetByMetricOrgId = new HashMap<>();
        for (ServiceQualityResult result : allTargets) {
            latestTargetByMetricOrgId.putIfAbsent(result.getMetricOrgId(), result);
        }

        // Query 7: Load categories cho metric (tránh N+1 khi build violation)
        List<Integer> categoryIds = activeConfigs.stream()
                .flatMap(c -> java.util.stream.Stream.of(
                        c.getServiceQualityMetric() != null ? c.getServiceQualityMetric().getCategoryId() : null,
                        c.getServiceQualityCategory() != null ? c.getServiceQualityCategory().getId() : null
                ))
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        List<ServiceQualityCategory> categories = categoryRepository.findAllById(categoryIds);
        Map<Integer, ServiceQualityCategory> categoryById = categories.stream()
                .collect(Collectors.toMap(ServiceQualityCategory::getId, c -> c));

        log.info("[{}] Load xong: {} metricOrgs, {} orgs, {} levels, {} actuals, {} targets, {} categories",
                shiftName,
                allMetricOrgs.size(), orgs.size(), allLevels.size(),
                latestActualByMetricOrgId.size(), latestTargetByMetricOrgId.size(), categories.size());

        return new BatchData(now, activeConfigs, metricOrgsByMetricId,
                levelsByConfigId, latestActualByMetricOrgId, latestTargetByMetricOrgId,
                orgNameByOrgId, categoryById);
    }

    // =========================================================================
    // PRIVATE — BƯỚC 2: Xử lý thuần Java
    // =========================================================================

    /**
     * Duyệt từng config → từng metricOrg → kiểm tra vi phạm.
     * Toàn bộ dữ liệu đọc từ Map, không thêm bất kỳ query DB nào.
     */
    private List<AlertViolationDto> buildViolations(
            String shiftName,
            LocalDateTime fromTime,
            LocalDateTime toTime,
            BatchData data) {

        List<AlertViolationDto> violations = new ArrayList<>();

        for (AlertConfig config : data.activeConfigs) {
            Integer metricId = config.getServiceQualityMetric().getId();

            List<ServiceQualityMetricOrg> metricOrgs =
                    data.metricOrgsByMetricId.getOrDefault(metricId, new ArrayList<>());

            if (metricOrgs.isEmpty()) {
                log.warn("[{}] Config ID={} (metricId={}) không có metric_org nào active",
                        shiftName, config.getId(), metricId);
                continue;
            }

            List<AlertLevel> levels =
                    data.levelsByConfigId.getOrDefault(config.getId(), new ArrayList<>());

            if (levels.isEmpty()) {
                log.warn("[{}] Config ID={} không có alert_level nào", shiftName, config.getId());
                continue;
            }

            for (ServiceQualityMetricOrg metricOrg : metricOrgs) {
                AlertViolationDto violation = checkOneMetricOrg(
                        shiftName, fromTime, toTime, config, metricOrg, levels, data);

                if (violation != null) {
                    violations.add(violation);
                }
            }
        }

        return violations;
    }

    /**
     * Kiểm tra vi phạm cho 1 cặp (metric, org).
     *
     * orgName được lấy từ data.orgNameByOrgId thay vì gọi mo.getServiceQualityOrg().getName()
     * (vì MetricOrg không còn @ManyToOne nữa).
     *
     * @return AlertViolationDto nếu vi phạm, null nếu không vi phạm hoặc thiếu dữ liệu
     */
    private AlertViolationDto checkOneMetricOrg(
            String shiftName,
            LocalDateTime fromTime,
            LocalDateTime toTime,
            AlertConfig config,
            ServiceQualityMetricOrg metricOrg,
            List<AlertLevel> levels,
            BatchData data) {

        Integer metricOrgId = metricOrg.getId();

        // Lấy tên org từ Map (không gọi DB, không join entity)
        String orgName = data.orgNameByOrgId.getOrDefault(
                metricOrg.getOrgId().longValue(), "Org#" + metricOrg.getOrgId());

        ServiceQualityResult actualResult = data.latestActualByMetricOrgId.get(metricOrgId);
        ServiceQualityResult targetResult = data.latestTargetByMetricOrgId.get(metricOrgId);

        if (actualResult == null) {
            log.warn("[{}] Không có actual KPI (type=1) cho metricOrgId={} org='{}' trong [{} ~ {}]",
                    shiftName, metricOrgId, orgName, fromTime, toTime);
            return null;
        }

        if (targetResult == null) {
            log.warn("[{}] Không có target KPI (type=0) cho metricOrgId={} org='{}' tháng {}/{}",
                    shiftName, metricOrgId, orgName, data.currentMonth, data.currentYear);
            return null;
        }

        BigDecimal actual = actualResult.getTotal();
        BigDecimal target = targetResult.getTotal();

        // Tìm mức vi phạm CAO NHẤT (level_number lớn nhất bị vi phạm)
        Integer highestViolatedLevel   = null;
        Integer highestViolatedLevelId = null;

        for (AlertLevel level : levels) {
            if (level.getStatus() != 1) {
                continue;
            }
            if (isViolated(actual, target, level)) {
                if (highestViolatedLevel == null || level.getLevelNumber() > highestViolatedLevel) {
                    highestViolatedLevel   = level.getLevelNumber();
                    highestViolatedLevelId = level.getId();
                }
            }
        }

        if (highestViolatedLevel == null) {
            return null;
        }

        BigDecimal deviation = target.subtract(actual).abs().setScale(4, RoundingMode.HALF_UP);

        Integer metricId = config.getServiceQualityMetric().getId();
        Integer categoryId = config.getServiceQualityMetric().getCategoryId();
        ServiceQualityCategory category = data.categoryById.get(categoryId);
        String categoryName = category != null ? category.getName() : "Unknown";
        Integer parentCategoryId = category != null ? category.getParentId() : null;

        return new AlertViolationDto()
                .setMetricId(metricId)
                .setCategoryId(categoryId)
                .setCategoryName(categoryName)
                .setParentCategoryId(parentCategoryId)
                .setMetricOrgId(metricOrgId)
                .setMetricName(config.getServiceQualityMetric().getName())
                .setOrgId(metricOrg.getOrgId())
                .setOrgName(orgName)
                .setActualValue(actual)
                .setTargetKpiValue(target)
                .setDeviationValue(deviation)
                .setViolatedLevelNumber(highestViolatedLevel)
                .setViolatedLevelId(highestViolatedLevelId)
                .setShiftName(shiftName)
                .setCheckTime(data.now);
    }

    // =========================================================================
    // PRIVATE — Tính toán ngưỡng
    // =========================================================================

    /**
     * Kiểm tra một alert_level có bị vi phạm không.
     *
     * comparisonValue = target × (1 - threshold_value / 100)
     *
     * Ví dụ: target=90, threshold=5 → comparisonValue = 85.5
     *   condition=1 (nhỏ hơn) → vi phạm nếu actual < 85.5
     *
     * condition=5 (trong khoảng):
     *   lowerBound = target × (1 - threshold_value_to / 100)
     *   → vi phạm nếu lowerBound < actual < comparisonValue
     */
    private boolean isViolated(BigDecimal actual, BigDecimal target, AlertLevel level) {
        int operator = level.getConditionOperator();

        BigDecimal comparisonValue = target.multiply(
                BigDecimal.ONE.subtract(
                        level.getThresholdValue().divide(HUNDRED, 10, RoundingMode.HALF_UP)
                )
        );

        switch (operator) {
            case 1: return actual.compareTo(comparisonValue) < 0;
            case 2: return actual.compareTo(comparisonValue) > 0;
            case 3: return actual.compareTo(comparisonValue) >= 0;
            case 4: return actual.compareTo(comparisonValue) <= 0;
            case 5:
                BigDecimal lowerBound = target.multiply(
                        BigDecimal.ONE.subtract(
                                level.getThresholdValueTo().divide(HUNDRED, 10, RoundingMode.HALF_UP)
                        )
                );
                return actual.compareTo(lowerBound) > 0
                        && actual.compareTo(comparisonValue) < 0;
            default:
                log.warn("condition_operator không hợp lệ: {} (alertLevelId={})",
                        operator, level.getId());
                return false;
        }
    }
}
