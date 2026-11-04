package com.techasians.alertservice.repository;

import com.techasians.alertservice.dto.ReportActionHistoryProjection;
import com.techasians.alertservice.entity.RptAuditLog;
import com.techasians.alertservice.enums.RptAuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface RptAuditLogRepository extends JpaRepository<RptAuditLog, Long> {

    // Lấy lịch sử thao tác của một instance, mới nhất lên trước
    List<RptAuditLog> findByReportInstanceIdOrderByCreatedAtDesc(Long reportInstanceId);

    @Query("SELECT instance.definitionId AS definitionId, audit.reportInstanceId AS instanceId, " +
            "definition.name AS definitionName, audit.weekStartDate AS weekStartDate, " +
            "audit.action AS action, audit.actor AS actor, audit.createdAt AS createdAt, " +
            "audit.reason AS reason, file.id AS fileId, file.originalFilename AS fileName " +
            "FROM RptAuditLog audit " +
            "JOIN RptInstance instance ON instance.id = audit.reportInstanceId " +
            "JOIN RptDefinition definition ON definition.id = instance.definitionId " +
            "JOIN RptFile file ON file.id = audit.fileId " +
            "WHERE (:fromCreatedAt IS NULL OR audit.createdAt >= :fromCreatedAt) " +
            "AND (:toCreatedAtExclusive IS NULL OR audit.createdAt < :toCreatedAtExclusive) " +
            "AND (:reportName IS NULL OR LOWER(definition.name) LIKE LOWER(CONCAT('%', :reportName, '%'))) " +
            "ORDER BY audit.createdAt DESC, audit.id DESC")
    Page<ReportActionHistoryProjection> searchActionHistory(
            @Param("fromCreatedAt") LocalDateTime fromCreatedAt,
            @Param("toCreatedAtExclusive") LocalDateTime toCreatedAtExclusive,
            @Param("reportName") String reportName,
            Pageable pageable);

    @Query("SELECT instance.definitionId AS definitionId, audit.reportInstanceId AS instanceId, " +
            "definition.name AS definitionName, audit.weekStartDate AS weekStartDate, " +
            "audit.action AS action, audit.actor AS actor, audit.createdAt AS createdAt, " +
            "audit.reason AS reason, file.id AS fileId, file.originalFilename AS fileName " +
            "FROM RptAuditLog audit " +
            "JOIN RptInstance instance ON instance.id = audit.reportInstanceId " +
            "JOIN RptDefinition definition ON definition.id = instance.definitionId " +
            "JOIN RptFile file ON file.id = audit.fileId " +
            "WHERE (:fromCreatedAt IS NULL OR audit.createdAt >= :fromCreatedAt) " +
            "AND (:toCreatedAtExclusive IS NULL OR audit.createdAt < :toCreatedAtExclusive) " +
            "AND (:reportName IS NULL OR LOWER(definition.name) LIKE LOWER(CONCAT('%', :reportName, '%'))) " +
            "AND audit.action IN :actions " +
            "ORDER BY audit.createdAt DESC, audit.id DESC")
    Page<ReportActionHistoryProjection> searchActionHistoryByActions(
            @Param("fromCreatedAt") LocalDateTime fromCreatedAt,
            @Param("toCreatedAtExclusive") LocalDateTime toCreatedAtExclusive,
            @Param("reportName") String reportName,
            @Param("actions") List<RptAuditAction> actions,
            Pageable pageable);
}
