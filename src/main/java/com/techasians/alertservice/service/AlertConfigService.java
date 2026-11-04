package com.techasians.alertservice.service;

import com.techasians.alertservice.dto.AlertConfigRequest;
import com.techasians.alertservice.dto.AlertConfigResponse;
import com.techasians.alertservice.dto.AlertConfigSearchRequest;
import com.techasians.alertservice.dto.AlertConfigDetailResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

public interface AlertConfigService {

    // Lấy danh sách cấu hình cảnh báo, có phân trang và tìm kiếm
    Page<AlertConfigResponse> search(AlertConfigSearchRequest searchRequest);

    // Thêm mới một cấu hình cảnh báo (bao gồm các mức và kênh nhận bên trong)
    Integer create(AlertConfigRequest request, String createdBy);

    // Cập nhật cấu hình cảnh báo (dùng cơ chế Xóa và Chèn lại cho các mức)
    Integer update(Integer id, AlertConfigRequest request, String updatedBy);

    // Xóa mềm cấu hình cảnh báo (chỉ bật cờ deleted = true)
    void delete(Integer id, String deletedBy);

    AlertConfigDetailResponse getDetail(Integer id);

    /**
     * Import hàng loạt cấu hình cảnh báo từ file Excel.
     *
     * Trả về:
     *   - null: nếu tất cả dòng import thành công
     *   - byte[]: nội dung file Excel lỗi (để Controller cho người dùng download)
     *             File lỗi có thêm cột "Kết quả" mô tả lỗi từng dòng.
     *
     * Ném ra IllegalArgumentException nếu file sai định dạng (.xlsx, header sai).
     */
    byte[] importFromExcel(MultipartFile file, String createdBy);
}

