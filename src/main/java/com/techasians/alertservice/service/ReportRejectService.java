package com.techasians.alertservice.service;

/**
 * Xử lý luồng reject một file input đã COMPLETED.
 * Phase 2: chưa kiểm tra quyền/role; actor nhận từ request body.
 */
public interface ReportRejectService {

    /**
     * Từ chối một file input đang ở trạng thái COMPLETED.
     * File vật lý và rpt_file metadata được giữ nguyên.
     *
     * @param instanceId ID của rpt_instance cần từ chối
     * @param reason     lý do từ chối (bắt buộc, không rỗng)
     * @param actor      người thực hiện (tạm nhận từ request)
     */
    void rejectInput(Long instanceId, String reason, String actor);
}
