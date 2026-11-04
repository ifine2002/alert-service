package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.entity.RptDefinition;
import com.techasians.alertservice.entity.RptFile;
import com.techasians.alertservice.entity.RptInstance;
import com.techasians.alertservice.enums.RptDirection;
import com.techasians.alertservice.enums.RptFileKind;
import com.techasians.alertservice.enums.RptInstanceStatus;
import com.techasians.alertservice.exception.InvalidReportStateException;
import com.techasians.alertservice.exception.ReportPreviewGenerationException;
import com.techasians.alertservice.exception.ResourceNotFoundException;
import com.techasians.alertservice.exception.UnsupportedReportPreviewException;
import com.techasians.alertservice.repository.RptDefinitionRepository;
import com.techasians.alertservice.repository.RptFileRepository;
import com.techasians.alertservice.repository.RptInstanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đọc và kiểm tra metadata preview trong một transaction ngắn.
 * Class riêng bảo đảm @Transactional được gọi qua Spring proxy.
 */
@Service
public class WordPreviewQueryService {

    private final RptInstanceRepository instanceRepository;
    private final RptDefinitionRepository definitionRepository;
    private final RptFileRepository fileRepository;

    public WordPreviewQueryService(RptInstanceRepository instanceRepository,
                                   RptDefinitionRepository definitionRepository,
                                   RptFileRepository fileRepository) {
        this.instanceRepository = instanceRepository;
        this.definitionRepository = definitionRepository;
        this.fileRepository = fileRepository;
    }

    @Transactional(readOnly = true)
    public WordPreviewContext loadPreviewContext(Long instanceId) {
        RptInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Instance không tồn tại: id=" + instanceId));

        RptDefinition definition = definitionRepository.findById(instance.getDefinitionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Definition không tồn tại: id=" + instance.getDefinitionId()));

        if (instance.getCurrentFileId() == null) {
            throw new ResourceNotFoundException("Instance chưa có file hiện tại: id=" + instanceId);
        }
        RptFile reportFile = fileRepository.findById(instance.getCurrentFileId())
                .filter(file -> instanceId.equals(file.getReportInstanceId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy file hiện tại của instance: id=" + instanceId));

        validateWordFile(definition);
        validatePreviewAllowed(definition, instance);
        validateDocxExtension(reportFile.getOriginalFilename());

        return new WordPreviewContext(
                reportFile.getOriginalFilename(),
                reportFile.getStorageObjectName()
        );
    }

    private void validateWordFile(RptDefinition definition) {
        if (definition.getFileKind() != RptFileKind.WORD) {
            throw new UnsupportedReportPreviewException(
                    "Chức năng preview chỉ hỗ trợ file Word. "
                    + "File Excel vui lòng dùng chức năng tải xuống.");
        }
    }

    private void validatePreviewAllowed(RptDefinition definition, RptInstance instance) {
        RptInstanceStatus status = instance.getStatus();

        if (definition.getDirection() == RptDirection.INPUT) {
            if (status == RptInstanceStatus.COMPLETED
                    || status == RptInstanceStatus.REJECTED
                    || status == RptInstanceStatus.ERROR) {
                return;
            }
        } else {
            if (status == RptInstanceStatus.COMPLETED
                    || status == RptInstanceStatus.STALE
                    || status == RptInstanceStatus.ERROR) {
                return;
            }
        }

        throw new InvalidReportStateException(
                "Không thể xem trước ở trạng thái hiện tại: " + status.name());
    }

    private void validateDocxExtension(String originalFilename) {
        if (originalFilename == null
                || !originalFilename.toLowerCase().endsWith(".docx")) {
            throw new ReportPreviewGenerationException(
                    "File hiện tại không phải định dạng .docx: " + originalFilename);
        }
    }
}
