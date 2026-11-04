package com.techasians.alertservice.exception;

/**
 * Ném khi preview được yêu cầu cho loại file không hỗ trợ (ví dụ: Excel).
 * Map về HTTP 415 Unsupported Media Type.
 */
public class UnsupportedReportPreviewException extends RuntimeException {

    public UnsupportedReportPreviewException(String message) {
        super(message);
    }
}
