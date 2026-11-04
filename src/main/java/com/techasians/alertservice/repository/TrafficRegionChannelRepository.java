package com.techasians.alertservice.repository;

import com.techasians.alertservice.dto.forecast.RegionChannelConfigProjection;
import com.techasians.alertservice.entity.TrafficRegionChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrafficRegionChannelRepository extends JpaRepository<TrafficRegionChannel, Long> {

    // Tìm mapping region-channel theo regionId và channelId
    Optional<TrafficRegionChannel> findByRegionIdAndChannelId(Long regionId, Integer channelId);

    // Tìm tất cả channel trong một region (đang hoạt động)
    @Query("SELECT trc FROM TrafficRegionChannel trc " +
            "JOIN FETCH trc.channel c " +
            "WHERE trc.region.id = :regionId AND trc.status = 1 AND c.status = 1")
    List<TrafficRegionChannel> findActiveByRegionId(@Param("regionId") Long regionId);

    /**
     * Load region-channel config cho forecast, kèm thông tin weekendAffected.
     * Filter theo regionIds và channelIds nếu có, null = lấy tất cả đang active.
     *
     * Trả về projection để tránh lazy loading entity.
     */
    @Query("SELECT trc.id AS regionChannelId, " +
           "trc.channel.id AS channelId, " +
           "trc.region.id AS regionId, " +
           "trc.region.name AS regionName, " +
           "trc.channel.name AS channelName, " +
           "trc.channel.weekendAffected AS weekendAffected, " +
           "trc.channel.mondayAffected AS mondayAffected " +
           "FROM TrafficRegionChannel trc " +
           "WHERE trc.status = 1 " +
           "AND trc.region.status = 1 " +
           "AND trc.channel.status = 1 " +
           "AND (:regionIds IS NULL OR trc.region.id IN :regionIds) " +
           "AND (:channelIds IS NULL OR trc.channel.id IN :channelIds)")
    List<RegionChannelConfigProjection> findActiveForForecast(
            @Param("regionIds") List<Long> regionIds,
            @Param("channelIds") List<Integer> channelIds
    );
}
