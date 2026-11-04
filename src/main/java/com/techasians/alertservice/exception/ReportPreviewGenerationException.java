package com.techasians.alertservice.exception;

/**
 * Ném khi Aspose không chuyển đổi được file Word sang PDF.
 * Map về HTTP 500 Internal Server Error.
 * Không để lộ thông tin đường dẫn hay nội dung file trong message trả về client.
 */
public class ReportPreviewGenerationException extends RuntimeException {

    public ReportPreviewGenerationException(String message) {
        super(message);
    }

    public ReportPreviewGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
