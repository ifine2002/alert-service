package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.service.ReportFileService;
import com.techasians.alertservice.service.StoredReportFile;
import com.techasians.alertservice.util.MinioUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@ConditionalOnProperty(
        name = "report.export.storage-type",
        havingValue = "minio",
        matchIfMissing = true
)
public class ReportFileServiceImpl implements ReportFileService {

    private static final Logger log =
            LoggerFactory.getLogger(ReportFileServiceImpl.class);

    /**
     * Thư mục lưu file input trên MinIO.
     *
     * Ví dụ:
     * rpt_week_vtt/input/2026-09-21/report_v1_20260922_221530123.xlsx
     */
    private static final String DOCUMENT_INPUT_PATH =
            "rpt_week_vtt/input/";

    /**
     * Thư mục lưu file output trên MinIO.
     *
     * Ví dụ:
     * rpt_week_vtt/output/2026-09-21/report_v1_20260922_221530123.xlsx
     */
    private static final String DOCUMENT_OUTPUT_PATH =
            "rpt_week_vtt/output/";

    /**
     * Format thời gian được gắn vào tên file.
     *
     * Ví dụ:
     * 20260922_221530123
     */
    private static final DateTimeFormatter FILE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS");

    private final MinioUtils minioUtils;

    public ReportFileServiceImpl(MinioUtils minioUtils) {
        this.minioUtils = minioUtils;
    }

    /**
     * Upload file input lên MinIO.
     *
     * Cấu trúc:
     *
     * rpt_week_vtt/input/{weekStartDate}/{fileName}_v{version}_{timestamp}.{extension}
     *
     * Ví dụ:
     *
     * rpt_week_vtt/input/2026-09-21/BaoCaoTuan_v3_20260922_221530123.xlsx
     */
    @Override
    public StoredReportFile upload(
            MultipartFile file,
            LocalDate weekStartDate,
            Long definitionId,
            Long instanceId,
            Integer versionNo
    ) {

        String originalFilename =
                sanitizeFilename(file.getOriginalFilename());

        String objectName = buildObjectName(
                DOCUMENT_INPUT_PATH,
                weekStartDate,
                originalFilename,
                versionNo
        );

        Path temporaryFile = null;

        try {
            temporaryFile = Files.createTempFile(
                    "report-upload-",
                    extractExtension(originalFilename)
            );

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            /*
             * Vừa đọc file upload,
             * vừa tính SHA-256,
             * vừa copy sang file tạm.
             */
            try (
                    InputStream input =
                            new DigestInputStream(
                                    file.getInputStream(),
                                    digest
                            )
            ) {
                Files.copy(
                        input,
                        temporaryFile,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
            }

            long fileSize =
                    Files.size(temporaryFile);

            String sha256 =
                    toHex(digest.digest());

            /*
             * Upload file tạm lên MinIO.
             */
            try (
                    InputStream input =
                            Files.newInputStream(temporaryFile)
            ) {
                minioUtils.upload(
                        objectName,
                        input,
                        fileSize,
                        contentType(file)
                );
            }

            return new StoredReportFile(
                    originalFilename,
                    objectName,
                    fileSize,
                    sha256
            );

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Không thể chuẩn bị file để upload MinIO",
                    e
            );

        } catch (NoSuchAlgorithmException e) {

            throw new IllegalStateException(
                    "SHA-256 không khả dụng trên JVM này",
                    e
            );

        } finally {

            deleteTemporaryFile(temporaryFile);
        }
    }

    /**
     * Upload file output đã generate lên MinIO.
     *
     * Cấu trúc:
     *
     * rpt_week_vtt/output/{weekStartDate}/{fileName}_v{version}_{timestamp}.{extension}
     *
     * Ví dụ:
     *
     * rpt_week_vtt/output/2026-09-21/BaoCaoTuan_v3_20260922_221530123.xlsx
     */
    @Override
    public StoredReportFile uploadGenerated(
            Path file,
            String originalFilename,
            LocalDate weekStartDate,
            Long definitionId,
            Long instanceId,
            Integer versionNo
    ) {

        String safeFilename =
                sanitizeFilename(originalFilename);

        String objectName = buildObjectName(
                DOCUMENT_OUTPUT_PATH,
                weekStartDate,
                safeFilename,
                versionNo
        );

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            /*
             * Đọc file để tính SHA-256.
             */
            try (
                    InputStream input =
                            new DigestInputStream(
                                    Files.newInputStream(file),
                                    digest
                            )
            ) {

                byte[] buffer = new byte[8192];

                while (input.read(buffer) != -1) {
                    // Chỉ đọc file để tính SHA-256.
                }
            }

            long fileSize =
                    Files.size(file);

            /*
             * Upload file output lên MinIO.
             */
            try (
                    InputStream input =
                            Files.newInputStream(file)
            ) {

                minioUtils.upload(
                        objectName,
                        input,
                        fileSize,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                );
            }

            return new StoredReportFile(
                    safeFilename,
                    objectName,
                    fileSize,
                    toHex(digest.digest())
            );

        } catch (
                IOException |
                NoSuchAlgorithmException exception
        ) {

            throw new IllegalStateException(
                    "Không thể upload file output lên MinIO",
                    exception
            );
        }
    }

    /**
     * Lấy file từ MinIO để download.
     */
    @Override
    public Resource resolveForDownload(
            String objectName
    ) {

        if (!minioUtils.exists(objectName)) {

            throw new IllegalStateException(
                    "Object MinIO không tồn tại: "
                            + objectName
            );
        }

        return new InputStreamResource(
                minioUtils.download(objectName)
        );
    }

    /**
     * Xóa object trên MinIO nếu tồn tại.
     */
    @Override
    public void deleteIfExists(
            String objectName
    ) {

        if (
                objectName == null ||
                        objectName.trim().isEmpty()
        ) {
            return;
        }

        try {

            if (minioUtils.exists(objectName)) {
                minioUtils.delete(objectName);
            }

        } catch (RuntimeException exception) {

            log.warn(
                    "Không thể xóa object MinIO {}: {}",
                    objectName,
                    exception.getMessage()
            );
        }
    }

    /**
     * Download object MinIO xuống file tạm
     * phục vụ chức năng preview.
     */
    @Override
    public TemporaryReportFile downloadToTemporaryFile(
            String objectName
    ) {

        Path temporaryFile = null;

        try {

            temporaryFile = Files.createTempFile(
                    "report-preview-",
                    extractExtension(objectName)
            );

            try (
                    InputStream input =
                            minioUtils.download(objectName)
            ) {

                Files.copy(
                        input,
                        temporaryFile,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
            }

            return new TemporaryReportFile(
                    temporaryFile
            );

        } catch (
                IOException |
                RuntimeException exception
        ) {

            deleteTemporaryFile(
                    temporaryFile
            );

            throw new IllegalStateException(
                    "Không thể tải object MinIO để preview: "
                            + objectName,
                    exception
            );
        }
    }

    /**
     * Tạo object name trên MinIO.
     *
     * Format:
     *
     * {dirPath}/{weekStartDate}/{fileName}_v{version}_{timestamp}.{extension}
     *
     * Ví dụ:
     *
     * rpt_week_vtt/input/
     * 2026-09-21/
     * BaoCaoTuan_v3_20260922_221530123.xlsx
     */
    private String buildObjectName(
            String dirPath,
            LocalDate weekStartDate,
            String fileName,
            Integer versionNo
    ) {

        String cleanDirPath =
                dirPath.startsWith("/")
                        ? dirPath.substring(1)
                        : dirPath;

        if (!cleanDirPath.endsWith("/")) {
            cleanDirPath += "/";
        }

        String safeFileName =
                sanitizeFilename(fileName);

        String extension =
                extractExtension(safeFileName);

        String fileNameWithoutExtension =
                removeExtension(safeFileName);

        String timestamp =
                LocalDateTime.now()
                        .format(FILE_TIME_FORMAT);

        String version =
                versionNo == null
                        ? "0"
                        : versionNo.toString();

        String generatedFileName =
                fileNameWithoutExtension
                        + "_v"
                        + version
                        + "_"
                        + timestamp
                        + extension;

        return cleanDirPath
                + weekStartDate
                + "/"
                + generatedFileName;
    }

    /**
     * Loại bỏ path khỏi filename,
     * chỉ giữ lại tên file.
     */
    private String sanitizeFilename(
            String filename
    ) {

        if (
                filename == null ||
                        filename.trim().isEmpty()
        ) {
            return "unknown";
        }

        String normalized =
                filename.replace('\\', '/');

        return normalized.substring(
                normalized.lastIndexOf('/') + 1
        );
    }

    /**
     * Lấy extension của file.
     *
     * Ví dụ:
     * report.xlsx -> .xlsx
     */
    private String extractExtension(
            String filename
    ) {

        if (filename == null) {
            return "";
        }

        int dotIndex =
                filename.lastIndexOf('.');

        return dotIndex >= 0
                ? filename.substring(dotIndex).toLowerCase()
                : "";
    }

    /**
     * Lấy tên file không bao gồm extension.
     *
     * Ví dụ:
     * report.xlsx -> report
     */
    private String removeExtension(
            String filename
    ) {

        if (
                filename == null ||
                        filename.trim().isEmpty()
        ) {
            return "unknown";
        }

        int dotIndex =
                filename.lastIndexOf('.');

        return dotIndex > 0
                ? filename.substring(0, dotIndex)
                : filename;
    }

    /**
     * Lấy content-type của MultipartFile.
     */
    private String contentType(
            MultipartFile file
    ) {

        return file.getContentType() == null
                ? "application/octet-stream"
                : file.getContentType();
    }

    /**
     * Chuyển SHA-256 byte array sang hex string.
     */
    private String toHex(
            byte[] bytes
    ) {

        StringBuilder result =
                new StringBuilder();

        for (byte value : bytes) {
            result.append(
                    String.format(
                            "%02x",
                            value
                    )
            );
        }

        return result.toString();
    }

    /**
     * Xóa file tạm sau khi upload/download xong.
     */
    private void deleteTemporaryFile(
            Path path
    ) {

        if (path == null) {
            return;
        }

        try {

            Files.deleteIfExists(path);

        } catch (IOException exception) {

            log.warn(
                    "Không thể xóa file tạm {}: {}",
                    path,
                    exception.getMessage()
            );
        }
    }
}