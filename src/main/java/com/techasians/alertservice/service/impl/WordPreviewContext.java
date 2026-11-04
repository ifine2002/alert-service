package com.techasians.alertservice.service.impl;

/**
 * Dữ liệu tối thiểu cần dùng sau khi transaction đọc DB kết thúc.
 */
public class WordPreviewContext {

    private final String originalFilename;
    private final String storageObjectName;

    public WordPreviewContext(String originalFilename, String storageObjectName) {
        this.originalFilename = originalFilename;
        this.storageObjectName = storageObjectName;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getStorageObjectName() {
        return storageObjectName;
    }
}
