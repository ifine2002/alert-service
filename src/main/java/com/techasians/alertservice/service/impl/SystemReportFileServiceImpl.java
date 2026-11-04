package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.service.ReportFileService;
import com.techasians.alertservice.service.StoredReportFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@ConditionalOnProperty(name = "report.export.storage-type", havingValue = "system")
public class SystemReportFileServiceImpl implements ReportFileService {
    private static final Logger log = LoggerFactory.getLogger(SystemReportFileServiceImpl.class);
    private static final String BASE_DIRECTORY = "rpt_week_vtt";
    private static final String TEMP_DIRECTORY = "temp";
    private static final String INPUT_DIRECTORY = "input";
    private static final String OUTPUT_DIRECTORY = "output";
    private static final DateTimeFormatter FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS");

    @Value("${report.export.base-path:/u01/os_csom/cos/exports}")
    private String storageRoot;

    private Path storageRootPath;
    private Path basePath;
    private Path tempPath;
    private Path inputPath;
    private Path outputPath;

    @PostConstruct
    public void init() {
        storageRootPath = Paths.get(storageRoot).toAbsolutePath().normalize();
        basePath = storageRootPath.resolve(BASE_DIRECTORY).normalize();
        tempPath = basePath.resolve(TEMP_DIRECTORY).normalize();
        inputPath = basePath.resolve(INPUT_DIRECTORY).normalize();
        outputPath = basePath.resolve(OUTPUT_DIRECTORY).normalize();
        assertUnderStorageRoot(basePath);
        assertUnderStorageRoot(tempPath);
        assertUnderStorageRoot(inputPath);
        assertUnderStorageRoot(outputPath);
        createIfNotExists(storageRootPath, "storage-root");
        createIfNotExists(basePath, BASE_DIRECTORY);
        createIfNotExists(tempPath, TEMP_DIRECTORY);
        createIfNotExists(inputPath, INPUT_DIRECTORY);
        createIfNotExists(outputPath, OUTPUT_DIRECTORY);
    }

    @Override
    public StoredReportFile upload(MultipartFile file, LocalDate weekStartDate,
                                   Long definitionId, Long instanceId, Integer versionNo) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("File upload không được để trống");
        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        Path destination = buildDestination(inputPath, weekStartDate, originalFilename, versionNo);
        try {
            return store(file.getInputStream(), originalFilename, destination);
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể đọc file upload", exception);
        }
    }

    @Override
    public StoredReportFile uploadGenerated(Path file, String originalFilename,
                                            LocalDate weekStartDate, Long definitionId,
                                            Long instanceId, Integer versionNo) {
        if (file == null || !Files.isRegularFile(file)) throw new IllegalArgumentException("File output không tồn tại: " + file);
        String safeFilename = sanitizeFilename(originalFilename);
        Path destination = buildDestination(outputPath, weekStartDate, safeFilename, versionNo);
        try {
            return store(Files.newInputStream(file), safeFilename, destination);
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể đọc file output: " + file, exception);
        }
    }

    private StoredReportFile store(InputStream source, String originalFilename, Path destination) {
        Path temporaryFile = null;
        try (InputStream input = source) {
            temporaryFile = Files.createTempFile(tempPath, "report-", extractExtension(originalFilename));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream digestInput = new DigestInputStream(input, digest)) {
                Files.copy(digestInput, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
            }
            long fileSize = Files.size(temporaryFile);
            String sha256 = toHex(digest.digest());
            moveToDestination(temporaryFile, destination);
            temporaryFile = null;
            return new StoredReportFile(originalFilename, toRelativePath(destination), fileSize, sha256);
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể lưu file lên system server: " + destination, exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 không khả dụng trên JVM này", exception);
        } finally {
            deleteTemporaryFile(temporaryFile);
        }
    }

    @Override
    public Resource resolveForDownload(String objectName) {
        Path file = resolveStoredFile(objectName);
        if (!Files.isRegularFile(file)) throw new IllegalStateException("File không tồn tại: " + objectName);
        return new FileSystemResource(file);
    }

    @Override
    public void deleteIfExists(String objectName) {
        if (objectName == null || objectName.trim().isEmpty()) return;
        try {
            Files.deleteIfExists(resolveStoredFile(objectName));
        } catch (IOException | RuntimeException exception) {
            log.warn("Không thể xóa file {}: {}", objectName, exception.getMessage());
        }
    }

    @Override
    public TemporaryReportFile downloadToTemporaryFile(String objectName) {
        Path source = resolveStoredFile(objectName);
        if (!Files.isRegularFile(source)) throw new IllegalStateException("File không tồn tại: " + objectName);
        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile(tempPath, "report-preview-", extractExtension(objectName));
            Files.copy(source, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
            return new TemporaryReportFile(temporaryFile);
        } catch (IOException exception) {
            deleteTemporaryFile(temporaryFile);
            throw new IllegalStateException("Không thể tạo file preview: " + objectName, exception);
        }
    }

    private Path buildDestination(Path directory, LocalDate weekStartDate, String filename, Integer versionNo) {
        if (weekStartDate == null) throw new IllegalArgumentException("Ngày bắt đầu tuần không được để trống");
        String safeName = sanitizeFilename(filename);
        String version = versionNo == null ? "0" : versionNo.toString();
        String physicalName = removeExtension(safeName) + "_v" + version + "_"
                + LocalDateTime.now().format(FILE_TIME_FORMAT) + extractExtension(safeName);
        Path weekDirectory = directory.resolve(weekStartDate.toString()).normalize();
        assertUnderStorageRoot(weekDirectory);
        createIfNotExists(weekDirectory, weekStartDate.toString());
        Path destination = weekDirectory.resolve(physicalName).normalize();
        assertUnderStorageRoot(destination);
        return destination;
    }

    private Path resolveStoredFile(String objectName) {
        if (objectName == null || objectName.trim().isEmpty()) throw new IllegalArgumentException("Đường dẫn file không được để trống");
        Path file = storageRootPath.resolve(objectName).normalize();
        assertUnderStorageRoot(file);
        return file;
    }

    private void moveToDestination(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination);
        }
    }

    private String toRelativePath(Path file) {
        return storageRootPath.relativize(file).toString().replace('\\', '/');
    }

    private void assertUnderStorageRoot(Path path) {
        if (!path.toAbsolutePath().normalize().startsWith(storageRootPath))
            throw new IllegalArgumentException("Đường dẫn nằm ngoài storage root: " + path);
    }

    private void createIfNotExists(Path directory, String name) {
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể tạo thư mục " + name + ": " + directory, exception);
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) return "unknown";
        String normalized = filename.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private String extractExtension(String filename) {
        if (filename == null) return "";
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex >= 0 ? filename.substring(dotIndex).toLowerCase() : "";
    }

    private String removeExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    }

    private String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format("%02x", value));
        return result.toString();
    }

    private void deleteTemporaryFile(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn("Không thể xóa file tạm {}: {}", path, exception.getMessage());
        }
    }
}
