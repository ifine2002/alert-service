package com.techasians.alertservice.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techasians.alertservice.dto.AlertConfigDetailResponse;
import com.techasians.alertservice.entity.AlertConfigHistory;
import com.techasians.alertservice.repository.AlertConfigHistoryRepository;
import com.techasians.alertservice.service.AlertConfigHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AlertConfigHistoryServiceImpl implements AlertConfigHistoryService {

    private static final Logger logger = LoggerFactory.getLogger(AlertConfigHistoryServiceImpl.class);

    private final AlertConfigHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    public AlertConfigHistoryServiceImpl(AlertConfigHistoryRepository historyRepository, ObjectMapper objectMapper) {
        this.historyRepository = historyRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void saveLog(Integer alertConfigId, int actionType, AlertConfigDetailResponse before, AlertConfigDetailResponse after, String createdBy) {
        try {
            AlertConfigHistory history = new AlertConfigHistory()
                    .setAlertConfigId(alertConfigId)
                    .setActionType(actionType)
                    .setCreatedBy(createdBy);

            if (before != null) {
                history.setBeforeSnapshot(objectMapper.writeValueAsString(before));
            }
            if (after != null) {
                history.setAfterSnapshot(objectMapper.writeValueAsString(after));
            }

            historyRepository.save(history);
        } catch (JsonProcessingException e) {
            // Theo yêu cầu của nghiệp vụ, chỉ ghi log lỗi chứ không throw Exception làm gián đoạn luồng chính
            logger.error("Lỗi khi chuyển đổi snapshot sang JSON cho AlertConfig ID: {}. Không thể lưu log lịch sử.", alertConfigId, e);
        } catch (Exception e) {
            logger.error("Lỗi không xác định khi lưu lịch sử cho AlertConfig ID: {}.", alertConfigId, e);
        }
    }
}
