package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.TrafficHistoryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TrafficHistoryEventRepository extends JpaRepository<TrafficHistoryEvent, Long> {

    @org.springframework.data.jpa.repository.Query(value =
            "SELECT DISTINCT th.traffic_date " +
            "FROM traffic_history_events thev " +
            "JOIN traffic_histories th ON th.id = thev.traffic_history_id " +
            "WHERE thev.traffic_event_id = :eventId " +
            "AND th.region_channel_id = :regionChannelId " +
            "AND th.traffic_date < :beforeDate " +
            "AND th.traffic_date >= :earliestDate " +
            "ORDER BY th.traffic_date DESC " +
            "LIMIT :limit",
            nativeQuery = true)
    java.util.List<java.sql.Date> findPreviousActualOccurrenceDates(
            @org.springframework.data.repository.query.Param("eventId") Integer eventId,
            @org.springframework.data.repository.query.Param("regionChannelId") Long regionChannelId,
            @org.springframework.data.repository.query.Param("beforeDate") java.time.LocalDate beforeDate,
            @org.springframework.data.repository.query.Param("earliestDate") java.time.LocalDate earliestDate,
            @org.springframework.data.repository.query.Param("limit") int limit
    );
}
