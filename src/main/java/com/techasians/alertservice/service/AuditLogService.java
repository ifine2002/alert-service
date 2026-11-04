package com.techasians.alertservice.service;

import com.techasians.alertservice.enums.RptAuditAction;

import java.time.LocalDate;

/**
 * Ghi lịch sử thao tác vào rpt_audit_log.
 * Tập trung mapping entity type và entity ID để tránh lặp code ở các service khác.
 */
public interface AuditLogService {

    /**
     * Ghi một bản ghi audit không có lý do.
     *
     * @param weekStartDate    tuần báo cáo liên quan
     * @param reportInstanceId ID instance liên quan (có thể null)
     * @param fileId           ID rpt_files cụ thể (có thể null)
     * @param action           hành động thực hiện
     * @param actor            người thực hiện (username)
     */
    default void log(LocalDate weekStartDate,
                     Long reportInstanceId,
                     Long fileId,
                     RptAuditAction action,
                     String actor) {
        log(weekStartDate, reportInstanceId, fileId, action, actor, null);
    }

    /**
     * Ghi một bản ghi audit kèm lý do.
     *
     * @param weekStartDate    tuần báo cáo liên quan
     * @param reportInstanceId ID instance liên quan (có thể null)
     * @param fileId           ID rpt_files cụ thể (có thể null)
     * @param action           hành động thực hiện
     * @param actor            người thực hiện (username)
     * @param reason           lý do thực hiện hành động (có thể null)
     */
    void log(LocalDate weekStartDate,
             Long reportInstanceId,
             Long fileId,
             RptAuditAction action,
             String actor,
             String reason);
}
