package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.entity.RptFile;
import com.techasians.alertservice.entity.RptInstance;
import com.techasians.alertservice.enums.RptAuditAction;
import com.techasians.alertservice.enums.RptInstanceStatus;
import com.techasians.alertservice.exception.InvalidReportStateException;
import com.techasians.alertservice.exception.ResourceNotFoundException;
import com.techasians.alertservice.repository.RptFileRepository;
import com.techasians.alertservice.repository.RptInstanceRepository;
import com.techasians.alertservice.service.AuditLogService;
import com.techasians.alertservice.service.StoredReportFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class ReportImportTransactionService {
    private final RptInstanceRepository instanceRepository;
    private final RptFileRepository fileRepository;
    private final AuditLogService auditLogService;

    public ReportImportTransactionService(RptInstanceRepository instanceRepository,
                                          RptFileRepository fileRepository,
                                          AuditLogService auditLogService) {
        this.instanceRepository = instanceRepository;
        this.fileRepository = fileRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public ImportAllocation prepare(Long definitionId, LocalDate weekStartDate, String actor) {
        Optional<RptInstance> existing =
                instanceRepository.findByDefinitionIdAndWeekStartDateWithLock(definitionId, weekStartDate);
        RptInstance instance;
        if (existing.isPresent()) {
            instance = existing.get();
            if (instance.getStatus() == RptInstanceStatus.PROCESSING) {
                throw new InvalidReportStateException("Instance đang xử lý, vui lòng thử lại sau");
            }
        } else {
            instance = new RptInstance()
                    .setDefinitionId(definitionId)
                    .setWeekStartDate(weekStartDate)
                    .setCreatedBy(actor);
        }
        instance.setStatus(RptInstanceStatus.PROCESSING).setUpdatedBy(actor);
        instance = instanceRepository.save(instance);

        RptFile latestFile = fileRepository.findTopByReportInstanceIdOrderByVersionNoDesc(instance.getId());
        int nextVersion = latestFile == null ? 1 : latestFile.getVersionNo() + 1;
        return new ImportAllocation(instance.getId(), nextVersion);
    }

    @Transactional
    public RptFile commit(ImportAllocation allocation, String actor, StoredReportFile storedFile) {
        RptInstance instance = instanceRepository.findById(allocation.getInstanceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Instance không tồn tại: id=" + allocation.getInstanceId()));
        if (instance.getStatus() != RptInstanceStatus.PROCESSING) {
            throw new InvalidReportStateException("Instance không còn ở trạng thái PROCESSING");
        }

        RptFile newFile = new RptFile()
                .setReportInstanceId(instance.getId())
                .setVersionNo(allocation.getVersionNo())
                .setOriginalFilename(storedFile.getOriginalFilename())
                .setStorageObjectName(storedFile.getObjectName())
                .setFileSize(storedFile.getFileSize())
                .setSha256(storedFile.getSha256())
                .setCreatedBy(actor)
                .setUpdatedBy(actor);
        newFile = fileRepository.save(newFile);

        instance.setCurrentFileId(newFile.getId())
                .setStatus(RptInstanceStatus.COMPLETED)
                .setRejectedReason(null)
                .setRejectedBy("")
                .setRejectedAt(null)
                .setUpdatedBy(actor);
        instanceRepository.save(instance);

        auditLogService.log(instance.getWeekStartDate(), instance.getId(), newFile.getId(), RptAuditAction.IMPORT, actor);
        return newFile;
    }

    @Transactional
    public void markError(Long instanceId, String actor) {
        instanceRepository.findById(instanceId).ifPresent(instance -> {
            instance.setStatus(RptInstanceStatus.ERROR).setUpdatedBy(actor);
            instanceRepository.save(instance);
        });
    }

    public static class ImportAllocation {
        private final Long instanceId;
        private final Integer versionNo;

        public ImportAllocation(Long instanceId, Integer versionNo) {
            this.instanceId = instanceId;
            this.versionNo = versionNo;
        }

        public Long getInstanceId() { return instanceId; }
        public Integer getVersionNo() { return versionNo; }
    }
}
