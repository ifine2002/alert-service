package com.techasians.alertservice.exception;

/**
 * Exception khi thiếu dữ liệu lịch sử bắt buộc cho tính toán forecast.
 *
 * Được ném trong các trường hợp:
 * 1. Thiếu dữ liệu lưu lượng ngày cụ thể trong context
 * 2. Baseline ngày thường = 0 (không thể tính tỷ lệ tác động)
 * 3. Không tìm được ngày thường tham chiếu (normal days rỗng)
 * 4. Không tìm được cặp tham chiếu cuối tuần hợp lệ
 *
 * Controller sẽ bắt exception này và trả về HTTP 422 Unprocessable Entity.
 */
public class ForecastDataException extends RuntimeException {

    public ForecastDataException(String message) {
        super(message);
    }

    public ForecastDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
