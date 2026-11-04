package com.techasians.alertservice.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Bind cấu hình app.report.* từ application.yml.
 * Tự động tạo các thư mục temp/input/output nếu chưa tồn tại khi ứng dụng khởi động.
 * <p>
 * Cấu hình mẫu trong application.yml:
 * <pre>
 * app:
 *   report:
 *     storage-root: /u01/os_csom/cos/exports
 *     temp-directory: temp
 *     input-directory: input
 *     output-directory: output
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "app.report")
public class ReportStorageProperties {

    // Đường dẫn gốc; tất cả thư mục con đều nằm dưới đây và trên cùng filesystem
    private String storageRoot = "/u01/os_csom/cos/exports";

    // Tên thư mục chứa file tạm trong lúc validate
    private String tempDirectory = "temp";

    // Tên thư mục chứa file input sau khi import thành công
    private String inputDirectory = "input";

    // Tên thư mục chứa file output sau khi generate thành công (Phase 5+)
    private String outputDirectory = "output";

    // Đường dẫn tuyệt đối đã resolve — chỉ tính một lần khi khởi tạo
    private Path storageRootPath;
    private Path tempPath;
    private Path inputPath;
    private Path outputPath;

    /**
     * Normalize các đường dẫn và tạo thư mục nếu chưa tồn tại.
     * Đảm bảo mọi thư mục con đều nằm dưới storageRoot (chống path traversal ở tầng cấu hình).
     */
    @PostConstruct
    public void init() {
        // Normalize đường dẫn root
        this.storageRootPath = Paths.get(storageRoot).toAbsolutePath().normalize();

        // Resolve và validate từng thư mục con
        this.tempPath = resolveAndValidate(tempDirectory, "temp-directory");
        this.inputPath = resolveAndValidate(inputDirectory, "input-directory");
        this.outputPath = resolveAndValidate(outputDirectory, "output-directory");

        // Tạo thư mục nếu chưa tồn tại
        createIfNotExists(storageRootPath, "storage-root");
        createIfNotExists(tempPath, "temp-directory");
        createIfNotExists(inputPath, "input-directory");
        createIfNotExists(outputPath, "output-directory");
    }

    private Path resolveAndValidate(String subDirectory, String configKey) {
        Path resolved = storageRootPath.resolve(subDirectory).normalize();
        // Đảm bảo thư mục con thực sự nằm dưới storageRoot
        if (!resolved.startsWith(storageRootPath)) {
            throw new IllegalStateException(
                    "Cấu hình '" + configKey + "' thoát khỏi storage root: " + resolved
            );
        }
        return resolved;
    }

    private void createIfNotExists(Path path, String configKey) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Không thể tạo thư mục '" + configKey + "': " + path, e
            );
        }
    }

    // ---- Getter ----

    public String getStorageRoot() {
        return storageRoot;
    }

    public void setStorageRoot(String storageRoot) {
        this.storageRoot = storageRoot;
    }

    public String getTempDirectory() {
        return tempDirectory;
    }

    public void setTempDirectory(String tempDirectory) {
        this.tempDirectory = tempDirectory;
    }

    public String getInputDirectory() {
        return inputDirectory;
    }

    public void setInputDirectory(String inputDirectory) {
        this.inputDirectory = inputDirectory;
    }

    public String getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    // Trả đường dẫn đã resolve — dùng khi service cần build path thực tế

    public Path getStorageRootPath() {
        return storageRootPath;
    }

    public Path getTempPath() {
        return tempPath;
    }

    public Path getInputPath() {
        return inputPath;
    }

    public Path getOutputPath() {
        return outputPath;
    }
}
