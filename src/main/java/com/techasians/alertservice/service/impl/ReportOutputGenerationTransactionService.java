package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.entity.*;
import com.techasians.alertservice.enums.*;
import com.techasians.alertservice.exception.*;
import com.techasians.alertservice.repository.*;
import com.techasians.alertservice.service.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ReportOutputGenerationTransactionService {
    private static final long GENERATION_TIMEOUT_MINUTES = 5;

    private final RptInstanceRepository instanceRepository;
    private final RptFileRepository fileRepository;
    private final RptOutputSourceFileRepository sourceFileRepository;
    private final RptOutputBlockDefRepository blockRepository;
    private final RptOutputSourceMapRepository sourceMapRepository;
    private final AuditLogService auditLogService;

    public ReportOutputGenerationTransactionService(RptInstanceRepository instanceRepository,
            RptFileRepository fileRepository, RptOutputSourceFileRepository sourceFileRepository,
            RptOutputBlockDefRepository blockRepository, RptOutputSourceMapRepository sourceMapRepository,
            AuditLogService auditLogService) {
        this.instanceRepository = instanceRepository;
        this.fileRepository = fileRepository;
        this.sourceFileRepository = sourceFileRepository;
        this.blockRepository = blockRepository;
        this.sourceMapRepository = sourceMapRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public GenerationAllocation prepare(Long definitionId, LocalDate weekStartDate, String actor) {
        Optional<RptInstance> existing = instanceRepository
                .findByDefinitionIdAndWeekStartDateWithLock(definitionId, weekStartDate);
        LocalDateTime now = LocalDateTime.now();
        RptInstance instance;
        if (existing.isPresent()) {
            instance = existing.get();
            if (instance.getStatus() == RptInstanceStatus.GENERATING
                    && (instance.getUpdatedAt() == null
                    || instance.getUpdatedAt().isAfter(now.minusMinutes(GENERATION_TIMEOUT_MINUTES)))) {
                throw new InvalidReportStateException("Output đang được generate");
            }
        } else {
            instance = new RptInstance().setDefinitionId(definitionId).setWeekStartDate(weekStartDate)
                    .setCreatedBy(actor);
        }
        // Buộc cập nhật thời gian khi gen lại, kể cả status và actor vẫn giữ nguyên.
        instance.setStatus(RptInstanceStatus.GENERATING).setUpdatedBy(actor).setUpdatedAt(now);
        instance = instanceRepository.save(instance);
        RptFile latest = fileRepository.findTopByReportInstanceIdOrderByVersionNoDesc(instance.getId());
        return new GenerationAllocation(instance.getId(), latest == null ? 1 : latest.getVersionNo() + 1,
                latest != null);
    }

    @Transactional
    public RptFile commit(GenerationAllocation allocation, String actor, StoredReportFile storedFile) {
        RptInstance instance = instanceRepository.findByIdWithLock(allocation.instanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Output instance không tồn tại"));
        if (instance.getStatus() != RptInstanceStatus.GENERATING) {
            throw new InvalidReportStateException("Output không còn ở trạng thái GENERATING");
        }
        RptFile newFile = fileRepository.save(new RptFile().setReportInstanceId(instance.getId())
                .setVersionNo(allocation.versionNo).setOriginalFilename(storedFile.getOriginalFilename())
                .setStorageObjectName(storedFile.getObjectName()).setFileSize(storedFile.getFileSize())
                .setSha256(storedFile.getSha256()).setCreatedBy(actor).setUpdatedBy(actor));

        List<RptOutputBlockDef> blocks = blockRepository
                .findByOutputDefinitionIdAndStatusOrderBySortOrder(instance.getDefinitionId(), 1);
        for (RptOutputBlockDef block : blocks) {
            // Nhiều mapping sheet có thể cùng dùng một file; bảng lịch sử chỉ lưu một lần cho mỗi block/file.
            Set<Long> recordedSourceFileIds = new HashSet<>();
            for (RptOutputSourceMap map : sourceMapRepository
                    .findByOutputBlockIdAndStatusOrderBySortOrder(block.getId(), 1)) {
                RptInstance sourceInstance = instanceRepository
                        .findByDefinitionIdAndWeekStartDate(map.getSourceDefinitionId(), instance.getWeekStartDate())
                        .orElseThrow(() -> new InvalidReportStateException("Thiếu source instance"));
                if (recordedSourceFileIds.add(sourceInstance.getCurrentFileId())) {
                    sourceFileRepository.save(new RptOutputSourceFile().setOutputFileId(newFile.getId())
                            .setSourceFileId(sourceInstance.getCurrentFileId()).setOutputBlockId(block.getId()));
                }
            }
        }
        instance.setCurrentFileId(newFile.getId()).setStatus(RptInstanceStatus.COMPLETED).setUpdatedBy(actor);
        instanceRepository.save(instance);
        auditLogService.log(instance.getWeekStartDate(), instance.getId(), newFile.getId(),
                allocation.regeneration ? RptAuditAction.REGENERATE : RptAuditAction.GENERATE, actor);
        return newFile;
    }

    @Transactional
    public void markError(Long instanceId, String actor) {
        instanceRepository.findByIdWithLock(instanceId).ifPresent(instance -> {
            instance.setStatus(instance.getCurrentFileId() == null ? RptInstanceStatus.ERROR : RptInstanceStatus.STALE)
                    .setUpdatedBy(actor);
            instanceRepository.save(instance);
        });
    }

    public static class GenerationAllocation {
        private final Long instanceId;
        private final Integer versionNo;
        private final boolean regeneration;

        public GenerationAllocation(Long instanceId, Integer versionNo, boolean regeneration) {
            this.instanceId = instanceId;
            this.versionNo = versionNo;
            this.regeneration = regeneration;
        }

        public Long getInstanceId() { return instanceId; }
        public Integer getVersionNo() { return versionNo; }
        public boolean isRegeneration() { return regeneration; }
    }
}
