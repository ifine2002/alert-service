package com.techasians.alertservice.repository;

import com.techasians.alertservice.dto.forecast.EventImpactProjection;
import com.techasians.alertservice.entity.TrafficEventChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrafficEventChannelRepository extends JpaRepository<TrafficEventChannel, Integer> {

    // Lấy tất cả channel mapping của 1 event — dùng cho diff-sync khi update
    List<TrafficEventChannel> findByEventId(Integer eventId);

    // Tìm theo (event_id, channel_id) — dùng để kiểm tra conflict
    Optional<TrafficEventChannel> findByEventIdAndChannelId(Integer eventId, Integer channelId);

    // Lấy tất cả channel mapping của danh sách sự kiện
    List<TrafficEventChannel> findByEventIdIn(List<Integer> eventIds);

    /**
     * Load tất cả impact_rate theo (eventId, channelId) cho forecast.
     * impactRate trong DB lưu dạng 0-100, khi dùng trong ForecastInputData cần chia 100.
     * Dùng projection để tránh load toàn bộ entity và lazy loading.
     */
    @Query("SELECT tec.event.id AS eventId, " +
           "tec.channel.id AS channelId, " +
           "tec.impactRate AS impactRate " +
           "FROM TrafficEventChannel tec " +
           "WHERE tec.event.deletedAt IS NULL")
    List<EventImpactProjection> findAllImpactRates();
}
