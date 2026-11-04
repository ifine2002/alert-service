package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.ServiceQualityResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository truy vấn kết quả KPI từ bảng service_quality_results.
 *
 * ServiceQualityResult không còn @ManyToOne tới ServiceQualityMetricOrg,
 * chỉ lưu Integer metricOrgId, nên dùng field trực tiếp trong JPQL.
 */
@Repository
public interface ServiceQualityResultRepository extends JpaRepository<ServiceQualityResult, Long> {

    /**
     * [Query đơn lẻ] KPI thực tế (type=1) mới nhất của 1 metricOrg trong khung giờ ca.
     */
    @Query("SELECT r FROM ServiceQualityResult r " +
           "WHERE r.metricOrgId = :metricOrgId " +
           "  AND r.type = 1 " +
           "  AND r.createdAt BETWEEN :fromTime AND :toTime " +
           "ORDER BY r.createdAt DESC")
    Optional<ServiceQualityResult> findLatestActual(
            @Param("metricOrgId") Integer metricOrgId,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime);

    /**
     * [Query đơn lẻ] Target KPI (type=0) mới nhất của 1 metricOrg trong tháng/năm.
     */
    @Query("SELECT r FROM ServiceQualityResult r " +
           "WHERE r.metricOrgId = :metricOrgId " +
           "  AND r.type = 0 " +
           "  AND r.reportMonth = :month " +
           "  AND r.reportYear = :year " +
           "ORDER BY r.createdAt DESC")
    Optional<ServiceQualityResult> findLatestTarget(
            @Param("metricOrgId") Integer metricOrgId,
            @Param("month") Integer month,
            @Param("year") Integer year);

    // =========================================================================
    // BATCH QUERIES — load toàn bộ 1 lần, dùng trong KpiCalculationService
    // =========================================================================

    /**
     * [BATCH] KPI thực tế (type=1) của NHIỀU metricOrgId trong khung giờ ca.
     * KpiCalculationService group theo metricOrgId, lấy bản ghi mới nhất (ORDER DESC).
     */
    @Query("SELECT r FROM ServiceQualityResult r " +
           "WHERE r.metricOrgId IN :metricOrgIds " +
           "  AND r.type = 1 " +
           "  AND r.createdAt BETWEEN :fromTime AND :toTime " +
           "ORDER BY r.metricOrgId ASC, r.createdAt DESC")
    List<ServiceQualityResult> findAllActualBatch(
            @Param("metricOrgIds") List<Integer> metricOrgIds,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime);

    /**
     * [BATCH] Target KPI (type=0) của NHIỀU metricOrgId trong tháng/năm.
     */
    @Query("SELECT r FROM ServiceQualityResult r " +
           "WHERE r.metricOrgId IN :metricOrgIds " +
           "  AND r.type = 0 " +
           "  AND r.reportMonth = :month " +
           "  AND r.reportYear = :year " +
           "ORDER BY r.metricOrgId ASC, r.createdAt DESC")
    List<ServiceQualityResult> findAllTargetBatch(
            @Param("metricOrgIds") List<Integer> metricOrgIds,
            @Param("month") Integer month,
            @Param("year") Integer year);
}
