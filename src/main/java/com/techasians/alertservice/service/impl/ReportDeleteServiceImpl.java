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
import com.techasians.alertservice.repository.RptOutputSourceMapRepository;
import com.techasians.alertservice.service.AuditLogService;
import com.techasians.alertservice.service.ReportDeleteService;
import com.techasians.alertservice.service.ReportValidationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
public class ReportDeleteServiceImpl implements ReportDeleteService {
    private final RptInstanceRepository instanceRepository;
    private final RptFileRepository fileRepository;
    private final RptDefinitionRepository definitionRepository;
    private final RptOutputSourceMapRepository sourceMapRepository;
    private final AuditLogService auditLogService;
    private final ReportValidationService validationService;

    public ReportDeleteServiceImpl(RptInstanceRepository instanceRepository,
                                   RptFileRepository fileRepository,
                                   RptDefinitionRepository definitionRepository,
                                   RptOutputSourceMapRepository sourceMapRepository,
                                   AuditLogService auditLogService,
                                   ReportValidationService validationService) {
        this.instanceRepository = instanceRepository;
        this.fileRepository = fileRepository;
        this.definitionRepository = definitionRepository;
        this.sourceMapRepository = sourceMapRepository;
        this.auditLogService = auditLogService;
        this.validationService = validationService;
    }

    @Override
    @Transactional
    public void deleteInputFile(Long instanceId, String actor, String reason) {
        validationService.validateActor(actor);
        String normalizedReason = normalizeReason(reason);

        RptInstance inputInstance = instanceRepository.findByIdWithLock(instanceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Instance không tồn tại: id=" + instanceId));
        validateInputDefinition(inputInstance.getDefinitionId());
        validateInputInstance(inputInstance);

        Long deletedFileId = inputInstance.getCurrentFileId();
        RptFile deletedFile = fileRepository.findById(deletedFileId)
                .filter(file -> instanceId.equals(file.getReportInstanceId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy file hiện tại của instance: id=" + instanceId));

        List<Long> outputDefinitionIds = sourceMapRepository
                .findActiveOutputDefinitionIdsBySourceDefinitionId(inputInstance.getDefinitionId());
        List<RptInstance> outputInstances = loadOutputInstances(inputInstance, outputDefinitionIds);
        validateOutputsNotGenerating(outputInstances);

        auditLogService.log(inputInstance.getWeekStartDate(), inputInstance.getId(),
                deletedFile.getId(), RptAuditAction.DELETE, actor, normalizedReason);

        clearInstance(inputInstance, actor);
        for (RptInstance outputInstance : outputInstances) {
            clearInstance(outputInstance, actor);
        }
        instanceRepository.save(inputInstance);
        instanceRepository.saveAll(outputInstances);
    }

    private void validateInputDefinition(Long definitionId) {
        RptDefinition definition = definitionRepository.findById(definitionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Definition không tồn tại: id=" + definitionId));
        boolean valid = definition.isActive()
                && definition.getDirection() == RptDirection.INPUT
                && definition.getNodeType() == RptNodeType.FILE
                && definition.getSourceMode() == RptSourceMode.IMPORT;
        if (!valid) {
            throw new InvalidReportStateException("Definition không hỗ trợ thao tác xóa file input");
        }
    }

    private void validateInputInstance(RptInstance instance) {
        if (instance.getStatus() != RptInstanceStatus.COMPLETED
                && instance.getStatus() != RptInstanceStatus.REJECTED) {
            throw new InvalidReportStateException(
                    "Chỉ có thể xóa file input ở trạng thái COMPLETED hoặc REJECTED. Trạng thái hiện tại: "
                            + instance.getStatus());
        }
        if (instance.getCurrentFileId() == null) {
            throw new ResourceNotFoundException("Instance chưa có file hiện tại: id=" + instance.getId());
        }
    }

    private List<RptInstance> loadOutputInstances(RptInstance inputInstance, List<Long> outputDefinitionIds) {
        if (outputDefinitionIds == null || outputDefinitionIds.isEmpty()) {
            return Collections.emptyList();
        }
        return instanceRepository.findByWeekStartDateAndDefinitionIdInOrderByIdAsc(
                inputInstance.getWeekStartDate(), outputDefinitionIds);
    }

    private void validateOutputsNotGenerating(List<RptInstance> outputInstances) {
        for (RptInstance outputInstance : outputInstances) {
            if (outputInstance.getStatus() == RptInstanceStatus.GENERATING) {
                throw new InvalidReportStateException(
                        "Không thể xóa input khi output đang được tạo: instanceId=" + outputInstance.getId());
            }
        }
    }

    private void clearInstance(RptInstance instance, String actor) {
        instance.setStatus(RptInstanceStatus.EMPTY)
                .setCurrentFileId(null)
                .setRejectedReason(null)
                .setRejectedBy("")
                .setRejectedAt(null)
                .setUpdatedBy(actor);
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String normalizedReason = reason.trim();
        if (normalizedReason.length() > 2000) {
            throw new IllegalArgumentException("Lý do xóa không được vượt quá 2000 ký tự");
        }
        return normalizedReason;
    }
}