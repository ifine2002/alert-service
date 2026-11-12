package com.techasians.alertservice.service;

import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/**
 * Interface validate file upload cho báo cáo tuần.
 * Tách riêng để dễ unit test và không phụ thuộc filesystem thực tế.
 */
public interface ReportValidationService {

    /**
     * Validate toàn bộ: extension, mở được và (nếu Excel) tồn tại đủ sheet bắt buộc theo tên.
     * Riêng definition 1010: đọc Ngày phát hiện theo d/M/yyyy hoặc d/M/yy, ghi lỗi vào cột warning.
     * Ném ReportFileValidationException nếu sai extension, cấu trúc hoặc cấu hình sheet/header.
     *
     * @param multipartFile   file từ frontend
     * @param allowedExtension extension cho phép (ví dụ: "docx", "xlsx") — từ rpt_definitions
     * @param definitionId     dùng để load danh sách sheet bắt buộc từ DB (chỉ cần nếu là Excel)
     * @param weekStartDate    ngày bắt đầu tuần; placeholder năm lấy theo ngày kết thúc tuần
     * @return file Excel có warning nếu có lỗi ngày; null nếu file hợp lệ
     */
    byte[] validate(MultipartFile multipartFile, String allowedExtension,
                  Long definitionId, LocalDate weekStartDate);

    /**
     * Validate tuần báo cáo — weekStartDate phải là Thứ Hai.
     * Ném IllegalArgumentException nếu không hợp lệ.
     */
    void validateWeekStartDate(LocalDate weekStartDate);

    /**
     * Validate actor không rỗng.
     * Ném IllegalArgumentException nếu không hợp lệ.
     */
    void validateActor(String actor);
}
