package com.techasians.alertservice.service;

/** Metadata của object đã được lưu trên MinIO. */
public class StoredReportFile {
    private final String originalFilename;
    private final String objectName;
    private final long fileSize;
    private final String sha256;

    public StoredReportFile(String originalFilename, String objectName, long fileSize, String sha256) {
        this.originalFilename = originalFilename;
        this.objectName = objectName;
        this.fileSize = fileSize;
        this.sha256 = sha256;
    }

    public String getOriginalFilename() { return originalFilename; }
    public String getObjectName() { return objectName; }
    public long getFileSize() { return fileSize; }
    public String getSha256() { return sha256; }
}
