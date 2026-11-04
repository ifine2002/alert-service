package com.techasians.alertservice.service;

import com.techasians.alertservice.dto.ReportPreviewDto;

/**
 * Service xử lý yêu cầu preview file Word của một report instance.
 * Controller chỉ biết interface này, không truy cập repository hay Aspose trực tiếp.
 */
public interface WordPreviewService {

    /**
     * Chuyển đổi file Word hiện tại của instance sang PDF và trả về sẵn sàng stream.
     *
     * @param instanceId ID của rpt_instance cần xem trước
     * @return DTO chứa resource PDF, tên file và số byte
     * @throws com.techasians.alertservice.exception.ResourceNotFoundException
     *         khi instance hoặc metadata file không tồn tại
     * @throws com.techasians.alertservice.exception.InvalidReportStateException
     *         khi trạng thái hiện tại không cho phép preview
     * @throws com.techasians.alertservice.exception.UnsupportedReportPreviewException
     *         khi loại file không phải Word
     * @throws com.techasians.alertservice.exception.ReportPreviewGenerationException
     *         khi Aspose không chuyển đổi được file
     */
    ReportPreviewDto previewInstance(Long instanceId);
}
