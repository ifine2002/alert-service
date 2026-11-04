package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.RptInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RptInstanceRepository extends JpaRepository<RptInstance, Long> {

    // Tìm instance theo definition và tuần (unique key trong DB)
    Optional<RptInstance> findByDefinitionIdAndWeekStartDate(Long definitionId, LocalDate weekStartDate);

    // Lấy tất cả instance trong một tuần (dùng cho màn hình tổng quan theo kỳ)
    List<RptInstance> findByWeekStartDate(LocalDate weekStartDate);

    // Lấy instance của các nguồn bắt buộc trong một query để tránh N+1.
    List<RptInstance> findByWeekStartDateAndDefinitionIdIn(
            LocalDate weekStartDate, List<Long> definitionIds);

    // Lấy các output instance phụ thuộc trong cùng kỳ báo cáo.
    List<RptInstance> findByWeekStartDateAndDefinitionIdInOrderByIdAsc(
            LocalDate weekStartDate, List<Long> definitionIds);

    // Lấy tất cả instance của một definition (dùng để xem lịch sử theo definition)
    List<RptInstance> findByDefinitionIdOrderByWeekStartDateDesc(Long definitionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM RptInstance i WHERE i.id = :instanceId")
    Optional<RptInstance> findByIdWithLock(@Param("instanceId") Long instanceId);

    // Tìm instance với pessimistic write lock — dùng khi import/re-import để chặn hai request đồng thời.
    // Nếu hai request cùng gọi vào (definition, week), request sau sẽ chờ request trước hoàn thành.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM RptInstance i WHERE i.definitionId = :definitionId AND i.weekStartDate = :weekStartDate")
    Optional<RptInstance> findByDefinitionIdAndWeekStartDateWithLock(
            @Param("definitionId") Long definitionId,
            @Param("weekStartDate") LocalDate weekStartDate
    );
}
