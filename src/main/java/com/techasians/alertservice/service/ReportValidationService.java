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
     * Ném ReportFileValidationException với message rõ ràng nếu không hợp lệ.
     *
     * @param multipartFile   file từ frontend
     * @param allowedExtension extension cho phép (ví dụ: "docx", "xlsx") — từ rpt_definitions
     * @param definitionId     dùng để load danh sách sheet bắt buộc từ DB (chỉ cần nếu là Excel)
     * @param weekStartDate    ngày bắt đầu tuần; placeholder năm lấy theo ngày kết thúc tuần
     */
    void validate(MultipartFile multipartFile, String allowedExtension,
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
