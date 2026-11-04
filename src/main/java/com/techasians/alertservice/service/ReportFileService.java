package com.techasians.alertservice.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.Closeable;
import java.nio.file.Path;
import java.time.LocalDate;

public interface ReportFileService {
    StoredReportFile upload(MultipartFile file, LocalDate weekStartDate,
                            Long definitionId, Long instanceId, Integer versionNo);

    StoredReportFile uploadGenerated(Path file, String originalFilename,
                                     LocalDate weekStartDate, Long definitionId,
                                     Long instanceId, Integer versionNo);

    Resource resolveForDownload(String objectName);

    void deleteIfExists(String objectName);

    TemporaryReportFile downloadToTemporaryFile(String objectName);

    class TemporaryReportFile implements Closeable {
        private final Path path;

        public TemporaryReportFile(Path path) {
            this.path = path;
        }

        public Path getPath() {
            return path;
        }

        @Override
        public void close() {
            try {
                java.nio.file.Files.deleteIfExists(path);
            } catch (java.io.IOException ignored) {
                // File tạm sẽ được hệ điều hành dọn sau; lỗi này không che lỗi nghiệp vụ chính.
            }
        }
    }
}
