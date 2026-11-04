package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.dto.AlertConfigDetailResponse;
import com.techasians.alertservice.dto.AlertLevelRequest;
import com.techasians.alertservice.dto.ImportAlertConfigRowDTO;
import com.techasians.alertservice.entity.AlertConfig;
import com.techasians.alertservice.entity.AlertGroup;
import com.techasians.alertservice.entity.NotificationChannel;
import com.techasians.alertservice.entity.ServiceQualityCategory;
import com.techasians.alertservice.entity.ServiceQualityMetric;
import com.techasians.alertservice.exception.DuplicateResourceException;
import com.techasians.alertservice.exception.ResourceNotFoundException;
import com.techasians.alertservice.repository.AlertConfigRepository;
import com.techasians.alertservice.repository.ServiceQualityMetricRepository;
import com.techasians.alertservice.service.AlertConfigHistoryService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Helper class tách ra để chứa saveImportGroup
 * với @Transactional(REQUIRES_NEW).
 *
 * LÝ DO phải tách ra class riêng:
 * - Nếu để saveImportGroup trong AlertConfigServiceImpl và gọi
 * this.saveImportGroup(),
 * Spring proxy sẽ KHÔNG intercept được (self-invocation).
 * - Kết quả: @Transactional không có hiệu lực → không có Hibernate session →
 * lỗi "no session".
 * - Giải pháp: đặt method vào class riêng, inject qua Spring → proxy hoạt động
 * bình thường.
 *
 * Inject @Lazy AlertConfigServiceImpl để tránh circular dependency:
 * AlertConfigServiceImpl → AlertConfigImportHelper → AlertConfigServiceImpl
 */
@Service
public class AlertConfigImportHelper {

    private final AlertConfigRepository alertConfigRepository;
    private final ServiceQualityMetricRepository metricRepository;
    private final AlertConfigHistoryService alertConfigHistoryService;

    // @Lazy để phá circular dependency:
    // AlertConfigServiceImpl inject AlertConfigImportHelper (trong constructor)
    // AlertConfigImportHelper inject AlertConfigServiceImpl (ở đây)
    // Nếu không có @Lazy, Spring không biết khởi tạo cái nào trước → ném
    // BeanCurrentlyInCreationException
    private final AlertConfigServiceImpl alertConfigServiceImpl;

    public AlertConfigImportHelper(
            AlertConfigRepository alertConfigRepository,
            ServiceQualityMetricRepository metricRepository,
            AlertConfigHistoryService alertConfigHistoryService,
            @Lazy AlertConfigServiceImpl alertConfigServiceImpl) {
        this.alertConfigRepository = alertConfigRepository;
        this.metricRepository = metricRepository;
        this.alertConfigHistoryService = alertConfigHistoryService;
        this.alertConfigServiceImpl = alertConfigServiceImpl;
    }

    /**
     * Lưu 1 nhóm (1 AlertConfig + nhiều AlertLevel) vào DB trong transaction riêng.
     *
     * Dùng REQUIRES_NEW để mỗi nhóm có transaction độc lập:
     * - Nếu nhóm này lỗi → chỉ rollback nhóm này, các nhóm khác không bị ảnh hưởng.
     * - Outer transaction (importFromExcel) không bị cuốn vào rollback.
     *
     * @param groupRows        Danh sách dòng Excel thuộc cùng 1 chỉ số (cùng
     *                         metricName)
     * @param categoriesByName Map tên category (lowercase) → entity (đã pre-load)
     * @param channelsByName   Map tên channel (lowercase) → entity (đã pre-load)
     * @param groupsByCode     Map code đầu mối → entity (đã pre-load)
     * @param createdBy        Username người thực hiện import
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveImportGroup(
            List<ImportAlertConfigRowDTO> groupRows,
            Map<String, ServiceQualityCategory> categoriesByName,
            Map<String, ServiceQualityMetric> metricsByKey,
            Map<String, NotificationChannel> channelsByName,
            Map<String, AlertGroup> groupsByCode,
            String createdBy) {

        // Lấy thông tin Category và Metric từ dòng đầu tiên trong nhóm
        ImportAlertConfigRowDTO firstRow = groupRows.get(0);
        ServiceQualityCategory category = categoriesByName.get(firstRow.getCategoryName().toLowerCase());

        // Dùng resolvedMetricKey đã được set trong validateRow() thay vì tự build lại
        // resolvedMetricKey luôn dùng id của category thực sự chứa metric (có thể là con)
        // dù categoryName trong row có thể là tên cha
        String metricKey = firstRow.getResolvedMetricKey();
        ServiceQualityMetric preLoadedMetric = metricsByKey.get(metricKey);

        if (preLoadedMetric == null) {
            throw new ResourceNotFoundException("Không tìm thấy chỉ số: " + firstRow.getMetricName());
        }

        // Metric phải tìm lại từ DB thông qua ID thay vì tên (entity từ Map pre-load có thể bị detach)
        ServiceQualityMetric metric = metricRepository.findById(preLoadedMetric.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy chỉ số: " + firstRow.getMetricName()));

        // Double-check lại DB trước khi lưu (tránh race condition khi import file lớn)
        if (alertConfigRepository.existsByServiceQualityCategoryIdAndServiceQualityMetricIdAndDeletedFalse(
                category.getId(), metric.getId())) {
            throw new DuplicateResourceException(
                    "Cấu hình cảnh báo cho chỉ số '" + firstRow.getMetricName() + "' đã tồn tại trong hệ thống");
        }

        // Tạo AlertConfig
        AlertConfig alertConfig = new AlertConfig()
                .setName(firstRow.getMetricName())
                .setServiceQualityCategory(category)
                .setServiceQualityMetric(metric)
                .setStatus(1)
                .setCreatedBy(createdBy)
                .setUpdatedBy(createdBy);

        AlertConfig savedConfig = alertConfigRepository.save(alertConfig);

        // Xây dựng danh sách AlertLevelRequest từ các dòng trong nhóm
        List<AlertLevelRequest> levelRequests = new ArrayList<>();
        for (ImportAlertConfigRowDTO row : groupRows) {

            // Xây dựng danh sách channelIds từ cờ SMS/Email
            // key trong channelsByName là lowercase: "sms", "email"
            List<Integer> channelIds = new ArrayList<>();
            if (Boolean.TRUE.equals(row.getReceiveSms())) {
                NotificationChannel smsChannel = channelsByName.get("sms");
                if (smsChannel != null)
                    channelIds.add(smsChannel.getId());
            }
            if (Boolean.TRUE.equals(row.getReceiveEmail())) {
                NotificationChannel emailChannel = channelsByName.get("email");
                if (emailChannel != null)
                    channelIds.add(emailChannel.getId());
            }

            // Xây dựng danh sách groupIds từ các code đầu mối
            List<Integer> groupIds = new ArrayList<>();
            if (row.getGroupCodes() != null) {
                for (String code : row.getGroupCodes()) {
                    AlertGroup group = groupsByCode.get(code);
                    if (group != null)
                        groupIds.add(group.getId().intValue());
                }
            }

            AlertLevelRequest levelRequest = new AlertLevelRequest()
                    .setLevelNumber(row.getLevelNumber())
                    .setConditionOperator(row.getConditionOperator())
                    .setThresholdValue(row.getThresholdValue())
                    .setThresholdValueTo(row.getThresholdValueTo())
                    .setChannelIds(channelIds)
                    .setGroupIds(groupIds);

            levelRequests.add(levelRequest);
        }

        // Gọi saveLevels (package-private) từ AlertConfigServiceImpl để lưu levels
        // Không cần cast vì inject thẳng AlertConfigServiceImpl
        alertConfigServiceImpl.saveLevels(savedConfig, levelRequests, createdBy);

        // Lưu lịch sử import (action_type = 1: Tạo mới)
        AlertConfigDetailResponse afterDetail = alertConfigServiceImpl.getDetail(savedConfig.getId());
        alertConfigHistoryService.saveLog(savedConfig.getId(), 1, null, afterDetail, createdBy);
    }
}
