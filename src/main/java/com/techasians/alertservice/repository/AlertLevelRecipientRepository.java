package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.AlertLevelRecipient;
import com.techasians.alertservice.entity.AlertUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertLevelRecipientRepository extends JpaRepository<AlertLevelRecipient, Integer> {

    // Lấy tất cả đầu mối nhận của một mức cảnh báo
    List<AlertLevelRecipient> findByAlertLevelId(Integer alertLevelId);

    // Lấy trực tiếp danh sách người dùng thông qua AlertGroupMember (không dùng g.members)
    @Query("SELECT DISTINCT u FROM AlertLevelRecipient r " +
           "JOIN r.alertGroup g " +
           "JOIN AlertGroupTarget t ON t.alertGroup.id = g.id " +
           "JOIN AlertGroupMember m ON m.alertGroup.id = g.id " +
           "JOIN m.alertUser u " +
           "WHERE r.alertLevel.id = :alertLevelId " +
           "AND t.targetId = :orgId AND t.targetType = com.techasians.alertservice.enums.TargetType.ORG " +
           "AND g.status = 1 AND g.deletedAt IS NULL " +
           "AND u.deletedAt IS NULL")
    List<AlertUser> findUsersByAlertLevelIdAndOrgId(@Param("alertLevelId") Integer alertLevelId, @Param("orgId") Long orgId);

    // Xóa toàn bộ đầu mối nhận của một mức cảnh báo (dùng khi Update theo cơ chế "Xóa và Chèn lại")
    @Modifying
    @Query("DELETE FROM AlertLevelRecipient r WHERE r.alertLevel.id = :alertLevelId")
    void deleteByAlertLevelId(@Param("alertLevelId") Integer alertLevelId);

    // Lấy tất cả đầu mối nhận cho một danh sách các mức cảnh báo (tránh N+1 Query)
    List<AlertLevelRecipient> findByAlertLevelIdIn(List<Integer> alertLevelIds);
}
