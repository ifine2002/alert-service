package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.TrafficChannelKpi;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

import com.techasians.alertservice.dto.TrafficChannelKpiProjection;

@Repository
public interface TrafficChannelKpiRepository extends JpaRepository<TrafficChannelKpi, Integer> {

    Optional<TrafficChannelKpi> findByIdAndDeletedAtIsNull(Integer id);

    boolean existsByChannelIdAndDeletedAtIsNull(Integer channelId);

    List<TrafficChannelKpi> findByChannelIdInAndDeletedAtIsNull(List<Integer> channelIds);

    boolean existsByChannelIdAndIdNotAndDeletedAtIsNull(Integer channelId, Integer id);

    @Query(
            value = "SELECT " +
                    "tk.id AS id, " +
                    "tk.channelId AS channelId, " +
                    "tk.kpiRate AS kpiRate, " +
                    "tk.productivityValue AS productivityValue, " +
                    "tc.name AS channelName, " +
                    "tk.createdAt AS createdAt, " +
                    "tk.createdBy AS createdBy, " +
                    "tk.updatedAt AS updatedAt, " +
                    "tk.updatedBy AS updatedBy " +
                    "FROM TrafficChannelKpi tk " +
                    "LEFT JOIN TrafficChannel tc ON tk.channelId = tc.id " +
                    "WHERE tk.deletedAt IS NULL " +
                    "AND (:channelId IS NULL OR tk.channelId = :channelId) " +
                    "AND (" +
                    "    :keyword IS NULL " +
                    "    OR LOWER(tc.name) LIKE LOWER(CONCAT('%', :keyword, '%'))" +
                    ")",
            countQuery = "SELECT COUNT(tk.id) " +
                    "FROM TrafficChannelKpi tk " +
                    "LEFT JOIN TrafficChannel tc ON tk.channelId = tc.id " +
                    "WHERE tk.deletedAt IS NULL " +
                    "AND (:channelId IS NULL OR tk.channelId = :channelId) " +
                    "AND (" +
                    "    :keyword IS NULL " +
                    "    OR LOWER(tc.name) LIKE LOWER(CONCAT('%', :keyword, '%'))" +
                    ")"
    )
    Page<TrafficChannelKpiProjection> search(
            @Param("channelId") Integer channelId,
            @Param("keyword") String keyword,
            Pageable pageable);
}
