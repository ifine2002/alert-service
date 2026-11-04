package com.techasians.alertservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Lớp này đọc toàn bộ cấu hình MinIO từ file application.yml,
 * dưới prefix "vcx.minio".
 *
 * Ví dụ cấu hình trong application.yml:
 *
 *   vcx:
 *     minio:
 *       endpoint: http://localhost:9000
 *       access-key: minio-app-key-001
 *       secret-key: minio-app-secret-001
 *       bucket: report
 *       auto-create-bucket: true
 *
 * Cách hoạt động:
 * - Spring Boot tự động tìm các field trong lớp này
 * - Gán giá trị từ file yml vào tương ứng
 * - Ví dụ: "access-key" trong yml → accessKey trong Java (kebab-case → camelCase)
 */
@Component
@ConfigurationProperties(prefix = "vcx.minio")
public class MinioProperties {

    // URL kết nối tới MinIO server, ví dụ: http://localhost:9000
    private String endpoint;

    // Access key (tên đăng nhập) của MinIO
    private String accessKey;

    // Secret key (mật khẩu) của MinIO
    private String secretKey;

    // Bucket mặc định sẽ dùng khi không truyền bucket cụ thể
    private String bucket;

    // Nếu true: tự động tạo bucket khi ứng dụng khởi động
    private boolean autoCreateBucket = false;

    // Thời gian hết hạn của presigned URL dùng để TẢI XUỐNG (GET)
    // Mặc định: 10 phút
    private Duration presignedGetExpiry = Duration.ofMinutes(10L);

    // Thời gian hết hạn của presigned URL dùng để TẢI LÊN (PUT)
    // Mặc định: 15 phút
    private Duration presignedPutExpiry = Duration.ofMinutes(15L);

    // ========== Getter & Setter ==========
    // Cần đủ getter/setter để Spring Boot có thể đọc và ghi giá trị từ yml

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public boolean isAutoCreateBucket() {
        return autoCreateBucket;
    }

    public void setAutoCreateBucket(boolean autoCreateBucket) {
        this.autoCreateBucket = autoCreateBucket;
    }

    public Duration getPresignedGetExpiry() {
        return presignedGetExpiry;
    }

    public void setPresignedGetExpiry(Duration presignedGetExpiry) {
        this.presignedGetExpiry = presignedGetExpiry;
    }

    public Duration getPresignedPutExpiry() {
        return presignedPutExpiry;
    }

    public void setPresignedPutExpiry(Duration presignedPutExpiry) {
        this.presignedPutExpiry = presignedPutExpiry;
    }
}
