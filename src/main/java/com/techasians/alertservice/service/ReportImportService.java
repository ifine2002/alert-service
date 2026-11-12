package com.techasians.alertservice.service;

import com.techasians.alertservice.dto.ReportImportResult;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/**
 * Xử lý luồng import và re-import file input báo cáo tuần.
 * Backend tự phân loại: chưa có rpt_file → IMPORT; đã có → REIMPORT.
 */
public interface ReportImportService {

    /**
     * Import hoặc re-import file input cho một tuần báo cáo.
     * <p>
     * Luồng an toàn:
     * 1. Validate tuần, actor và definition.
     * 2. Lưu file vào temp.
     * 3. Validate extension, nội dung và sheet (nếu Excel).
     * 4. Promote file sang storage chính thức.
     * 5. Commit DB (instance, rpt_file, audit) trong một transaction riêng (class khác).
     * 6. Nếu DB lỗi sau promote: xóa file mới, giữ file cũ và metadata cũ.
     *
     * @param definitionId  ID của rpt_definitions (phải là input-file-import-active)
     * @param weekStartDate ngày Thứ Hai bắt đầu tuần
     * @param file          file upload từ frontend
     * @param actor         người thực hiện (tạm nhận từ request)
     * @return kết quả import; errorFile chứa Excel cảnh báo nếu dữ liệu ngày của nguồn 1010 không hợp lệ
     */
    ReportImportResult importFile(Long definitionId,
                    LocalDate weekStartDate,
                    MultipartFile file,
                    String actor);
}
