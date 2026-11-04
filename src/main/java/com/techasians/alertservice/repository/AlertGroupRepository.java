package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.AlertGroup;
import com.techasians.alertservice.dto.AlertGroupListProjection;
import com.techasians.alertservice.enums.TargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertGroupRepository extends JpaRepository<AlertGroup, Integer> {

    @Query("SELECT a FROM AlertGroup a WHERE a.deletedAt IS NULL AND a.code IN :codes")
    List<AlertGroup> findByCodeInAndDeletedFalse(@Param("codes") List<String> codes);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    boolean existsByCodeAndIdNotAndDeletedAtIsNull(String code, Integer id);

    @Query(value = "SELECT " +
           "    ag.id AS id, " +
           "    ag.code AS code, " +
           "    ag.name AS name, " +
           "    ag.status AS status, " +
           "    ag.description AS description, " +
           "    COUNT(DISTINCT agm.id) AS memberCount, " +
           "    GROUP_CONCAT(DISTINCT COALESCE(sqo.name, sqc.name) SEPARATOR ', ') AS targetNames " +
           "FROM alert_groups ag " +
           "LEFT JOIN alert_group_targets agt ON agt.alert_group_id = ag.id " +
           "LEFT JOIN service_quality_orgs sqo ON agt.target_id = sqo.id AND agt.target_type = 'ORG' " +
           "LEFT JOIN service_quality_categories sqc ON agt.target_id = sqc.id AND agt.target_type = 'CATEGORY' " +
           "LEFT JOIN alert_group_members agm ON agm.alert_group_id = ag.id " +
           "WHERE ag.deleted_at IS NULL " +
           "  AND (:keyword IS NULL OR :keyword = '' " +
           "       OR LOWER(ag.code) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!' " +
           "       OR LOWER(ag.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!' ) " +
           "  AND (:status IS NULL OR ag.status = :status) " +
           "GROUP BY ag.id, ag.code, ag.name, ag.status, ag.description, ag.updated_at " +
           "ORDER BY ag.updated_at DESC",
           countQuery = "SELECT COUNT(DISTINCT ag.id) " +
           "FROM alert_groups ag " +
           "WHERE ag.deleted_at IS NULL " +
           "  AND (:keyword IS NULL OR :keyword = '' " +
           "       OR LOWER(ag.code) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!' " +
           "       OR LOWER(ag.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!' ) " +
           "  AND (:status IS NULL OR ag.status = :status)",
           nativeQuery = true)
    Page<AlertGroupListProjection> searchAlertGroupsNative(@Param("keyword") String keyword, 
                                                           @Param("status") Integer status, 
                                                           Pageable pageable);

    @Query("SELECT DISTINCT g FROM AlertLevelRecipient r " +
           "JOIN r.alertGroup g " +
           "JOIN AlertGroupTarget t ON t.alertGroup.id = g.id " +
           "WHERE r.alertLevel.id = :levelId " +
           "AND t.targetId = :targetId " +
           "AND t.targetType = :targetType " +
           "AND g.status = 1 AND g.deletedAt IS NULL")
    List<AlertGroup> findGroupsByLevelAndTarget(@Param("levelId") Integer levelId, 
                                                @Param("targetId") Long targetId, 
                                                @Param("targetType") TargetType targetType);
}
