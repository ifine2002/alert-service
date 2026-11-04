package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.TrafficChannelMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrafficChannelMetricRepository extends JpaRepository<TrafficChannelMetric, Long> {

    // Lấy tất cả metric của một kênh, sắp xếp theo thứ tự hiển thị
    @Query("SELECT cm FROM TrafficChannelMetric cm " +
            "JOIN FETCH cm.metric m " +
            "WHERE cm.channel.id = :channelId AND cm.status = 1 " +
            "ORDER BY cm.displayOrder ASC")
    List<TrafficChannelMetric> findActiveByChannelId(@Param("channelId") Integer channelId);

    // Lấy tất cả metric của nhiều kênh trong 1 query (tránh N+1)
    @Query("SELECT cm FROM TrafficChannelMetric cm " +
            "JOIN FETCH cm.channel c " +
            "JOIN FETCH cm.metric m " +
            "WHERE cm.channel.id IN (:channelIds) AND cm.status = 1 " +
            "ORDER BY cm.channel.id ASC, cm.displayOrder ASC")
    List<TrafficChannelMetric> findActiveByChannelIds(@Param("channelIds") List<Integer> channelIds);
}
