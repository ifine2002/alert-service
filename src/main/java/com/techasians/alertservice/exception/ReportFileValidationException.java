package com.techasians.alertservice.exception;

/**
 * Lỗi nghiệp vụ khi file không hợp lệ trong quá trình import báo cáo tuần.
 * GlobalExceptionHandler sẽ bắt và trả HTTP 400 Bad Request.
 * <p>
 * Ví dụ sử dụng:
 * <pre>
 *   throw new ReportFileValidationException("File Excel thiếu các sheet bắt buộc: PL08-KPI VTT, Bất cập");
 *   throw new ReportFileValidationException("Extension không hợp lệ. Chỉ chấp nhận .xlsx");
 *   throw new ReportFileValidationException("File bị hỏng hoặc không mở được");
 * </pre>
 */
public class ReportFileValidationException extends RuntimeException {

    public ReportFileValidationException(String message) {
        super(message);
    }

    public ReportFileValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
