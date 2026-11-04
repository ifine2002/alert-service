package com.techasians.alertservice.dto;

import org.springframework.core.io.Resource;

/**
 * Kết quả preview Word → PDF.
 * Service trả DTO này cho controller; controller chỉ dựng HTTP headers từ đây.
 * Không chứa physical path, SHA-256 hoặc trạng thái STALE
 * (frontend tự đọc trạng thái từ API list output).
 */
public class ReportPreviewDto {

    // PDF bytes đã chuyển đổi, sẵn sàng stream
    private final Resource resource;

    // Tên file để đặt trong Content-Disposition, ví dụ: bao-cao-preview.pdf
    private final String fileName;

    // Số byte PDF thực tế để đặt Content-Length
    private final long contentLength;

    public ReportPreviewDto(Resource resource, String fileName, long contentLength) {
        this.resource = resource;
        this.fileName = fileName;
        this.contentLength = contentLength;
    }

    public Resource getResource() {
        return resource;
    }

    public String getFileName() {
        return fileName;
    }

    public long getContentLength() {
        return contentLength;
    }
}
