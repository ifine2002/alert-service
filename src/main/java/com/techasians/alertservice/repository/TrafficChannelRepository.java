package com.techasians.alertservice.repository;

import java.util.List;

import com.techasians.alertservice.entity.TrafficChannel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TrafficChannelRepository extends JpaRepository<TrafficChannel, Integer> {

    List<TrafficChannel> findByStatus(Integer status);

    @Query("SELECT c.id as id, c.name as name FROM TrafficChannel c WHERE c.status = :status")
    List<com.techasians.alertservice.dto.TrafficChannelTemplateProjection> findActiveChannelsForTemplate(@Param("status") Integer status);

    @Query("SELECT DISTINCT c FROM TrafficChannel c " +
            "JOIN TrafficRegionChannel trc ON trc.channel.id = c.id " +
            "WHERE c.status = 1 AND trc.status = 1")
    Page<TrafficChannel> findAllActiveChannels(Pageable pageable);

    @Query("SELECT DISTINCT c FROM TrafficChannel c " +
            "JOIN TrafficRegionChannel trc ON trc.channel.id = c.id " +
            "WHERE trc.region.id IN (:regionIds) " +
            "AND c.status = 1 AND trc.status = 1")
    Page<TrafficChannel> searchChannelsByRegionIds(@Param("regionIds") List<Long> regionIds,
                                        Pageable pageable);
}
