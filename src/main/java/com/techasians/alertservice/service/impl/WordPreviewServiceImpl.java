package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.dto.ReportPreviewDto;
import com.techasians.alertservice.service.ReportFileService;
import com.techasians.alertservice.service.WordPreviewService;
import com.techasians.alertservice.service.WordToPdfConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * Triển khai preview file Word → PDF cho report instance.
 * <p>
 * Luồng:
 * 1. Đọc và kiểm tra metadata trong transaction ngắn tại WordPreviewQueryService.
 * 2. Resolve đường dẫn file vật lý an toàn.
 * 3. Aspose chuyển đổi Word → PDF trong bộ nhớ ngoài transaction.
 * 4. Trả DTO cho controller.
 */
@Service
public class WordPreviewServiceImpl implements WordPreviewService {

    private static final Logger log = LoggerFactory.getLogger(WordPreviewServiceImpl.class);

    private final WordPreviewQueryService wordPreviewQueryService;
    private final ReportFileService reportFileService;
    private final WordToPdfConverter wordToPdfConverter;

    public WordPreviewServiceImpl(WordPreviewQueryService wordPreviewQueryService,
                                  ReportFileService reportFileService,
                                  WordToPdfConverter wordToPdfConverter) {
        this.wordPreviewQueryService = wordPreviewQueryService;
        this.reportFileService = reportFileService;
        this.wordToPdfConverter = wordToPdfConverter;
    }

    @Override
    public ReportPreviewDto previewInstance(Long instanceId) {
        WordPreviewContext previewContext =
                wordPreviewQueryService.loadPreviewContext(instanceId);

        long totalStartTime = System.nanoTime();
        String storageObjectName = previewContext.getStorageObjectName();
        byte[] pdfBytes;

        log.info("Preview instance={} bắt đầu tải file tạm, objectName={}",
                instanceId, storageObjectName);
        long downloadStartTime = System.nanoTime();
        try (ReportFileService.TemporaryReportFile temporaryFile =
                     reportFileService.downloadToTemporaryFile(storageObjectName)) {
            long downloadDurationMs = (System.nanoTime() - downloadStartTime) / 1_000_000;
            log.info("Preview instance={} tải file tạm xong trong {}ms, path={}",
                    instanceId, downloadDurationMs, temporaryFile.getPath());

            log.info("Preview instance={} bắt đầu chuyển Word sang PDF", instanceId);
            long convertStartTime = System.nanoTime();
            pdfBytes = wordToPdfConverter.convert(temporaryFile.getPath());
            long convertDurationMs = (System.nanoTime() - convertStartTime) / 1_000_000;
            log.info("Preview instance={} chuyển Word sang PDF xong trong {}ms, pdfSize={} bytes",
                    instanceId, convertDurationMs, pdfBytes.length);
        }

        long totalDurationMs = (System.nanoTime() - totalStartTime) / 1_000_000;
        log.info("Preview instance={} hoàn tất download và convert trong {}ms",
                instanceId, totalDurationMs);

        String previewFileName = buildPreviewFileName(previewContext.getOriginalFilename());
        return new ReportPreviewDto(
                new ByteArrayResource(pdfBytes),
                previewFileName,
                pdfBytes.length
        );
    }

    /**
     * Tạo tên file PDF hiển thị từ tên Word gốc.
     * Ví dụ: bao-cao-tuan.docx → bao-cao-tuan-preview.pdf
     */
    private String buildPreviewFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isEmpty()) {
            return "preview.pdf";
        }

        String nameWithoutExtension = originalFilename;
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            nameWithoutExtension = originalFilename.substring(0, dotIndex);
        }
        return nameWithoutExtension + "-preview.pdf";
    }
}

