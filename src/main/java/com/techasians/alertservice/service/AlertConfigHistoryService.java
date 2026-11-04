package com.techasians.alertservice.service;

import com.techasians.alertservice.dto.AlertConfigDetailResponse;

public interface AlertConfigHistoryService {
    
    /**
     * Lưu lịch sử thay đổi cấu hình cảnh báo.
     * 
     * @param alertConfigId ID của cấu hình
     * @param actionType Loại thao tác: 1-Tạo mới, 2-Cập nhật, 3-Xóa mềm, 4-Kích hoạt, 5-Vô hiệu hóa
     * @param before Dữ liệu cấu hình đầy đủ trước khi thay đổi
     * @param after Dữ liệu cấu hình đầy đủ sau khi thay đổi
     * @param createdBy Người thực hiện thao tác
     */
    void saveLog(Integer alertConfigId, int actionType, AlertConfigDetailResponse before, AlertConfigDetailResponse after, String createdBy);
}
