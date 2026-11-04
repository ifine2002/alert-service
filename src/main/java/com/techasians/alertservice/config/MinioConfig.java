package com.techasians.alertservice.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Lớp cấu hình Spring - tạo Bean MinioClient để các class khác có thể dùng.
 *
 * Cách hoạt động:
 * - @Configuration: báo cho Spring biết đây là lớp cấu hình
 * - @Bean: Spring sẽ tạo đối tượng MinioClient và quản lý lifecycle (singleton)
 * - MinioProperties được inject tự động qua constructor
 *
 * Ví dụ sai (tạo MinioClient trực tiếp trong service):
 *   MinioClient client = MinioClient.builder()...build(); // Không nên, vi phạm DI
 *
 * Ví dụ đúng (dùng @Bean tập trung tại đây):
 *   Inject MinioUtils vào service, MinioUtils đã có sẵn client từ Bean này
 */
@Configuration
public class MinioConfig {

    private final MinioProperties properties;

    // Spring tự inject MinioProperties nhờ @Component + @ConfigurationProperties
    public MinioConfig(MinioProperties properties) {
        this.properties = properties;
    }

    /**
     * Tạo Bean MinioClient dựa trên cấu hình đọc từ application.yml.
     *
     * MinioClient.builder() nhận:
     *   - endpoint: URL server MinIO
     *   - credentials: access key + secret key
     */
    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }
}
