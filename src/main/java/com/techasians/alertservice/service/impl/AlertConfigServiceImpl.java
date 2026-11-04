package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.dto.AlertConfigRequest;
import com.techasians.alertservice.dto.AlertConfigResponse;
import com.techasians.alertservice.dto.AlertConfigDetailResponse;
import com.techasians.alertservice.dto.AlertConfigSearchRequest;
import com.techasians.alertservice.dto.AlertLevelResponse;
import com.techasians.alertservice.dto.AlertLevelRequest;
import com.techasians.alertservice.dto.AlertGroupSumaryResponse;
import com.techasians.alertservice.dto.ImportAlertConfigRowDTO;
import com.techasians.alertservice.entity.AlertConfig;
import com.techasians.alertservice.entity.AlertGroup;
import com.techasians.alertservice.entity.AlertLevel;
import com.techasians.alertservice.entity.AlertLevelChannel;
import com.techasians.alertservice.entity.AlertLevelRecipient;
import com.techasians.alertservice.entity.NotificationChannel;
import com.techasians.alertservice.entity.ServiceQualityCategory;
import com.techasians.alertservice.entity.ServiceQualityMetric;
import com.techasians.alertservice.exception.DuplicateResourceException;
import com.techasians.alertservice.exception.ResourceNotFoundException;
import com.techasians.alertservice.repository.AlertConfigRepository;
import com.techasians.alertservice.repository.AlertLevelChannelRepository;
import com.techasians.alertservice.repository.AlertLevelRecipientRepository;
import com.techasians.alertservice.repository.AlertLevelRepository;
import com.techasians.alertservice.repository.AlertGroupRepository;
import com.techasians.alertservice.repository.NotificationChannelRepository;
import com.techasians.alertservice.repository.ServiceQualityCategoryRepository;
import com.techasians.alertservice.repository.ServiceQualityMetricRepository;
import com.techasians.alertservice.service.AlertConfigHistoryService;
import com.techasians.alertservice.service.AlertConfigService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AlertConfigServiceImpl implements AlertConfigService {

    private final AlertConfigRepository alertConfigRepository;
    private final AlertLevelRepository alertLevelRepository;
    private final AlertLevelChannelRepository alertLevelChannelRepository;
    private final AlertLevelRecipientRepository alertLevelRecipientRepository;
    private final ServiceQualityCategoryRepository categoryRepository;
    private final ServiceQualityMetricRepository metricRepository;
    private final NotificationChannelRepository notificationChannelRepository;
    private final AlertGroupRepository alertGroupRepository;
    private final AlertConfigHistoryService alertConfigHistoryService;

    // Helper tách riêng để @Transactional(REQUIRES_NEW) hoạt động đúng
    // (self-invocation trong cùng class sẽ không qua Spring proxy)
    private final AlertConfigImportHelper importHelper;

    // Constructor Injection: Spring tự động đưa các Repository vào khi khởi chạy
    public AlertConfigServiceImpl(
            AlertConfigRepository alertConfigRepository,
            AlertLevelRepository alertLevelRepository,
            AlertLevelChannelRepository alertLevelChannelRepository,
            AlertLevelRecipientRepository alertLevelRecipientRepository,
            ServiceQualityCategoryRepository categoryRepository,
            ServiceQualityMetricRepository metricRepository,
            NotificationChannelRepository notificationChannelRepository,
            AlertGroupRepository alertGroupRepository,
            AlertConfigHistoryService alertConfigHistoryService,
            AlertConfigImportHelper importHelper) {
        this.alertConfigRepository = alertConfigRepository;
        this.alertLevelRepository = alertLevelRepository;
        this.alertLevelChannelRepository = alertLevelChannelRepository;
        this.alertLevelRecipientRepository = alertLevelRecipientRepository;
        this.categoryRepository = categoryRepository;
        this.metricRepository = metricRepository;
        this.notificationChannelRepository = notificationChannelRepository;
        this.alertGroupRepository = alertGroupRepository;
        this.alertConfigHistoryService = alertConfigHistoryService;
        this.importHelper = importHelper;
    }

    @Override
    public Page<AlertConfigResponse> search(AlertConfigSearchRequest searchRequest) {
        // Làm sạch và xử lý ký tự đặc biệt cho câu truy vấn LIKE (ESCAPE '!')
        String metricName = escapeSqlWildcards(searchRequest.getMetricName());
        String createdBy = escapeSqlWildcards(searchRequest.getCreatedBy());

        Sort sort = Sort.unsorted();
        if (StringUtils.hasText(searchRequest.getSort())) {
            String[] arr = searchRequest.getSort().split(",");
            if (arr.length == 2) {
                sort = Sort.by(Sort.Direction.fromString(arr[1].trim()), arr[0].trim());
            } else {
                sort = Sort.by(arr[0].trim());
            }
        }

        // Chuẩn bị thông tin phân trang
        Pageable pageable = PageRequest.of(searchRequest.getPage(), searchRequest.getSize(), sort);

        // Gọi Repository để tìm kiếm, kết quả trả về là Page<AlertConfig>
        Page<AlertConfig> configPage = alertConfigRepository.search(
                searchRequest.getCategoryId(),
                metricName,
                createdBy,
                pageable);

        // Chuyển đổi từ Entity sang Response DTO trước khi trả về
        return configPage.map(this::mapToResponse);
    }

    @Override
    @Transactional // Bắt buộc: đảm bảo nếu có lỗi ở bước nào thì toàn bộ sẽ bị Rollback
    public Integer create(AlertConfigRequest request, String createdBy) {
        // Tối ưu (Fail-Fast): Validate dữ liệu thuần túy (VD: mức cảnh báo) trước khi
        // chạm vào CSDL
        if (request.getLevels() != null && !request.getLevels().isEmpty()) {
            validateLevelsInput(request.getLevels());
        }

        // Kiểm tra xem Cấu hình cho Category và Metric này đã tồn tại chưa
        if (alertConfigRepository.existsByServiceQualityCategoryIdAndServiceQualityMetricIdAndDeletedFalse(
                request.getCategoryId(), request.getMetricId())) {
            throw new DuplicateResourceException(
                    "Cấu hình cảnh báo cho Loại chỉ số và Chỉ số chi tiết này đã tồn tại.");
        }

        // Bước 1: Kiểm tra Category và Metric có hợp lệ không
        ServiceQualityCategory category = findCategoryById(request.getCategoryId());
        ServiceQualityMetric metric = findMetricById(request.getMetricId());

        // Bước 2: Tạo đối tượng AlertConfig và lưu xuống DB
        AlertConfig alertConfig = new AlertConfig()
                .setName(request.getName())
                .setServiceQualityCategory(category)
                .setServiceQualityMetric(metric)
                .setStatus(request.getStatus() != null ? request.getStatus() : 1)
                .setCreatedBy(createdBy)
                .setUpdatedBy(createdBy);

        AlertConfig savedConfig = alertConfigRepository.save(alertConfig);

        // Bước 3: Xử lý danh sách các mức cảnh báo (levels)
        if (request.getLevels() != null && !request.getLevels().isEmpty()) {
            saveLevels(savedConfig, request.getLevels(), createdBy);
        }

        // Bước 4: Lưu lịch sử (Tạo mới)
        AlertConfigDetailResponse afterDetail = getDetail(savedConfig.getId());
        alertConfigHistoryService.saveLog(savedConfig.getId(), 1, null, afterDetail, createdBy);

        return savedConfig.getId();
    }

    @Override
    @Transactional
    public Integer update(Integer id, AlertConfigRequest request, String updatedBy) {
        // Tối ưu (Fail-Fast): Validate dữ liệu thuần túy (VD: mức cảnh báo) trước khi
        // chạm vào CSDL
        if (request.getLevels() != null && !request.getLevels().isEmpty()) {
            validateLevelsInput(request.getLevels());
        }

        // Bước 1: Tìm AlertConfig cần sửa, nếu không tìm thấy thì báo lỗi
        AlertConfig alertConfig = findAlertConfigById(id);

        // Lấy snapshot trước khi cập nhật
        AlertConfigDetailResponse beforeDetail = mapToDetailResponse(alertConfig);

        // Kiểm tra xem việc đổi Category/Metric có bị trùng với cấu hình khác đã tồn
        // tại không
        boolean isCategoryChanged = !alertConfig.getServiceQualityCategory().getId().equals(request.getCategoryId());
        boolean isMetricChanged = !alertConfig.getServiceQualityMetric().getId().equals(request.getMetricId());

        if (isCategoryChanged || isMetricChanged) {
            if (alertConfigRepository.existsByServiceQualityCategoryIdAndServiceQualityMetricIdAndDeletedFalse(
                    request.getCategoryId(), request.getMetricId())) {
                throw new DuplicateResourceException(
                        "Cấu hình cảnh báo cho Loại chỉ số và Chỉ số chi tiết này đã tồn tại.");
            }
        }

        if (isCategoryChanged) {
            ServiceQualityCategory category = findCategoryById(request.getCategoryId());
            alertConfig.setServiceQualityCategory(category);
        }

        if (isMetricChanged) {
            ServiceQualityMetric metric = findMetricById(request.getMetricId());
            alertConfig.setServiceQualityMetric(metric);
        }

        // Bước 3: Cập nhật thông tin cơ bản của AlertConfig
        alertConfig.setName(request.getName())
                .setStatus(request.getStatus())
                .setUpdatedBy(updatedBy);

        alertConfigRepository.save(alertConfig);

        // Bước 4: Áp dụng cơ chế "Xóa và Chèn Lại" (Clear and Rebuild) cho các Levels
        // 4a. Lấy danh sách các mức cũ từ DB
        List<AlertLevel> oldLevels = alertLevelRepository.findByAlertConfigId(id);

        // 4b. Với mỗi mức cũ, xóa hết channel và recipient trước
        for (AlertLevel oldLevel : oldLevels) {
            alertLevelChannelRepository.deleteByAlertLevelId(oldLevel.getId());
            alertLevelRecipientRepository.deleteByAlertLevelId(oldLevel.getId());
        }

        // 4c. Xóa toàn bộ các mức cũ
        alertLevelRepository.deleteByAlertConfigId(id);

        // 4d. Tạo lại các mức mới từ request
        if (request.getLevels() != null && !request.getLevels().isEmpty()) {
            saveLevels(alertConfig, request.getLevels(), updatedBy);
        }

        // Bước 5: Lưu lịch sử (Cập nhật, Kích hoạt, hoặc Vô hiệu hóa)
        AlertConfigDetailResponse afterDetail = getDetail(alertConfig.getId());

        int actionType = 2; // Cập nhật mặc định
        if (!Objects.equals(beforeDetail.getStatus(), afterDetail.getStatus())) {
            actionType = afterDetail.getStatus() != null && afterDetail.getStatus() == 1 ? 4 : 5;
        }

        alertConfigHistoryService.saveLog(alertConfig.getId(), actionType, beforeDetail, afterDetail, updatedBy);

        return alertConfig.getId();
    }

    @Override
    @Transactional
    public void delete(Integer id, String deletedBy) {
        // Bước 1: Tìm AlertConfig cần xóa
        AlertConfig alertConfig = findAlertConfigById(id);

        // Lấy snapshot trước khi xóa
        AlertConfigDetailResponse beforeDetail = mapToDetailResponse(alertConfig);

        // Bước 2: Chỉ bật cờ deleted = true (xóa mềm), không đụng đến các bảng con
        alertConfig.setDeleted(true)
                .setUpdatedBy(deletedBy);

        alertConfigRepository.save(alertConfig);

        // Bước 3: Lưu lịch sử (Xóa mềm)
        alertConfigHistoryService.saveLog(alertConfig.getId(), 3, beforeDetail, null, deletedBy);
    }

    @Override
    public AlertConfigDetailResponse getDetail(Integer id) {
        // Bước 1: Tìm AlertConfig theo ID
        AlertConfig alertConfig = findAlertConfigById(id);

        // Bước 2: Chuyển đổi sang AlertConfigDetailResponse
        return mapToDetailResponse(alertConfig);
    }

    // =====================================================================
    // Các phương thức phụ trợ (Private Helper Methods)

    /**
     * Xử lý chuỗi đầu vào để an toàn khi dùng trong câu SQL LIKE (với ESCAPE '!').
     * Thay thế các ký tự '%', '_' và '!' thành dạng literal.
     */
    private String escapeSqlWildcards(String input) {
        if (input == null) {
            return null;
        }
        return input.trim()
                .replace("!", "!!") // Phải escape chính ký tự '!' trước
                .replace("%", "!%")
                .replace("_", "!_");
    }

    /**
     * Validate dữ liệu thuần túy của mức cảnh báo (levels).
     * Bắn ra Exception nếu có lỗi, giúp hệ thống fail-fast trước khi truy vấn CSDL.
     */
    private void validateLevelsInput(List<AlertLevelRequest> levelRequests) {
        // Validate duplicate levelNumber trong chính request gửi lên
        Set<Integer> seenLevelNumbers = new HashSet<>();
        for (AlertLevelRequest levelRequest : levelRequests) {
            if (levelRequest.getLevelNumber() != null
                    && !seenLevelNumbers.add(levelRequest.getLevelNumber())) {
                throw new IllegalArgumentException(
                        "Danh sách mức cảnh báo có levelNumber bị trùng: " + levelRequest.getLevelNumber());
            }
        }

        // Validate từng level: conditionOperator, thresholdValue
        for (AlertLevelRequest levelRequest : levelRequests) {
            if (levelRequest.getConditionOperator() != null && levelRequest.getConditionOperator() == 5) {
                if (levelRequest.getThresholdValueTo() == null) {
                    throw new IllegalArgumentException(
                            "Phép so sánh 'Trong khoảng' yêu cầu phải nhập giá trị ngưỡng thứ hai (thresholdValueTo)");
                }
                if (levelRequest.getThresholdValue() != null
                        && levelRequest.getThresholdValue().compareTo(levelRequest.getThresholdValueTo()) >= 0) {
                    throw new IllegalArgumentException("Giá trị ngưỡng 'Đến' phải lớn hơn giá trị ngưỡng 'Từ'");
                }
            } else {
                // Nếu phép so sánh không phải 'Trong khoảng', ép giá trị thứ 2 về null để tránh
                // rác CSDL
                levelRequest.setThresholdValueTo(null);
            }
        }
    }

    /**
     * Lưu danh sách các mức cảnh báo vào DB, bao gồm cả Channel và Recipient
     * của từng mức.
     */
    // Package-private (bỏ từ khóa private) để AlertConfigImportHelper trong cùng
    // package gọi được
    void saveLevels(AlertConfig alertConfig, List<AlertLevelRequest> levelRequests, String createdBy) {

        // ----------------------------------------------------------------
        // Bước 1: Gom tất cả ID, query Channel + Group mỗi loại 1 lần duy nhất
        // ----------------------------------------------------------------
        List<Integer> allChannelIds = levelRequests.stream()
                .filter(lr -> lr.getChannelIds() != null && !lr.getChannelIds().isEmpty())
                .flatMap(lr -> lr.getChannelIds().stream())
                .distinct()
                .collect(Collectors.toList());

        List<Integer> allGroupIds = levelRequests.stream()
                .filter(lr -> lr.getGroupIds() != null && !lr.getGroupIds().isEmpty())
                .flatMap(lr -> lr.getGroupIds().stream())
                .distinct()
                .collect(Collectors.toList());

        Map<Integer, NotificationChannel> channelMap = notificationChannelRepository
                .findAllById(allChannelIds)
                .stream()
                .collect(Collectors.toMap(NotificationChannel::getId, channel -> channel));

        Map<Integer, AlertGroup> groupMap = alertGroupRepository
                .findAllById(allGroupIds)
                .stream()
                .collect(Collectors.toMap(AlertGroup::getId, g -> g));

        // Validate tất cả ID có tồn tại trong CSDL không — fail sớm trước khi ghi
        if (!channelMap.keySet().containsAll(allChannelIds)) {
            throw new ResourceNotFoundException("Một hoặc nhiều Kênh nhận cảnh báo không tồn tại");
        }
        if (!groupMap.keySet().containsAll(allGroupIds)) {
            throw new ResourceNotFoundException("Một hoặc nhiều Nhóm đầu mối nhận không tồn tại");
        }

        // ----------------------------------------------------------------
        // Bước 3: Tạo List<AlertLevel> và saveAll 1 lần
        // ----------------------------------------------------------------
        List<AlertLevel> alertLevels = new ArrayList<>();
        for (AlertLevelRequest levelRequest : levelRequests) {
            AlertLevel alertLevel = new AlertLevel()
                    .setAlertConfig(alertConfig)
                    .setLevelNumber(levelRequest.getLevelNumber())
                    .setConditionOperator(levelRequest.getConditionOperator())
                    .setThresholdValue(levelRequest.getThresholdValue())
                    .setThresholdValueTo(levelRequest.getThresholdValueTo())
                    .setStatus(1)
                    .setCreatedBy(createdBy)
                    .setUpdatedBy(createdBy);
            alertLevels.add(alertLevel);
        }
        List<AlertLevel> savedLevels = alertLevelRepository.saveAll(alertLevels);

        // ----------------------------------------------------------------
        // Bước 4: Sinh List<AlertLevelChannel> và saveAll 1 lần
        // ----------------------------------------------------------------
        List<AlertLevelChannel> allAlertLevelChannels = new ArrayList<>();
        for (int i = 0; i < savedLevels.size(); i++) {
            AlertLevel savedLevel = savedLevels.get(i);
            AlertLevelRequest levelRequest = levelRequests.get(i);

            if (levelRequest.getChannelIds() != null && !levelRequest.getChannelIds().isEmpty()) {
                for (Integer channelId : levelRequest.getChannelIds()) {
                    AlertLevelChannel alertLevelChannel = new AlertLevelChannel()
                            .setAlertLevel(savedLevel)
                            .setNotificationChannel(channelMap.get(channelId))
                            .setStatus(1)
                            .setCreatedBy(createdBy)
                            .setUpdatedBy(createdBy);
                    allAlertLevelChannels.add(alertLevelChannel);
                }
            }
        }
        if (!allAlertLevelChannels.isEmpty()) {
            alertLevelChannelRepository.saveAll(allAlertLevelChannels);
        }

        // ----------------------------------------------------------------
        // Bước 5: Sinh List<AlertLevelRecipient> và saveAll 1 lần
        // ----------------------------------------------------------------
        List<AlertLevelRecipient> allAlertLevelRecipients = new ArrayList<>();
        for (int i = 0; i < savedLevels.size(); i++) {
            AlertLevel savedLevel = savedLevels.get(i);
            AlertLevelRequest levelRequest = levelRequests.get(i);

            if (levelRequest.getGroupIds() != null && !levelRequest.getGroupIds().isEmpty()) {
                for (Integer groupId : levelRequest.getGroupIds()) {
                    AlertLevelRecipient recipient = new AlertLevelRecipient()
                            .setAlertLevel(savedLevel)
                            .setAlertGroup(groupMap.get(groupId.longValue()))
                            .setCreatedBy(createdBy)
                            .setUpdatedBy(createdBy);
                    allAlertLevelRecipients.add(recipient);
                }
            }
        }
        if (!allAlertLevelRecipients.isEmpty()) {
            alertLevelRecipientRepository.saveAll(allAlertLevelRecipients);
        }
    }

    /**
     * Tìm AlertConfig theo ID, đảm bảo bản ghi chưa bị xóa mềm.
     * Ném ra lỗi ResourceNotFoundException nếu không tìm thấy.
     */
    private AlertConfig findAlertConfigById(Integer id) {
        return alertConfigRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy cấu hình cảnh báo với ID: " + id));
    }

    /**
     * Tìm ServiceQualityCategory theo ID.
     * Kiểm tra Database xem ID có tồn tại thật sự không, ném ra lỗi nếu không tìm
     * thấy.
     */
    private ServiceQualityCategory findCategoryById(Integer categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy Loại chỉ số với ID: " + categoryId));
    }

    /**
     * Tìm ServiceQualityMetric theo ID.
     * Kiểm tra Database xem ID có tồn tại thật sự không, ném ra lỗi nếu không tìm
     * thấy.
     */
    private ServiceQualityMetric findMetricById(Integer metricId) {
        return metricRepository.findById(metricId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy Chỉ số chi tiết với ID: " + metricId));
    }

    /**
     * Chuyển đổi AlertConfig Entity sang AlertConfigResponse DTO để trả về cho
     * Frontend.
     */
    private AlertConfigResponse mapToResponse(AlertConfig entity) {
        AlertConfigResponse response = new AlertConfigResponse()
                .setId(entity.getId())
                .setName(entity.getName())
                .setStatus(entity.getStatus())
                .setCreatedBy(entity.getCreatedBy());

        // Lấy thông tin category nếu đã được load
        if (entity.getServiceQualityCategory() != null) {
            response.setCategoryId(entity.getServiceQualityCategory().getId());
            response.setCategoryName(entity.getServiceQualityCategory().getName());
        }

        // Lấy thông tin metric nếu đã được load
        if (entity.getServiceQualityMetric() != null) {
            response.setMetricId(entity.getServiceQualityMetric().getId());
            response.setMetricName(entity.getServiceQualityMetric().getName());
            response.setType(entity.getServiceQualityMetric().getType());
        }

        return response;
    }

    /**
     * Chuyển đổi AlertConfig Entity sang AlertConfigDetailResponse DTO (bao gồm
     * levels).
     */
    private AlertConfigDetailResponse mapToDetailResponse(AlertConfig entity) {
        AlertConfigDetailResponse response = new AlertConfigDetailResponse()
                .setId(entity.getId())
                .setName(entity.getName())
                .setStatus(entity.getStatus());

        // Lấy thông tin category nếu đã được load
        if (entity.getServiceQualityCategory() != null) {
            response.setCategoryId(entity.getServiceQualityCategory().getId());
            response.setCategoryName(entity.getServiceQualityCategory().getName());
        }

        // Lấy thông tin metric nếu đã được load
        if (entity.getServiceQualityMetric() != null) {
            response.setMetricId(entity.getServiceQualityMetric().getId());
            response.setMetricName(entity.getServiceQualityMetric().getName());
        }

        // Lấy danh sách các mức cảnh báo từ DB
        List<AlertLevel> levels = alertLevelRepository.findByAlertConfigId(entity.getId());
        List<AlertLevelResponse> levelResponses = new ArrayList<>();

        if (levels.isEmpty()) {
            response.setLevels(levelResponses);
            return response;
        }

        // Tạo mảng ID của tất cả các mức để truy vấn 1 lần (Tránh N+1 Query)
        List<Integer> levelIds = levels.stream().map(AlertLevel::getId).collect(Collectors.toList());

        // Truy vấn tất cả kênh nhận và đầu mối nhận của TẤT CẢ các mức
        List<AlertLevelChannel> allChannels = alertLevelChannelRepository.findByAlertLevelIdIn(levelIds);
        List<AlertLevelRecipient> allRecipients = alertLevelRecipientRepository.findByAlertLevelIdIn(levelIds);

        // Group (Nhóm) lại bằng bộ nhớ RAM thay vì query CSDL nhiều lần
        Map<Integer, List<Integer>> channelIdsByLevel = allChannels.stream()
                .collect(Collectors.groupingBy(
                        ch -> ch.getAlertLevel().getId(),
                        Collectors.mapping(ch -> ch.getNotificationChannel().getId(), Collectors.toList())));

        Map<Integer, List<AlertGroupSumaryResponse>> groupsByLevel = allRecipients.stream()
                .collect(Collectors.groupingBy(
                        rp -> rp.getAlertLevel().getId(),
                        Collectors.mapping(rp -> new AlertGroupSumaryResponse()
                                .setGroupId(rp.getAlertGroup().getId().intValue())
                                .setGroupCode(rp.getAlertGroup().getCode()), Collectors.toList())));

        for (AlertLevel level : levels) {
            // Lấy dữ liệu từ Map đã nhóm ở trên
            List<Integer> channelIds = channelIdsByLevel.getOrDefault(level.getId(), new ArrayList<>());
            List<AlertGroupSumaryResponse> groups = groupsByLevel.getOrDefault(level.getId(), new ArrayList<>());

            AlertLevelResponse levelResponse = new AlertLevelResponse()
                    .setId(level.getId())
                    .setLevelNumber(level.getLevelNumber())
                    .setConditionOperator(level.getConditionOperator())
                    .setThresholdValue(level.getThresholdValue())
                    .setThresholdValueTo(level.getThresholdValueTo())
                    .setStatus(level.getStatus())
                    .setChannelIds(channelIds)
                    .setGroups(groups);

            levelResponses.add(levelResponse);
        }

        response.setLevels(levelResponses);
        return response;
    }

    // =====================================================================
    // PHẦN IMPORT FILE EXCEL
    // =====================================================================

    /**
     * Tên các cột header trong file Excel template import.
     * Dùng để validate định dạng file khi người dùng upload sai template.
     */
    private static final List<String> EXPECTED_HEADERS = Arrays.asList(
            "Loại chỉ số",
            "Tên chỉ số",
            "Mức độ cảnh báo",
            "Phép so sánh",
            "Giá trị ngưỡng (V1/Min)",
            "Giá trị ngưỡng (V2/Max)",
            "Nhận SMS (Y/N)",
            "Nhận Email (Y/N)",
            "Đầu mối nhận cảnh báo");

    @Override
    public byte[] importFromExcel(MultipartFile file, String createdBy) {
        // Bước 1: Kiểm tra extension file — phải là .xlsx
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".xlsx")) {
            throw new IllegalArgumentException(
                    "File import không đúng định dạng. Vui lòng kiểm tra và thử lại.");
        }

        // Bước 2-9: Đọc file và xử lý — dùng try-with-resources để đảm bảo đóng
        // Workbook đọc vào
        // Lưu ý: buildErrorExcelFile() phải được gọi SAU khi try-with-resources kết
        // thúc
        // vì nó tự tạo Workbook mới và ném IOException riêng — không liên quan đến file
        // đầu vào
        List<ImportAlertConfigRowDTO> rows = readAndProcessExcel(file, createdBy);

        // Bước 10: Nếu có dòng lỗi → tạo file Excel kết quả và trả về
        // buildErrorExcelFile() được gọi Ở ĐÂY, ngoài try-with-resources đọc file đầu
        // vào
        // để IOException từ bước tạo file lỗi không bị nhầm với lỗi đọc file đầu vào
        boolean hasAnyError = rows.stream().anyMatch(ImportAlertConfigRowDTO::hasError);
        if (hasAnyError) {
            try {
                return buildErrorExcelFile(rows, file);
            } catch (IOException ex) {
                throw new RuntimeException("Không thể tạo file kết quả lỗi: " + ex.getMessage(), ex);
            }
        }

        // Tất cả thành công — trả về null
        return null;
    }

    /**
     * Bước nội bộ: đọc file Excel, validate, và lưu DB.
     * Trả về danh sách tất cả dòng đã được xử lý (kể cả dòng lỗi).
     *
     * Tách riêng method này để try-with-resources (đóng Workbook đọc vào)
     * không ảnh hưởng đến IOException từ buildErrorExcelFile().
     */
    private List<ImportAlertConfigRowDTO> readAndProcessExcel(MultipartFile file, String createdBy) {
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            // Bước 3: Validate header (dòng đầu tiên phải khớp với template)
            validateHeader(sheet);

            // Bước 4: Đọc toàn bộ dòng dữ liệu từ Excel vào danh sách
            List<ImportAlertConfigRowDTO> rows = readAllRows(sheet);

            // Nếu file không có dữ liệu (chỉ có header), báo lỗi định dạng
            if (rows.isEmpty()) {
                throw new IllegalArgumentException(
                        "File import không đúng định dạng. Vui lòng kiểm tra và thử lại.");
            }

            // Bước 5: Pre-load toàn bộ dữ liệu tham chiếu từ DB vào bộ nhớ (1 lần duy nhất)
            // Mục đích: tránh query DB trong mỗi vòng lặp (N+1 query problem)
            Map<String, ServiceQualityCategory> categoriesByName = loadAllCategories();
            Map<String, NotificationChannel> channelsByName = loadAllChannels();
            Map<String, AlertGroup> groupsByCode = loadAllGroupsByCode(rows);
            Map<String, ServiceQualityMetric> metricsByKey = loadAllMetrics();
            Set<String> existingConfigKeys = loadAllActiveConfigKeys();
            // Map parentId → danh sách category con: dùng để hỗ trợ nhập tên dịch vụ cha
            Map<Integer, List<ServiceQualityCategory>> childCategoriesByParentId = loadChildCategoriesByParentId(categoriesByName);

            // Bước 6: Validate từng dòng — ghi lỗi vào errorMessage của từng row
            for (ImportAlertConfigRowDTO row : rows) {
                validateRow(row, categoriesByName, childCategoriesByParentId, groupsByCode, metricsByKey, existingConfigKeys);
            }

            // Bước 7: Phát hiện trùng lặp trong file
            // Cặp (metricName, levelNumber) phải là duy nhất trong toàn file
            detectDuplicatesInFile(rows);

            // Bước 8: Gom nhóm dòng hợp lệ theo tên chỉ số
            // Nhiều dòng cùng metricName → 1 AlertConfig + nhiều AlertLevel
            Map<String, List<ImportAlertConfigRowDTO>> groupedByMetric = groupValidRowsByMetric(rows);

            // Bước 9: Lưu từng nhóm vào DB thông qua importHelper
            // importHelper.saveImportGroup() có @Transactional(REQUIRES_NEW) — mỗi nhóm là
            // 1 transaction riêng
            for (Map.Entry<String, List<ImportAlertConfigRowDTO>> entry : groupedByMetric.entrySet()) {
                List<ImportAlertConfigRowDTO> groupRows = entry.getValue();
                try {
                    importHelper.saveImportGroup(groupRows, categoriesByName, metricsByKey, channelsByName,
                            groupsByCode, createdBy);
                    // Đánh dấu tất cả dòng trong nhóm là thành công (errorMessage = "" = thành
                    // công)
                    for (ImportAlertConfigRowDTO row : groupRows) {
                        row.setErrorMessage("");
                    }
                } catch (Exception ex) {
                    // Nếu nhóm này lỗi khi lưu DB → đánh dấu lỗi cho tất cả dòng trong nhóm
                    String dbError = "Lỗi khi lưu dữ liệu: " + ex.getMessage();
                    for (ImportAlertConfigRowDTO row : groupRows) {
                        if (!row.hasError()) {
                            row.setErrorMessage(dbError);
                        }
                    }
                }
            }

            return rows;

        } catch (IllegalArgumentException ex) {
            // Ném lại để controller bắt và trả về HTTP 400
            throw ex;
        } catch (IOException ex) {
            // Chỉ xảy ra khi ĐỌC file đầu vào bị lỗi (không phải khi tạo file kết quả)
            throw new IllegalArgumentException(
                    "File import không đúng định dạng. Vui lòng kiểm tra và thử lại.");
        }
    }

    /**
     * Validate dòng header của file Excel.
     * Phải có đúng 9 cột với tên khớp với template.
     * Nếu sai → ném IllegalArgumentException.
     */
    private void validateHeader(Sheet sheet) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw new IllegalArgumentException(
                    "File import không đúng định dạng. Vui lòng kiểm tra và thử lại.");
        }

        int expectedSize = EXPECTED_HEADERS.size();
        for (int i = 0; i < expectedSize; i++) {
            Cell cell = headerRow.getCell(i);
            String cellValue = getCellStringValue(cell).trim();
            if (!EXPECTED_HEADERS.get(i).equalsIgnoreCase(cellValue)) {
                throw new IllegalArgumentException(
                        "File import không đúng định dạng. Vui lòng kiểm tra và thử lại.");
            }
        }

        // Kiểm tra nếu có cột thừa (cột có chứa dữ liệu)
        int lastCellNum = headerRow.getLastCellNum();
        for (int i = expectedSize; i < lastCellNum; i++) {
            Cell cell = headerRow.getCell(i);
            String cellValue = getCellStringValue(cell).trim();
            if (!cellValue.isEmpty()) {
                throw new IllegalArgumentException(
                        "File import không đúng định dạng. Vui lòng kiểm tra và thử lại.");
            }
        }
    }

    /**
     * Đọc tất cả dòng dữ liệu (từ dòng thứ 2 trở đi) từ sheet Excel.
     * Bỏ qua các dòng hoàn toàn trống.
     */
    private List<ImportAlertConfigRowDTO> readAllRows(Sheet sheet) {
        List<ImportAlertConfigRowDTO> rows = new ArrayList<>();

        // Dòng 0 = header, dữ liệu bắt đầu từ dòng 1
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);

            // Bỏ qua dòng null hoặc dòng trống hoàn toàn
            if (row == null || isRowEmpty(row)) {
                continue;
            }

            // Đọc giá trị thô từng cột
            String categoryName = getCellStringValue(row.getCell(0)).trim();
            String metricName = getCellStringValue(row.getCell(1)).trim();
            String levelRaw = getCellStringValue(row.getCell(2)).trim();
            String operatorRaw = getCellStringValue(row.getCell(3)).trim();
            String v1Raw = getCellStringValue(row.getCell(4)).trim();
            String v2Raw = getCellStringValue(row.getCell(5)).trim();
            String smsRaw = getCellStringValue(row.getCell(6)).trim().toUpperCase();
            String emailRaw = getCellStringValue(row.getCell(7)).trim().toUpperCase();
            String groupCodesRaw = getCellStringValue(row.getCell(8)).trim();

            // Parse mức cảnh báo: "Mức 1" → 1, "Mức 2" → 2, "Mức 3" → 3
            Integer levelNumber = parseLevelNumber(levelRaw);

            // Parse phép so sánh: "<" → 1, "<=" → 2, ..."between" → 6
            Integer conditionOperator = parseConditionOperator(operatorRaw);

            // Parse giá trị ngưỡng V1 và V2
            BigDecimal thresholdValue = parseBigDecimal(v1Raw);
            BigDecimal thresholdValueTo = parseBigDecimal(v2Raw);

            // Parse nhận SMS/Email: "Y" → true, "N" → false
            Boolean receiveSms = parseYesNo(smsRaw);
            Boolean receiveEmail = parseYesNo(emailRaw);

            // Parse danh sách mã đầu mối: "VTT; VTT_SME" → ["VTT", "VTT_SME"]
            List<String> groupCodes = parseGroupCodes(groupCodesRaw);

            ImportAlertConfigRowDTO rowDTO = new ImportAlertConfigRowDTO()
                    .setRowNumber(rowIndex + 1) // +1 vì Excel đếm từ 1
                    .setCategoryName(categoryName)
                    .setMetricName(metricName)
                    .setLevelNumber(levelNumber)
                    .setConditionOperator(conditionOperator)
                    .setThresholdValue(thresholdValue)
                    .setThresholdValueTo(thresholdValueTo)
                    .setV1Raw(v1Raw)
                    .setV2Raw(v2Raw)
                    .setReceiveSms(receiveSms)
                    .setReceiveEmail(receiveEmail)
                    .setGroupCodes(groupCodes);

            rows.add(rowDTO);
        }

        return rows;
    }

    /**
     * Validate dữ liệu 1 dòng — ghi lỗi vào errorMessage nếu có vấn đề.
     * Một dòng chỉ có 1 thông báo lỗi đầu tiên gặp phải (dừng ngay khi tìm thấy
     * lỗi).
     */
    private void validateRow(
            ImportAlertConfigRowDTO row,
            Map<String, ServiceQualityCategory> categoriesByName,
            Map<Integer, List<ServiceQualityCategory>> childCategoriesByParentId,
            Map<String, AlertGroup> groupsByCode,
            Map<String, ServiceQualityMetric> metricsByKey,
            Set<String> existingConfigKeys) {

        List<String> errors = new ArrayList<>();

        // Validate cột A: Loại chỉ số
        ServiceQualityCategory category = null;
        if (row.getCategoryName().isEmpty()) {
            errors.add("Loại chỉ số không được để trống");
        } else {
            category = categoriesByName.get(row.getCategoryName().toLowerCase());
            if (category == null) {
                errors.add("Loại chỉ số '" + row.getCategoryName() + "' không tồn tại trong hệ thống");
            }
        }

        // Validate cột B: Tên chỉ số
        ServiceQualityMetric metric = null;
        if (row.getMetricName().isEmpty()) {
            errors.add("Tên chỉ số không được để trống");
        } else if (category != null) {
            // Bước 1: Thử tìm metric trực tiếp theo category người dùng đã nhập
            String normalizedMetricName = row.getMetricName().replaceAll("[\\s\\p{Z}\\p{C}]+", "").toLowerCase();
            String metricKey = category.getId() + "_" + normalizedMetricName;
            metric = metricsByKey.get(metricKey);

            // Bước 2: Nếu không tìm thấy, thử tìm trong các category con của nó
            // (hỗ trợ trường hợp người dùng nhập tên dịch vụ cha thay vì tên dịch vụ con)
            if (metric == null) {
                List<ServiceQualityCategory> children = childCategoriesByParentId.get(category.getId());
                if (children != null && !children.isEmpty()) {
                    for (ServiceQualityCategory child : children) {
                        String childMetricKey = child.getId() + "_" + normalizedMetricName;
                        ServiceQualityMetric candidate = metricsByKey.get(childMetricKey);
                        if (candidate != null) {
                            // Tìm thấy metric trong dịch vụ con
                            metric = candidate;
                            // Lưu lại key theo id con để saveImportGroup tra đúng metric
                            // Không thay đổi categoryName trong row → lưu DB sẽ dùng category cha
                            row.setResolvedMetricKey(childMetricKey);
                            break;
                        }
                    }
                }
            }

            // Nếu tìm thấy trực tiếp theo category người dùng nhập (không qua con),
            // cũng lưu resolvedMetricKey theo key thông thường để saveImportGroup dùng nhất quán
            if (metric != null && row.getResolvedMetricKey() == null) {
                row.setResolvedMetricKey(metricKey);
            }

            if (metric == null) {
                errors.add("Tên chỉ số '" + row.getMetricName() + "' không tồn tại hoặc không thuộc loại '"
                        + row.getCategoryName() + "' (hoặc các dịch vụ con của nó)");
            }
        }

        // Validate cột C: Mức độ cảnh báo
        if (row.getLevelNumber() == null) {
            errors.add("Mức độ cảnh báo phải là 'Mức 1', 'Mức 2' hoặc 'Mức 3'");
        }

        // Validate cột D: Phép so sánh
        if (row.getConditionOperator() == null) {
            errors.add("Phép so sánh không hợp lệ. Chấp nhận: <, <=, >, >=, =, between");
        }

        // Validate cột E: Giá trị ngưỡng V1 — bắt buộc với mọi phép so sánh

        validateThreshold(
                row.getV1Raw(),
                row.getThresholdValue(),
                "Giá trị ngưỡng (V1)",
                errors);

        // Validate cột F: Giá trị ngưỡng V2 — chỉ bắt buộc khi phép so sánh là
        // "between"
        if (row.getConditionOperator() == 5) {

            validateThreshold(
                    row.getV2Raw(),
                    row.getThresholdValueTo(),
                    "Giá trị ngưỡng (V2)",
                    errors);

            if (row.getThresholdValue() != null
                    && row.getThresholdValueTo() != null
                    && row.getThresholdValue().compareTo(row.getThresholdValueTo()) >= 0) {

                errors.add("Giá trị ngưỡng V2 phải lớn hơn V1");
            }

        } else {
            row.setThresholdValueTo(null);
        }

        // Validate cột G: Nhận SMS
        if (row.getReceiveSms() == null) {
            errors.add("Nhận SMS chỉ chấp nhận 'Y' hoặc 'N'");
        }

        // Validate cột H: Nhận Email
        if (row.getReceiveEmail() == null) {
            errors.add("Nhận Email chỉ chấp nhận 'Y' hoặc 'N'");
        }

        // Validate cột I: Mã đầu mối (bắt buộc nhập)
        if (row.getGroupCodes() == null || row.getGroupCodes().isEmpty()) {
            errors.add("Mã đầu mối bắt buộc nhập");
        } else {
            for (String code : row.getGroupCodes()) {
                if (!groupsByCode.containsKey(code)) {
                    errors.add("Mã đầu mối '" + code + "' không tồn tại trong hệ thống");
                }
            }
        }

        // Kiểm tra trùng với DB: cặp (categoryId + metricId) đã tồn tại chưa
        if (category != null && metric != null) {
            boolean existsInDb = existingConfigKeys.contains(category.getId() + "_" + metric.getId());
            if (existsInDb) {
                errors.add("Cấu hình cảnh báo cho chỉ số '" + row.getMetricName() + "' đã tồn tại trong hệ thống");
            }
        }

        // Nếu có lỗi thì nối các lỗi lại với nhau thành dạng danh sách
        // Thêm khoảng trắng trước dấu trừ để tránh lỗi công thức khi xuất Excel
        if (!errors.isEmpty()) {
            row.setErrorMessage(" - " + String.join("\n - ", errors));
        }
    }

    private void validateThreshold(
            String rawValue,
            BigDecimal value,
            String fieldName,
            List<String> errors) {

        if (rawValue == null || rawValue.isBlank()) {
            errors.add(fieldName + " bắt buộc nhập");
            return;
        }

        if (!rawValue.matches("^-?\\d+(\\.\\d{1,2})?$")) {
            errors.add(
                    fieldName + " không hợp lệ: Chỉ cho phép số, dùng dấu chấm (.) và tối đa 2 số sau dấu thập phân");
            return;
        }

        if (value == null) {
            errors.add(fieldName + " không hợp lệ");
            return;
        }

        if (value.compareTo(BigDecimal.ZERO) < 0) {
            errors.add(fieldName + " phải >= 0");
            return;
        }

        if (value.compareTo(BigDecimal.valueOf(100)) > 0) {
            errors.add(fieldName + " không được lớn hơn 100");
        }
    }

    /**
     * Phát hiện trùng lặp trong file:
     * Bộ 3 (categoryName + metricName + levelNumber) phải xuất hiện duy nhất 1 lần.
     * Nếu trùng → đánh dấu lỗi cho tất cả dòng bị trùng.
     */
    private void detectDuplicatesInFile(List<ImportAlertConfigRowDTO> rows) {
        // Gom nhóm các dòng theo cặp khóa (metricName|levelNumber)
        Map<String, List<ImportAlertConfigRowDTO>> groupedByKey = new HashMap<>();

        for (ImportAlertConfigRowDTO row : rows) {
            // Không bỏ qua dòng có lỗi khác, nhưng phải đảm bảo Loại chỉ số, Tên chỉ số và Mức cảnh báo
            // không bị trống thì mới có cơ sở để so sánh trùng lặp
            if (row.getCategoryName() == null || row.getCategoryName().isEmpty()
                    || row.getMetricName() == null || row.getMetricName().isEmpty()
                    || row.getLevelNumber() == null) {
                continue;
            }

            String categoryPart = row.getCategoryName().replaceAll("[\\s\\p{Z}\\p{C}]+", "").toLowerCase();
            String metricPart = row.getMetricName().replaceAll("[\\s\\p{Z}\\p{C}]+", "").toLowerCase();
            String key = categoryPart + "|" + metricPart + "|" + row.getLevelNumber();
            groupedByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        // Kiểm tra xem nhóm nào có nhiều hơn 1 dòng -> Đánh dấu lỗi tất cả
        for (Map.Entry<String, List<ImportAlertConfigRowDTO>> entry : groupedByKey.entrySet()) {
            List<ImportAlertConfigRowDTO> duplicateRows = entry.getValue();
            if (duplicateRows.size() > 1) {
                // Tạo chuỗi danh sách các dòng bị trùng để báo lỗi chi tiết
                List<String> rowNumbers = new ArrayList<>();
                for (ImportAlertConfigRowDTO dupRow : duplicateRows) {
                    rowNumbers.add(String.valueOf(dupRow.getRowNumber()));
                }
                String duplicateMsg = "Trùng lặp trong file: Cấu hình (Loại chỉ số, Tên chỉ số và Mức cảnh báo) xuất hiện tại các dòng: "
                        + String.join(", ", rowNumbers);

                // Đánh dấu lỗi cho TẤT CẢ các dòng bị trùng (cộng dồn lỗi nếu đã có lỗi trước
                // đó)
                for (ImportAlertConfigRowDTO dupRow : duplicateRows) {
                    if (dupRow.hasError()) {
                        dupRow.setErrorMessage(dupRow.getErrorMessage() + "; " + duplicateMsg);
                    } else {
                        dupRow.setErrorMessage(duplicateMsg);
                    }
                }
            }
        }
    }

    /**
     * Gom các dòng hợp lệ (chưa có lỗi) theo cặp (categoryName + metricName).
     * Nhiều dòng cùng categoryName + metricName → 1 AlertConfig + nhiều AlertLevel.
     * Dùng LinkedHashMap để giữ thứ tự xuất hiện trong file.
     */
    private Map<String, List<ImportAlertConfigRowDTO>> groupValidRowsByMetric(
            List<ImportAlertConfigRowDTO> rows) {

        Map<String, List<ImportAlertConfigRowDTO>> grouped = new LinkedHashMap<>();

        for (ImportAlertConfigRowDTO row : rows) {
            // Chỉ gom những dòng chưa có lỗi (errorMessage == null)
            if (row.getErrorMessage() != null)
                continue;

            // Key phải bao gồm cả categoryName để phân biệt các metric cùng tên nhưng thuộc loại khác nhau
            String categoryPart = row.getCategoryName().replaceAll("[\\s\\p{Z}\\p{C}]+", "").toLowerCase();
            String metricPart = row.getMetricName().replaceAll("[\\s\\p{Z}\\p{C}]+", "").toLowerCase();
            String key = categoryPart + "|" + metricPart;
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        return grouped;
    }

    /**
     * Lưu 1 nhóm (1 AlertConfig + nhiều AlertLevel) vào DB.
     *
     * NOTE: method này chỉ được giữ lại để backward-compatible với code cũ.
     * Thực tế bây giờ việc lưu được delegate sang importHelper.saveImportGroup()
     * để @Transactional(REQUIRES_NEW) hoạt động đúng qua Spring proxy.
     *
     * @deprecated Dùng importHelper.saveImportGroup() thay thế
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveImportGroup(
            List<ImportAlertConfigRowDTO> groupRows,
            Map<String, ServiceQualityCategory> categoriesByName,
            Map<String, ServiceQualityMetric> metricsByKey,
            Map<String, NotificationChannel> channelsByName,
            Map<String, AlertGroup> groupsByCode,
            String createdBy) {
        // Delegate sang importHelper — method này không dùng trực tiếp nữa
        importHelper.saveImportGroup(groupRows, categoriesByName, metricsByKey, channelsByName, groupsByCode,
                createdBy);
    }

    /**
     * Tạo file Excel kết quả lỗi:
     *
     * Dùng lại file upload gốc để giữ nguyên định dạng, độ rộng cột, chiều cao
     * dòng.
     * Thêm cột "Kết quả" (cột J) ở cuối và bôi đỏ lỗi.
     * Trả về nội dung file dưới dạng byte[].
     */
    private byte[] buildErrorExcelFile(List<ImportAlertConfigRowDTO> rows, MultipartFile file) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream());
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.getSheetAt(0);

            // Thêm header "Kết quả" vào dòng 0
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                headerRow = sheet.createRow(0);
            }
            Cell headerCell = headerRow.createCell(EXPECTED_HEADERS.size());
            headerCell.setCellValue("Kết quả");

            // Tạo CellStyle cho header (in đậm, cỡ 12, Times New Roman)
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setFontName("Times New Roman");
            headerFont.setFontHeightInPoints((short) 12);
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);

            if (headerStyle instanceof org.apache.poi.xssf.usermodel.XSSFCellStyle) {
                byte[] rgb = new byte[] { (byte) 184, (byte) 204, (byte) 228 };
                org.apache.poi.xssf.usermodel.XSSFColor bgColor = new org.apache.poi.xssf.usermodel.XSSFColor(rgb,
                        null);
                ((org.apache.poi.xssf.usermodel.XSSFCellStyle) headerStyle).setFillForegroundColor(bgColor);
                headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            }

            headerCell.setCellStyle(headerStyle);

            // Tạo CellStyle cho lỗi (chữ màu đỏ, Times New Roman, 12)
            CellStyle errorStyle = workbook.createCellStyle();
            Font errorFont = workbook.createFont();
            errorFont.setFontName("Times New Roman");
            errorFont.setFontHeightInPoints((short) 12);
            errorFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.RED.getIndex());
            errorStyle.setFont(errorFont);
            errorStyle.setWrapText(true);

            // Tạo CellStyle cho thành công (Times New Roman, 12)
            CellStyle successStyle = workbook.createCellStyle();
            Font successFont = workbook.createFont();
            successFont.setFontName("Times New Roman");
            successFont.setFontHeightInPoints((short) 12);
            successStyle.setFont(successFont);

            // Điền kết quả vào từng dòng
            for (ImportAlertConfigRowDTO row : rows) {
                // rowNumber = rowIndex + 1 -> index trên sheet = row.getRowNumber() - 1
                int rowIndex = row.getRowNumber() - 1;
                Row dataRow = sheet.getRow(rowIndex);
                if (dataRow == null) {
                    dataRow = sheet.createRow(rowIndex);
                }

                // Trim khoảng trắng cho tất cả các cột dữ liệu gốc
                for (int col = 0; col < EXPECTED_HEADERS.size(); col++) {
                    Cell cell = dataRow.getCell(col);
                    if (cell != null && cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
                        String val = cell.getStringCellValue();
                        if (val != null) {
                            cell.setCellValue(val.trim());
                        }
                    }
                }

                Cell resultCell = dataRow.createCell(EXPECTED_HEADERS.size());

                if (row.hasError()) {
                    resultCell.setCellValue(row.getErrorMessage());
                    resultCell.setCellStyle(errorStyle);
                } else {
                    resultCell.setCellValue("Thành công");
                    resultCell.setCellStyle(successStyle);
                }
            }

            // Auto-size cột kết quả
            sheet.autoSizeColumn(EXPECTED_HEADERS.size());

            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Load toàn bộ ServiceQualityCategory từ DB vào Map.
     * key = tên category (lowercase), value = entity.
     */
    private Map<String, ServiceQualityCategory> loadAllCategories() {
        return categoryRepository.findAll().stream()
                .collect(Collectors.toMap(
                        cat -> cat.getName().toLowerCase(),
                        cat -> cat,
                        (existing, duplicate) -> existing // nếu trùng tên thì giữ cái đầu
                ));
    }

    /**
     * Từ map categoriesByName, build ra map: parentId → danh sách category con.
     * Dùng để hỗ trợ nhập tên dịch vụ cha trong file Excel:
     * nếu người dùng nhập tên cha, hệ thống sẽ tự tìm metric trong các dịch vụ con.
     */
    private Map<Integer, List<ServiceQualityCategory>> loadChildCategoriesByParentId(
            Map<String, ServiceQualityCategory> categoriesByName) {
        Map<Integer, List<ServiceQualityCategory>> result = new HashMap<>();
        for (ServiceQualityCategory cat : categoriesByName.values()) {
            if (cat.getParentId() != null) {
                result.computeIfAbsent(cat.getParentId(), k -> new ArrayList<>()).add(cat);
            }
        }
        return result;
    }

    /**
     * Load toàn bộ ServiceQualityMetric từ DB vào Map.
     * key = categoryId + "_" + tên metric (lowercase), value = entity.
     */
    private Map<String, ServiceQualityMetric> loadAllMetrics() {
        return metricRepository.findAll().stream()
                .collect(Collectors.toMap(
                        m -> m.getCategoryId() + "_" + m.getName().replaceAll("[\\s\\p{Z}\\p{C}]+", "").toLowerCase(),
                        m -> m,
                        (existing, duplicate) -> existing));
    }

    /**
     * Load toàn bộ cấu hình cảnh báo đang có trên hệ thống để check trùng.
     * key = categoryId + "_" + metricId
     */
    private Set<String> loadAllActiveConfigKeys() {
        return alertConfigRepository.findAllActiveConfigKeys().stream()
                .map(row -> row[0] + "_" + row[1])
                .collect(Collectors.toSet());
    }

    /**
     * Load toàn bộ NotificationChannel từ DB vào Map.
     * key = tên channel (lowercase), value = entity.
     */
    private Map<String, NotificationChannel> loadAllChannels() {
        return notificationChannelRepository.findAll().stream()
                .collect(Collectors.toMap(
                        ch -> ch.getName().toLowerCase(),
                        ch -> ch,
                        (existing, duplicate) -> existing));
    }

    /**
     * Load AlertGroup theo danh sách code có trong file Excel.
     * Chỉ load những code thực sự xuất hiện trong file (tiết kiệm bộ nhớ).
     * key = code (giữ nguyên case), value = entity.
     */
    private Map<String, AlertGroup> loadAllGroupsByCode(List<ImportAlertConfigRowDTO> rows) {
        // Thu thập tất cả code đầu mối từ các dòng trong file
        Set<String> allCodes = new HashSet<>();
        for (ImportAlertConfigRowDTO row : rows) {
            if (row.getGroupCodes() != null) {
                allCodes.addAll(row.getGroupCodes());
            }
        }

        if (allCodes.isEmpty()) {
            return new HashMap<>();
        }

        // Truy vấn DB 1 lần cho toàn bộ code
        List<AlertGroup> groups = alertGroupRepository.findByCodeInAndDeletedFalse(new ArrayList<>(allCodes));
        return groups.stream().collect(Collectors.toMap(
                AlertGroup::getCode,
                group -> group,
                (existing, duplicate) -> existing));
    }

    // ====================== Các phương thức parse dữ liệu từ Excel
    // ======================

    /**
     * Parse "Mức 1" → 1, "Mức 2" → 2, "Mức 3" → 3.
     * Trả về null nếu không hợp lệ.
     */
    private Integer parseLevelNumber(String raw) {
        if (raw == null || raw.isEmpty())
            return null;
        switch (raw.trim()) {
            case "Mức 1":
                return 1;
            case "Mức 2":
                return 2;
            case "Mức 3":
                return 3;
            default:
                return null;
        }
    }

    /**
     * Parse phép so sánh từ ký hiệu sang số nguyên.
     * Trả về null nếu không hợp lệ.
     */
    private Integer parseConditionOperator(String raw) {
        if (raw == null || raw.isEmpty())
            return null;
        switch (raw.trim().toLowerCase()) {
            case "<":
                return 1;
            case "<=":
                return 2;
            case ">":
                return 3;
            case ">=":
                return 4;
            case "=":
                return 5;
            case "between":
                return 6;
            default:
                return null;
        }
    }

    /**
     * Chuyển conditionOperator ngược lại thành ký hiệu hiển thị trong file lỗi.
     * Đúng theo DB schema: 1=<, 2=>, 3=>=, 4=<=, 5=between
     */
    private String formatOperator(Integer operator) {
        if (operator == null)
            return "";
        switch (operator) {
            case 1:
                return "<";
            case 2:
                return ">";
            case 3:
                return ">=";
            case 4:
                return "<=";
            case 5:
                return "between";
            default:
                return "";
        }
    }

    /**
     * Parse chuỗi số thành BigDecimal.
     * Trả về null nếu chuỗi rỗng hoặc không phải số.
     */
    private BigDecimal parseBigDecimal(String raw) {
        if (raw == null || raw.isEmpty())
            return null;
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Parse "Y" → true, "N" → false.
     * Trả về null nếu giá trị không hợp lệ.
     */
    private Boolean parseYesNo(String raw) {
        if ("Y".equalsIgnoreCase(raw))
            return true;
        if ("N".equalsIgnoreCase(raw))
            return false;
        return null;
    }

    /**
     * Parse chuỗi mã đầu mối: "VTT; VTT_SME" → ["VTT", "VTT_SME"].
     * Phân cách bằng dấu ";".
     */
    private List<String> parseGroupCodes(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isEmpty())
            return result;

        String[] parts = raw.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * Đọc giá trị của một ô Excel thành String.
     * Xử lý cả ô kiểu TEXT, NUMERIC, BOOLEAN, và FORMULA.
     */
    private String getCellStringValue(Cell cell) {
        if (cell == null)
            return "";

        if (cell.getCellType() == CellType.NUMERIC) {
            // Ô số: tránh hiển thị dạng 95.0 khi giá trị là số nguyên
            double numericValue = cell.getNumericCellValue();
            if (numericValue == Math.floor(numericValue) && !Double.isInfinite(numericValue)) {
                return String.valueOf((long) numericValue);
            }
            return String.valueOf(numericValue);
        }

        if (cell.getCellType() == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        }

        // Mặc định: đọc dưới dạng chuỗi bằng DataFormatter (cách chuẩn không bị
        // deprecated)
        DataFormatter dataFormatter = new DataFormatter();
        return dataFormatter.formatCellValue(cell);
    }

    /**
     * Kiểm tra xem 1 dòng Excel có hoàn toàn trống không.
     * Bỏ qua các dòng trống để tránh xử lý thừa.
     */
    private boolean isRowEmpty(Row row) {
        if (row == null)
            return true;
        for (int c = 0; c < 9; c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getCellStringValue(cell).trim();
                if (!value.isEmpty())
                    return false;
            }
        }
        return true;
    }
}
