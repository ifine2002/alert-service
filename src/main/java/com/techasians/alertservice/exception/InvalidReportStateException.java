package com.techasians.alertservice.exception;

/**
 * Lỗi nghiệp vụ khi thao tác không hợp lệ theo trạng thái hiện tại của instance.
 * GlobalExceptionHandler sẽ bắt và trả HTTP 409 Conflict.
 * <p>
 * Ví dụ sử dụng:
 * <pre>
 *   throw new InvalidReportStateException("Chỉ có thể từ chối file đang ở trạng thái COMPLETED");
 *   throw new InvalidReportStateException("Instance đang xử lý, vui lòng thử lại sau");
 *   throw new InvalidReportStateException("Definition không phải loại import file input");
 * </pre>
 */
public class InvalidReportStateException extends RuntimeException {

    public InvalidReportStateException(String message) {
        super(message);
    }

    public InvalidReportStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
