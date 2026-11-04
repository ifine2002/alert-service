package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.entity.RptAuditLog;
import com.techasians.alertservice.enums.RptAuditAction;
import com.techasians.alertservice.repository.RptAuditLogRepository;
import com.techasians.alertservice.service.AuditLogService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Ghi audit log vào bảng rpt_audit_log.
 * Dùng enum RptAuditAction thay vì số nguyên để tránh nhầm ordinal.
 */
@Service
public class AuditLogServiceImpl implements AuditLogService {

    private final RptAuditLogRepository auditLogRepository;

    public AuditLogServiceImpl(RptAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public void log(LocalDate weekStartDate,
                    Long reportInstanceId,
                    Long fileId,
                    RptAuditAction action,
                    String actor,
                    String reason) {
        RptAuditLog auditLog = new RptAuditLog()
                .setWeekStartDate(weekStartDate)
                .setReportInstanceId(reportInstanceId)
                .setFileId(fileId)
                .setAction(action)
                .setActor(actor == null ? "" : actor)
                .setReason(reason);
        auditLogRepository.save(auditLog);
    }
}
