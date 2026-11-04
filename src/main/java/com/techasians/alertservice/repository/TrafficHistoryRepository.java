package com.techasians.alertservice.repository;

import com.techasians.alertservice.dto.forecast.TrafficActualProjection;
import com.techasians.alertservice.entity.TrafficHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TrafficHistoryRepository extends JpaRepository<TrafficHistory, Long> {

    // flushAutomatically = true: buộc Hibernate flush pending operations trước khi thực thi DELETE
    @Modifying(flushAutomatically = true)
    @Query(value =
            "DELETE v FROM traffic_history_metric_values v " +
            "JOIN traffic_histories h ON h.id = v.traffic_history_id " +
            "WHERE h.region_channel_id IN :regionChannelIds " +
            "AND h.traffic_date BETWEEN :startDate AND :endDate",
            nativeQuery = true)
    void bulkDeleteMetricValues(
            @Param("regionChannelIds") List<Long> regionChannelIds,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Modifying(flushAutomatically = true)
    @Query(value =
            "DELETE e FROM traffic_history_events e " +
            "JOIN traffic_histories h ON h.id = e.traffic_history_id " +
            "WHERE h.region_channel_id IN :regionChannelIds " +
            "AND h.traffic_date BETWEEN :startDate AND :endDate",
            nativeQuery = true)
    void bulkDeleteEvents(
            @Param("regionChannelIds") List<Long> regionChannelIds,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Modifying(flushAutomatically = true)
    @Query(value =
            "DELETE FROM traffic_histories " +
            "WHERE region_channel_id IN :regionChannelIds " +
            "AND traffic_date BETWEEN :startDate AND :endDate",
            nativeQuery = true)
    void bulkDeleteHistories(
            @Param("regionChannelIds") List<Long> regionChannelIds,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Tìm ngày actual mới nhất trong toàn hệ thống.
     * Đây là actualCutoff - forecast sẽ bắt đầu từ ngày hôm sau.
     */
    @Query("SELECT MAX(th.trafficDate) FROM TrafficHistory th")
    LocalDate findActualCutoff();

    /**
     * Tìm ngày actual mới nhất theo từng region-channel cho metric "Cuộc gọi".
     *
     * Native query để DB dùng đúng UNIQUE KEY uk_traffic_history(region_channel_id, traffic_date).
     * JOIN từ traffic_histories ra ngoài: DB filter region_channel_id trước (có index),
     * sau đó mới JOIN vào metric tables để filter name='Cuộc gọi'.
     */
    @Query(value =
            "SELECT th.region_channel_id AS regionChannelId, " +
            "       MAX(th.traffic_date)  AS actualCutoff " +
            "FROM traffic_histories th " +
            "JOIN traffic_history_metric_values thmv ON thmv.traffic_history_id = th.id " +
            "JOIN traffic_channel_metrics tcm         ON tcm.id = thmv.channel_metric_id " +
            "JOIN traffic_metrics tm                  ON tm.id = tcm.metric_id " +
            "WHERE th.region_channel_id IN :regionChannelIds " +
            "  AND tm.name = 'Cu\u1ed9c g\u1ecdi' " +
            "  AND thmv.metric_value IS NOT NULL " +
            "GROUP BY th.region_channel_id",
            nativeQuery = true)
    List<Object[]> findActualCutoffByRegionChannelIds(
            @Param("regionChannelIds") List<Long> regionChannelIds
    );

    /**
     * Load actual traffic metric "Cuộc gọi" cho forecast.
     *
     * Native query để DB dùng đúng UNIQUE KEY uk_traffic_history(region_channel_id, traffic_date).
     * Filter region_channel_id và traffic_date trước (có index composite),
     * JOIN vào metric tables chỉ để xác nhận metric name.
     */
    @Query(value =
            "SELECT th.region_channel_id AS regionChannelId, " +
            "       th.traffic_date       AS trafficDate, " +
            "       thmv.metric_value     AS metricValue, " +
            "       th.has_event          AS hasEvent " +
            "FROM traffic_histories th " +
            "JOIN traffic_history_metric_values thmv ON thmv.traffic_history_id = th.id " +
            "JOIN traffic_channel_metrics tcm         ON tcm.id = thmv.channel_metric_id " +
            "JOIN traffic_metrics tm                  ON tm.id = tcm.metric_id " +
            "WHERE th.region_channel_id IN :regionChannelIds " +
            "  AND th.traffic_date BETWEEN :fromDate AND :toDate " +
            "  AND tm.name = 'Cu\u1ed9c g\u1ecdi' " +
            "ORDER BY th.traffic_date ASC",
            nativeQuery = true)
    List<Object[]> findActualCallTraffic(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("regionChannelIds") List<Long> regionChannelIds
    );
}

