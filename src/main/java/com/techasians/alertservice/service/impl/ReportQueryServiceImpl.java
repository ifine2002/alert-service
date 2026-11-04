package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.dto.ReportActionHistoryProjection;
import com.techasians.alertservice.dto.ReportActionHistoryResponse;
import com.techasians.alertservice.dto.ReportDocumentSummaryResponse;
import com.techasians.alertservice.dto.ReportDownloadDto;
import com.techasians.alertservice.dto.ReportInputNodeResponse;
import com.techasians.alertservice.dto.ReportInputSearchRequest;
import com.techasians.alertservice.dto.ReportOutputResponse;
import com.techasians.alertservice.dto.OutputReadinessResult;
import com.techasians.alertservice.dto.ReportFileVersionResponse;
import com.techasians.alertservice.entity.RptDefinition;
import com.techasians.alertservice.entity.RptDefinitionLink;
import com.techasians.alertservice.entity.RptFile;
import com.techasians.alertservice.entity.RptInstance;
import com.techasians.alertservice.enums.RptAuditAction;
import com.techasians.alertservice.enums.RptDirection;
import com.techasians.alertservice.enums.RptInstanceStatus;
import com.techasians.alertservice.enums.RptNodeType;
import com.techasians.alertservice.exception.ResourceNotFoundException;
import com.techasians.alertservice.repository.RptAuditLogRepository;
import com.techasians.alertservice.repository.RptDefinitionLinkRepository;
import com.techasians.alertservice.repository.RptDefinitionRepository;
import com.techasians.alertservice.repository.RptFileRepository;
import com.techasians.alertservice.repository.RptInstanceRepository;
import com.techasians.alertservice.service.OutputReadinessService;
import com.techasians.alertservice.service.ReportFileService;
import com.techasians.alertservice.service.ReportQueryService;
import com.techasians.alertservice.service.ReportStatusService;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReportQueryServiceImpl implements ReportQueryService {
    private final RptDefinitionRepository definitionRepository;
    private final RptDefinitionLinkRepository linkRepository;
    private final RptInstanceRepository instanceRepository;
    private final RptFileRepository fileRepository;
    private final ReportStatusService statusService;
    private final ReportFileService reportFileService;
    private final OutputReadinessService outputReadinessService;
    private final RptAuditLogRepository auditLogRepository;

    public ReportQueryServiceImpl(RptDefinitionRepository definitionRepository,
            RptDefinitionLinkRepository linkRepository,
            RptInstanceRepository instanceRepository,
            RptFileRepository fileRepository,
            ReportStatusService statusService,
            ReportFileService reportFileService,
            OutputReadinessService outputReadinessService,
            RptAuditLogRepository auditLogRepository) {
        this.definitionRepository = definitionRepository;
        this.linkRepository = linkRepository;
        this.instanceRepository = instanceRepository;
        this.fileRepository = fileRepository;
        this.statusService = statusService;
        this.reportFileService = reportFileService;
        this.outputReadinessService = outputReadinessService;
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public List<ReportInputNodeResponse> listInputs(ReportInputSearchRequest request) {
        if (request == null || request.getWeekStartDate() == null) {
            throw new IllegalArgumentException("weekStartDate không được để trống");
        }

        String keyword = normalizeSearchText(request.getKeyword());
        List<RptInstanceStatus> statuses = request.getStatuses();

        List<RptDefinition> groups = definitionRepository
                .findByDirectionAndNodeTypeAndStatusOrderBySortOrderAscIdAsc(
                        RptDirection.INPUT, RptNodeType.GROUP, 1);
        List<RptDefinition> inputFiles = definitionRepository
                .findByDirectionAndNodeTypeAndStatusOrderBySortOrderAscIdAsc(
                        RptDirection.INPUT, RptNodeType.FILE, 1);
        if (inputFiles.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, RptDefinition> groupsById = new HashMap<>();
        for (RptDefinition group : groups) {
            groupsById.put(group.getId(), group);
        }

        List<Long> fileDefinitionIds = inputFiles.stream()
                .map(RptDefinition::getId).collect(Collectors.toList());
        List<RptDefinitionLink> links = linkRepository
                .findByChildDefinitionIdInAndStatusOrderByChildDefinitionIdAscSortOrderAsc(
                        fileDefinitionIds, 1);
        Map<Long, Long> parentIdByChildId = buildParentMap(links);
        ReportData reportData = loadReportData(request.getWeekStartDate());

        Map<Long, List<ReportInputNodeResponse>> childrenByGroupId = new HashMap<>();
        List<ReportInputNodeResponse> rootFiles = new ArrayList<>();
        for (RptDefinition inputFile : inputFiles) {
            Long parentId = parentIdByChildId.get(inputFile.getId());
            RptDefinition parentGroup = parentId == null ? null : groupsById.get(parentId);
            if (parentId != null && parentGroup == null) {
                throw new IllegalStateException(
                        "Group cha không tồn tại hoặc không hoạt động: definitionId=" + parentId);
            }

            ReportInputNodeResponse fileNode = toInputFileNode(parentId, inputFile, reportData);
            if (!matchesInputFilters(fileNode, parentGroup, keyword, statuses)) {
                continue;
            }
            if (parentGroup == null) {
                rootFiles.add(fileNode);
            } else {
                childrenByGroupId.computeIfAbsent(parentId, key -> new ArrayList<>()).add(fileNode);
            }
        }

        List<ReportInputNodeResponse> result = new ArrayList<>();
        for (RptDefinition group : groups) {
            List<ReportInputNodeResponse> children = childrenByGroupId.get(group.getId());
            if (children == null || children.isEmpty()) {
                continue;
            }
            result.add(toInputGroupNode(group, children, reportData));
        }
        result.addAll(rootFiles);
        result.sort((first, second) -> {
            RptDefinition firstDefinition = findDefinition(first.getDefinitionId(), groups, inputFiles);
            RptDefinition secondDefinition = findDefinition(second.getDefinitionId(), groups, inputFiles);
            int sortComparison = firstDefinition.getSortOrder().compareTo(secondDefinition.getSortOrder());
            if (sortComparison != 0)
                return sortComparison;
            return first.getDefinitionId().compareTo(second.getDefinitionId());
        });
        return result;
    }

    @Override
    public List<ReportOutputResponse> listOutputs(LocalDate weekStartDate) {
        List<RptDefinition> outputs = definitionRepository.findByDirectionAndNodeTypeAndStatusOrderBySortOrder(
                RptDirection.OUTPUT, RptNodeType.FILE, 1);
        ReportData data = loadReportData(weekStartDate);
        Map<Long, OutputReadinessResult> readinessByOutputId = outputReadinessService.checkReadinessBatch(outputs,
                weekStartDate);
        List<ReportOutputResponse> result = new ArrayList<>();
        for (RptDefinition definition : outputs) {
            RptInstance instance = data.instances.get(definition.getId());
            RptFile file = currentFile(instance, data.files);
            OutputReadinessResult readiness = readinessByOutputId.get(definition.getId());
            result.add(new ReportOutputResponse().setDefinitionId(definition.getId()).setName(definition.getName())
                    .setFileKind(definition.getFileKind()).setAllowedExtension(definition.getAllowedExtension())
                    .setInstanceId(instance == null ? null : instance.getId())
                    .setStatus(instance == null ? RptInstanceStatus.EMPTY : instance.getStatus())
                    .setReadinessConfigured(readiness.isConfigured())
                    .setReadyToGenerate(readiness.isReadyToGenerate())
                    .setMissingSourceIds(readiness.getMissingSourceIds())
                    .setFileName(file == null ? null : file.getOriginalFilename())
                    .setGenerationComplete(file == null ? null : file.getGenerationComplete())
                    .setGenerationWarnings(file == null ? null : file.getGenerationWarnings())
                    .setUpdatedAt(instance == null ? null : instance.getUpdatedAt()));
        }
        return result;
    }

    @Override
    public List<ReportDocumentSummaryResponse> listDocuments(LocalDate weekStartDate, RptDirection direction) {
        List<RptDefinition> definitions;
        if (direction == null) {
            definitions = definitionRepository.findByNodeTypeAndStatusOrderByDirectionAscSortOrderAsc(RptNodeType.FILE,
                    1);
        } else {
            definitions = definitionRepository.findByDirectionAndNodeTypeAndStatusOrderBySortOrder(
                    direction, RptNodeType.FILE, 1);
        }
        Map<Long, RptInstance> instances = new HashMap<>();
        for (RptInstance instance : instanceRepository.findByWeekStartDate(weekStartDate)) {
            instances.put(instance.getDefinitionId(), instance);
        }
        List<ReportDocumentSummaryResponse> result = new ArrayList<>();
        for (RptDefinition definition : definitions) {
            RptInstance instance = instances.get(definition.getId());
            result.add(new ReportDocumentSummaryResponse()
                    .setDefinitionId(definition.getId())
                    .setName(definition.getName())
                    .setDirection(definition.getDirection())
                    .setStatus(instance == null ? RptInstanceStatus.EMPTY : instance.getStatus())
                    .setUpdatedAt(instance == null ? null : instance.getUpdatedAt()));
        }
        return result;
    }

    @Override
    public ReportDownloadDto downloadInstanceFile(Long instanceId, String actor) {
        // Load instance và metadata file
        RptInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Instance không tồn tại: id=" + instanceId));

        if (instance.getCurrentFileId() == null) {
            throw new ResourceNotFoundException("Instance chưa có file hiện tại: id=" + instanceId);
        }
        RptFile rptFile = fileRepository.findById(instance.getCurrentFileId())
                .filter(file -> instanceId.equals(file.getReportInstanceId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "File hiện tại không tồn tại cho instance: id=" + instanceId));

        return toDownloadDto(instance, rptFile, actor);
    }

    @Override
    public List<ReportFileVersionResponse> listFileVersions(Long instanceId) {
        RptInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Instance không tồn tại: id=" + instanceId));
        List<ReportFileVersionResponse> result = new ArrayList<>();
        for (RptFile file : fileRepository.findByReportInstanceIdOrderByVersionNoDesc(instanceId)) {
            result.add(new ReportFileVersionResponse()
                    .setFileId(file.getId())
                    .setVersionNo(file.getVersionNo())
                    .setOriginalFilename(file.getOriginalFilename())
                    .setFileSize(file.getFileSize())
                    .setSha256(file.getSha256())
                    .setCreatedAt(file.getCreatedAt())
                    .setCreatedBy(file.getCreatedBy())
                    .setCurrent(file.getId().equals(instance.getCurrentFileId())));
        }
        return result;
    }

    @Override
    public ReportDownloadDto downloadFileVersion(Long fileId, String actor) {
        RptFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File không tồn tại: id=" + fileId));
        RptInstance instance = instanceRepository.findById(file.getReportInstanceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Instance của file không tồn tại: id=" + file.getReportInstanceId()));
        return toDownloadDto(instance, file, actor);
    }

    @Override
    public Page<ReportActionHistoryResponse> searchActionHistory(LocalDate fromDate, LocalDate toDate,
            String reportName, List<RptAuditAction> actions,
            int page, int size) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate không được lớn hơn toDate");
        }
        if (page < 1) {
            throw new IllegalArgumentException("page phải lớn hơn hoặc bằng 1");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size phải lớn hơn hoặc bằng 1");
        }

        String normalizedReportName = reportName == null ? null : reportName.trim();
        if (normalizedReportName != null && normalizedReportName.isEmpty()) {
            normalizedReportName = null;
        }

        LocalDateTime fromCreatedAt = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime toCreatedAtExclusive = toDate == null ? null : toDate.plusDays(1).atStartOfDay();

        Pageable pageable = PageRequest.of(page - 1, size);
        Page<ReportActionHistoryProjection> historyPage;
        if (actions == null || actions.isEmpty()) {
            historyPage = auditLogRepository.searchActionHistory(
                    fromCreatedAt, toCreatedAtExclusive, normalizedReportName, pageable);
        } else {
            historyPage = auditLogRepository.searchActionHistoryByActions(
                    fromCreatedAt, toCreatedAtExclusive, normalizedReportName, actions, pageable);
        }

        return historyPage.map(this::toActionHistoryResponse);
    }

    private ReportActionHistoryResponse toActionHistoryResponse(ReportActionHistoryProjection history) {
        LocalDate weekStartDate = history.getWeekStartDate();
        return new ReportActionHistoryResponse()
                .setDefinitionId(history.getDefinitionId())
                .setInstanceId(history.getInstanceId())
                .setDefinitionName(history.getDefinitionName())
                .setWeekStartDate(weekStartDate)
                .setWeekEndDate(weekStartDate.plusDays(6))
                .setAction(history.getAction())
                .setActor(history.getActor())
                .setCreatedAt(history.getCreatedAt())
                .setReason(history.getReason())
                .setFileId(history.getFileId())
                .setFileName(history.getFileName());
    }

    private ReportDownloadDto toDownloadDto(RptInstance instance, RptFile rptFile, String actor) {
        Resource resource = reportFileService.resolveForDownload(rptFile.getStorageObjectName());
        return new ReportDownloadDto(resource, rptFile.getOriginalFilename(),
                determineContentType(rptFile.getOriginalFilename()));
    }

    private String determineContentType(String filename) {
        if (filename == null) {
            return org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (lower.endsWith(".pdf")) {
            return org.springframework.http.MediaType.APPLICATION_PDF_VALUE;
        }
        return org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private Map<Long, RptDefinition> loadDefinitions(List<RptDefinitionLink> links) {
        List<Long> ids = new ArrayList<>();
        for (RptDefinitionLink link : links)
            if (!ids.contains(link.getChildDefinitionId()))
                ids.add(link.getChildDefinitionId());
        Map<Long, RptDefinition> result = new HashMap<>();
        for (RptDefinition definition : definitionRepository.findAllById(ids))
            result.put(definition.getId(), definition);
        return result;
    }

    private ReportData loadReportData(LocalDate weekStartDate) {
        Map<Long, RptInstance> instances = new HashMap<>();
        List<Long> instanceIds = new ArrayList<>();
        for (RptInstance instance : instanceRepository.findByWeekStartDate(weekStartDate)) {
            instances.put(instance.getDefinitionId(), instance);
            instanceIds.add(instance.getId());
        }
        Map<Long, RptFile> files = new HashMap<>();
        List<Long> currentFileIds = new ArrayList<>();
        for (RptInstance instance : instances.values()) {
            if (instance.getCurrentFileId() != null)
                currentFileIds.add(instance.getCurrentFileId());
        }
        if (!currentFileIds.isEmpty()) {
            for (RptFile file : fileRepository.findAllById(currentFileIds))
                files.put(file.getId(), file);
        }
        return new ReportData(instances, files);
    }

    private Map<Long, Long> buildParentMap(List<RptDefinitionLink> links) {
        Map<Long, Long> result = new HashMap<>();
        for (RptDefinitionLink link : links) {
            Long existingParentId = result.putIfAbsent(
                    link.getChildDefinitionId(), link.getParentDefinitionId());
            if (existingParentId != null && !existingParentId.equals(link.getParentDefinitionId())) {
                throw new IllegalStateException(
                        "File input thuộc nhiều group active: definitionId=" + link.getChildDefinitionId());
            }
        }
        return result;
    }

    private ReportInputNodeResponse toInputFileNode(Long parentDefinitionId,
            RptDefinition definition,
            ReportData data) {
        RptInstance instance = data.instances.get(definition.getId());
        RptFile file = currentFile(instance, data.files);
        RptInstanceStatus status = instance == null ? RptInstanceStatus.EMPTY : instance.getStatus();
        return new ReportInputNodeResponse()
                .setDefinitionId(definition.getId())
                .setParentDefinitionId(parentDefinitionId)
                .setNodeType(RptNodeType.FILE)
                .setReportName(definition.getName())
                .setFileKind(definition.getFileKind())
                .setAllowedExtension(definition.getAllowedExtension())
                .setInstanceId(instance == null ? null : instance.getId())
                .setStatus(status)
                .setRejected(status == RptInstanceStatus.REJECTED)
                .setImportedFileName(file == null ? null : file.getOriginalFilename())
                .setUpdatedBy(instance == null ? null : instance.getUpdatedBy())
                .setUpdatedAt(instance == null ? null : instance.getUpdatedAt());
    }

    private ReportInputNodeResponse toInputGroupNode(RptDefinition group,
            List<ReportInputNodeResponse> children,
            ReportData data) {
        int completedFiles = 0;
        for (ReportInputNodeResponse child : children) {
            if (child.getStatus() == RptInstanceStatus.COMPLETED)
                completedFiles++;
        }
        RptInstance latestInstance = findLatestInstance(children, data);
        return new ReportInputNodeResponse()
                .setDefinitionId(group.getId())
                .setParentDefinitionId(null)
                .setNodeType(RptNodeType.GROUP)
                .setReportName(group.getName())
                .setStatus(statusService.calculateGroupStatus(children))
                .setCompletedFiles(completedFiles)
                .setTotalFiles(children.size())
                .setUpdatedBy(latestInstance == null ? null : latestInstance.getUpdatedBy())
                .setUpdatedAt(latestInstance == null ? null : latestInstance.getUpdatedAt())
                .setChildren(children);
    }

    private boolean matchesInputFilters(ReportInputNodeResponse file,
            RptDefinition parentGroup,
            String keyword,
            List<RptInstanceStatus> statuses) {
        if (keyword != null) {
            boolean fileNameMatches = containsNormalizedText(file.getReportName(), keyword);
            boolean groupNameMatches = parentGroup != null
                    && containsNormalizedText(parentGroup.getName(), keyword);
            boolean updatedByMatches = containsNormalizedText(file.getUpdatedBy(), keyword);
            if (!fileNameMatches && !groupNameMatches && !updatedByMatches)
                return false;
        }
        if (statuses != null && !statuses.isEmpty() && !statuses.contains(file.getStatus()))
            return false;
        return true;
    }

    private String normalizeSearchText(String value) {
        if (value == null)
            return null;

        String normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean containsNormalizedText(String value, String normalizedSearchText) {
        String normalizedValue = normalizeSearchText(value);
        return normalizedValue != null && normalizedValue.contains(normalizedSearchText);
    }

    private RptDefinition findDefinition(Long definitionId,
            List<RptDefinition> groups,
            List<RptDefinition> inputFiles) {
        for (RptDefinition group : groups) {
            if (group.getId().equals(definitionId))
                return group;
        }
        for (RptDefinition inputFile : inputFiles) {
            if (inputFile.getId().equals(definitionId))
                return inputFile;
        }
        throw new IllegalStateException("Definition không tồn tại: id=" + definitionId);
    }

    private RptFile currentFile(RptInstance instance, Map<Long, RptFile> files) {
        if (instance == null || instance.getCurrentFileId() == null)
            return null;
        return files.get(instance.getCurrentFileId());
    }

    private RptInstance findLatestInstance(List<ReportInputNodeResponse> files, ReportData data) {
        RptInstance latestInstance = null;
        for (ReportInputNodeResponse file : files) {
            RptInstance instance = data.instances.get(file.getDefinitionId());
            if (instance == null || instance.getUpdatedAt() == null)
                continue;
            if (latestInstance == null || latestInstance.getUpdatedAt() == null
                    || instance.getUpdatedAt().isAfter(latestInstance.getUpdatedAt())) {
                latestInstance = instance;
            }
        }
        return latestInstance;
    }

    private static class ReportData {
        private final Map<Long, RptInstance> instances;
        private final Map<Long, RptFile> files;

        private ReportData(Map<Long, RptInstance> instances, Map<Long, RptFile> files) {
            this.instances = instances;
            this.files = files;
        }
    }
}
