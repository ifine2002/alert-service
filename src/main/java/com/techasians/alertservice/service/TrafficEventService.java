package com.techasians.alertservice.service;

import com.techasians.alertservice.dto.*;
import org.springframework.web.multipart.MultipartFile;

public interface TrafficEventService {

    /**
     * Tạo mới Traffic Event cùng schedules và channels.
     * Toàn bộ trong 1 transaction.
     */
    TrafficEventResponse create(TrafficEventRequest request, String createdBy);

    /**
     * Cập nhật Traffic Event và đồng bộ schedules/channels bằng diff-sync.
     * Chỉ insert/update/delete những bản ghi thực sự thay đổi.
     * Toàn bộ trong 1 transaction.
     */
    TrafficEventResponse update(Integer id, TrafficEventRequest request, String updatedBy);

    /**
     * Import sự kiện từ file Excel template.
     */
    TrafficEventImportResult importFile(MultipartFile file, String importedBy);

    /**
     * Soft delete Traffic Event (chỉ set deleted_at, deleted_by).
     * Schedules và channels giữ nguyên trong DB.
     */
    void delete(Integer id, String deletedBy);

    /**
     * Lấy chi tiết 1 Traffic Event kèm schedules và channels.
     */
    TrafficEventResponse getById(Integer id);

    /**
     * Tìm kiếm phân trang Traffic Event theo các filter tùy chọn.
     */
    PageResult<TrafficEventResponse> search(TrafficEventSearchRequest request);
}
