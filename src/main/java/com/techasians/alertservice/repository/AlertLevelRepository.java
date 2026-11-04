package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.AlertLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertLevelRepository extends JpaRepository<AlertLevel, Integer> {

    // Lấy tất cả các mức cảnh báo thuộc một AlertConfig
    List<AlertLevel> findByAlertConfigId(Integer alertConfigId);

    // Xóa toàn bộ các mức cảnh báo thuộc một AlertConfig (dùng khi Update)
    @Modifying
    @Query("DELETE FROM AlertLevel a WHERE a.alertConfig.id = :alertConfigId")
    void deleteByAlertConfigId(@Param("alertConfigId") Integer alertConfigId);

    /**
     * [BATCH] Lấy TẤT CẢ alert_levels của NHIỀU alertConfigId cùng lúc.
     *
     * Dùng trong KpiCalculationService để load toàn bộ levels 1 lần.
     * Ví dụ: có 5 alert_configs → 1 query thay vì 5 query.
     * KpiCalculationService tự group theo alertConfig.id khi xử lý.
     */
    List<AlertLevel> findByAlertConfigIdIn(List<Integer> alertConfigIds);
}
