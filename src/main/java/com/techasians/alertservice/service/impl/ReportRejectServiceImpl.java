package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.entity.RptDefinition;
import com.techasians.alertservice.entity.RptFile;
import com.techasians.alertservice.entity.RptInstance;
import com.techasians.alertservice.enums.RptAuditAction;
import com.techasians.alertservice.enums.RptDirection;
import com.techasians.alertservice.enums.RptInstanceStatus;
import com.techasians.alertservice.enums.RptNodeType;
import com.techasians.alertservice.enums.RptSourceMode;
import com.techasians.alertservice.exception.InvalidReportStateException;
import com.techasians.alertservice.exception.ResourceNotFoundException;
import com.techasians.alertservice.repository.RptDefinitionRepository;
import com.techasians.alertservice.repository.RptFileRepository;
import com.techasians.alertservice.repository.RptInstanceRepository;
import com.techasians.alertservice.service.AuditLogService;
import com.techasians.alertservice.service.ReportRejectService;
import com.techasians.alertservice.service.ReportValidationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Từ chối file input đang COMPLETED.
 * File vật lý được GIỮ NGUYÊN để có thể xem lại và tải về sau.
 * Thông tin reject lưu vào rpt_instance; lịch sử vào rpt_audit_log.
 */
@Service
public class ReportRejectServiceImpl implements ReportRejectService {

    private final RptInstanceRepository instanceRepository;
    private final RptFileRepository fileRepository;
    private final RptDefinitionRepository definitionRepository;
    private final AuditLogService auditLogService;
    private final ReportValidationService validationService;

    public ReportRejectServiceImpl(RptInstanceRepository instanceRepository,
                                   RptFileRepository fileRepository,
                                   RptDefinitionRepository definitionRepository,
                                   AuditLogService auditLogService,
                                   ReportValidationService validationService) {
        this.instanceRepository = instanceRepository;
        this.fileRepository = fileRepository;
        this.definitionRepository = definitionRepository;
        this.auditLogService = auditLogService;
        this.validationService = validationService;
    }

    @Override
    @Transactional
    public void rejectInput(Long instanceId, String reason, String actor) {
        // Validate actor và reason
        validationService.validateActor(actor);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Lý do từ chối không được để trống");
        }

        // Load instance
        RptInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Instance không tồn tại: id=" + instanceId
                ));

        // Chỉ cho phép reject khi đang COMPLETED
        if (instance.getStatus() != RptInstanceStatus.COMPLETED) {
            throw new InvalidReportStateException(
                    "Chỉ có thể từ chối file đang ở trạng thái COMPLETED. " +
                    "Trạng thái hiện tại: " + instance.getStatus()
            );
        }

        // Validate definition: phải là input-file-import-active
        RptDefinition definition = definitionRepository.findById(instance.getDefinitionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Definition không tồn tại: id=" + instance.getDefinitionId()
                ));

        if (definition.getDirection() != RptDirection.INPUT
                || definition.getNodeType() != RptNodeType.FILE
                || definition.getSourceMode() != RptSourceMode.IMPORT
                || !definition.isActive()) {
            throw new InvalidReportStateException(
                    "Definition không hỗ trợ thao tác từ chối"
            );
        }

        if (instance.getCurrentFileId() == null) {
            throw new ResourceNotFoundException("Instance chưa có file hiện tại: id=" + instanceId);
        }
        RptFile existingFile = fileRepository.findById(instance.getCurrentFileId())
                .filter(file -> instanceId.equals(file.getReportInstanceId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy file hiện tại của instance: id=" + instanceId));

        // Cập nhật trạng thái — KHÔNG xóa rpt_file và file vật lý
        LocalDateTime now = LocalDateTime.now();
        instance.setStatus(RptInstanceStatus.REJECTED)
                .setRejectedReason(reason)
                .setRejectedBy(actor)
                .setRejectedAt(now)
                .setUpdatedBy(actor);
        instanceRepository.save(instance);

        // Ghi audit REJECT cho đúng phiên bản file hiện tại bị từ chối.
        auditLogService.log(
                instance.getWeekStartDate(),
                instanceId,
                existingFile.getId(),
                RptAuditAction.REJECT,
                actor,
                reason
        );
    }
}
