package com.techasians.alertservice.exception;

import com.techasians.alertservice.dto.ApiResponse;
import com.techasians.alertservice.exception.InvalidReportStateException;
import com.techasians.alertservice.exception.ReportFileValidationException;
import com.techasians.alertservice.exception.ReportPreviewGenerationException;
import com.techasians.alertservice.exception.UnsupportedReportPreviewException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import com.techasians.alertservice.dto.FileTemplateResponse;
import com.techasians.alertservice.exception.ImportValidationException;
import org.springframework.http.ResponseEntity;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Xử lý lỗi Validate (Lỗi 400 Bad Request) khi Frontend gửi thiếu/sai dữ liệu
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String errorMessage = "Dữ liệu đầu vào không hợp lệ";

        // Tìm xem có lỗi nào ở các trường nested (VD: levels[2].channelIds) không
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            if (error.getField().contains("[")) {
                errorMessage = error.getDefaultMessage();
                break; // Lấy thông báo đầu tiên của mảng/list rồi dừng
            }
        }

        // Nếu không có lỗi mảng, lấy lỗi đầu tiên bất kỳ
        if ("Dữ liệu đầu vào không hợp lệ".equals(errorMessage) && ex.getBindingResult().hasFieldErrors()) {
            errorMessage = ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        }

        return ApiResponse.error(400, errorMessage);
    }

    // Xử lý lỗi không tìm thấy (Lỗi 404 Not Found)
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleResourceNotFoundException(ResourceNotFoundException ex) {
        return ApiResponse.error(404, ex.getMessage());
    }

    // Xử lý lỗi trùng lặp (Lỗi 409 Conflict)
    @ExceptionHandler(DuplicateResourceException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleDuplicateResourceException(DuplicateResourceException ex) {
        return ApiResponse.error(409, ex.getMessage());
    }

    // Xử lý lỗi tham số không hợp lệ (Lỗi 400 Bad Request)
    // VD: file import sai định dạng, file sai header template
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ApiResponse.error(400, ex.getMessage());
    }

    @ExceptionHandler(ImportValidationException.class)
    public ResponseEntity<ApiResponse<FileTemplateResponse>> handleImportValidationException(ImportValidationException ex) {
        ApiResponse<FileTemplateResponse> response = new ApiResponse<>(400, false, ex.getMessage(), ex.getFileTemplateResponse());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // Xử lý lỗi validate file báo cáo tuần (sai extension, thiếu sheet, file hỏng...) — Lỗi 400
    @ExceptionHandler(ReportFileValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleReportFileValidationException(ReportFileValidationException ex) {
        return ApiResponse.error(400, ex.getMessage());
    }

    // Xử lý lỗi thao tác không hợp lệ theo trạng thái instance báo cáo — Lỗi 409
    @ExceptionHandler(InvalidReportStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleInvalidReportStateException(InvalidReportStateException ex) {
        return ApiResponse.error(409, ex.getMessage());
    }

    // Xử lý lỗi preview loại file không hỗ trợ (Excel, v.v.) — Lỗi 415
    @ExceptionHandler(UnsupportedReportPreviewException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ApiResponse<Void> handleUnsupportedReportPreviewException(UnsupportedReportPreviewException ex) {
        return ApiResponse.error(415, ex.getMessage());
    }

    // Xử lý lỗi chuyển đổi Word sang PDF — Lỗi 500
    // Không trả đường dẫn vật lý hay chi tiết Aspose cho client
    @ExceptionHandler(ReportPreviewGenerationException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleReportPreviewGenerationException(ReportPreviewGenerationException ex) {
        return ApiResponse.error(500, "Không thể tạo bản xem trước. Vui lòng thử lại sau.");
    }

    // Xử lý lỗi giao tiếp với dịch vụ bên ngoài (MinIO, API bên thứ 3, v.v.)
    // statusCode thường là 502 Bad Gateway
    @ExceptionHandler(ExternalApiException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ApiResponse<Void> handleExternalApiException(ExternalApiException ex) {
        return ApiResponse.error(502, "Lỗi kết nối dịch vụ lưu trữ [" + ex.getTarget() + "]: " + ex.getMessage());
    }

    // Xử lý các lỗi chung khác (Lỗi 500 Internal Server Error)
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleGlobalException(Exception ex) {
        return ApiResponse.error(500, "Đã xảy ra lỗi hệ thống: " + ex.getMessage());
    }
}
