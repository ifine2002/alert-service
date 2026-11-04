package com.techasians.alertservice.exception;

/**
 * Exception được ném ra khi có lỗi giao tiếp với dịch vụ bên ngoài,
 * ví dụ: MinIO, API bên thứ 3, v.v.
 *
 * Ví dụ sai (dùng RuntimeException thô):
 *   throw new RuntimeException("MinIO lỗi: " + e.getMessage());
 *
 * Ví dụ đúng (dùng ExternalApiException để ghi rõ target và statusCode):
 *   throw new ExternalApiException("report/2024/file.xlsx", 502, e.getMessage(), e);
 */
public class ExternalApiException extends RuntimeException {

    // Tên target bị lỗi, ví dụ: "report/2024/file.xlsx" hoặc "my-bucket"
    private final String target;

    // HTTP status code tương ứng với loại lỗi (thường là 502 Bad Gateway)
    private final int statusCode;

    public ExternalApiException(String target, int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.target = target;
        this.statusCode = statusCode;
    }

    public String getTarget() {
        return target;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
