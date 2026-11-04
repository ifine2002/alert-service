package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.AlertLevelChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertLevelChannelRepository extends JpaRepository<AlertLevelChannel, Integer> {

    // Lấy tất cả kênh nhận của một mức cảnh báo
    List<AlertLevelChannel> findByAlertLevelId(Integer alertLevelId);

    // Lấy tất cả kênh nhận cùng với NotificationChannel trong 1 câu query (Giải quyết N+1)
    @Query("SELECT c FROM AlertLevelChannel c " +
           "JOIN FETCH c.notificationChannel nc " +
           "WHERE c.alertLevel.id = :alertLevelId")
    List<AlertLevelChannel> findByAlertLevelIdWithChannel(@Param("alertLevelId") Integer alertLevelId);

    // Xóa toàn bộ kênh nhận của một mức cảnh báo (dùng khi Update theo cơ chế "Xóa và Chèn lại")
    @Modifying
    @Query("DELETE FROM AlertLevelChannel c WHERE c.alertLevel.id = :alertLevelId")
    void deleteByAlertLevelId(@Param("alertLevelId") Integer alertLevelId);

    // Lấy tất cả kênh nhận cho một danh sách các mức cảnh báo (tránh N+1 Query)
    List<AlertLevelChannel> findByAlertLevelIdIn(List<Integer> alertLevelIds);
}
