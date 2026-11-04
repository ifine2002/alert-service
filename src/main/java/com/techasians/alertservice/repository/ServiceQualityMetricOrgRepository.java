package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.ServiceQualityMetricOrg;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceQualityMetricOrgRepository extends JpaRepository<ServiceQualityMetricOrg, Integer> {

    /**
     * [Query đơn lẻ] Lấy tất cả cặp (metric, org) đang active theo 1 metric_id.
     * Dùng field metricId trực tiếp (không JOIN entity vì đã đổi sang Integer).
     */
    @Query("SELECT mo FROM ServiceQualityMetricOrg mo WHERE mo.metricId = :metricId AND mo.status = 1")
    List<ServiceQualityMetricOrg> findActiveByMetricId(@Param("metricId") Integer metricId);

    /**
     * [BATCH] Lấy TẤT CẢ cặp (metric, org) đang active của NHIỀU metricId cùng lúc.
     * Ví dụ: 5 alert_configs → 5 metricId → 1 query thay vì 5 query.
     */
    @Query("SELECT mo FROM ServiceQualityMetricOrg mo WHERE mo.metricId IN :metricIds AND mo.status = 1")
    List<ServiceQualityMetricOrg> findAllActiveByMetricIds(@Param("metricIds") List<Integer> metricIds);
}
