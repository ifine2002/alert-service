package com.techasians.alertservice.dto;

import org.springframework.core.io.Resource;

/**
 * Data Transfer Object đóng gói dữ liệu cần thiết để download file từ Service lên Controller.
 */
public class ReportDownloadDto {
    private final Resource resource;
    private final String fileName;
    private final String contentType;

    public ReportDownloadDto(Resource resource, String fileName, String contentType) {
        this.resource = resource;
        this.fileName = fileName;
        this.contentType = contentType;
    }

    public Resource getResource() {
        return resource;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }
}
