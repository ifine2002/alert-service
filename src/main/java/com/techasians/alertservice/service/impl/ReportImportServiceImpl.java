package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.dto.ReportImportResult;
import com.techasians.alertservice.entity.RptDefinition;
import com.techasians.alertservice.enums.RptDirection;
import com.techasians.alertservice.enums.RptNodeType;
import com.techasians.alertservice.enums.RptSourceMode;
import com.techasians.alertservice.exception.InvalidReportStateException;
import com.techasians.alertservice.exception.ResourceNotFoundException;
import com.techasians.alertservice.repository.RptDefinitionRepository;
import com.techasians.alertservice.service.ReportFileService;
import com.techasians.alertservice.service.ReportImportService;
import com.techasians.alertservice.service.ReportValidationService;
import com.techasians.alertservice.service.StoredReportFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/**
 * Orchestration luồng import/re-import file input báo cáo tuần.
 * <p>
 * Nguyên tắc:
 * - File I/O và validate xảy ra NGOÀI transaction DB.
 * - Transaction DB nằm trong {@link ReportImportTransactionService#commit} — class riêng
 *   để Spring AOP proxy hoạt động đúng.
 * - Nếu transaction rollback sau khi file mới đã promote: xóa file mới để bù trừ.
 * - File cũ và metadata cũ không bị đụng đến cho đến khi commit DB thành công.
 */
@Service
public class ReportImportServiceImpl implements ReportImportService {

    private static final Logger log = LoggerFactory.getLogger(ReportImportServiceImpl.class);

    private final RptDefinitionRepository definitionRepository;
    private final ReportValidationService validationService;
    private final ReportFileService reportFileService;
    private final ReportImportTransactionService importTxService;
    private final OutputGenerationCoordinator outputGenerationCoordinator;

    public ReportImportServiceImpl(RptDefinitionRepository definitionRepository,
                                   ReportValidationService validationService,
                                   ReportFileService reportFileService,
                                   ReportImportTransactionService importTxService,
                                   OutputGenerationCoordinator outputGenerationCoordinator) {
        this.definitionRepository = definitionRepository;
        this.validationService = validationService;
        this.reportFileService = reportFileService;
        this.importTxService = importTxService;
        this.outputGenerationCoordinator = outputGenerationCoordinator;
    }

    @Override
    public ReportImportResult importFile(Long definitionId,
                           LocalDate weekStartDate,
                           MultipartFile file,
                           String actor) {
        long totalStart = System.nanoTime();
        log.info("[importFile] BẮT ĐẦU — definitionId={}, weekStartDate={}, actor={}, fileSize={}bytes",
                definitionId, weekStartDate, actor, file.getSize());

        // Bước 1: Validate đầu vào
        long step1Start = System.nanoTime();
        validationService.validateWeekStartDate(weekStartDate);
        validationService.validateActor(actor);
        RptDefinition definition = loadAndValidateDefinition(definitionId);
        byte[] errorFile = validationService.validate(
                file,
                definition.getAllowedExtension(),
                definitionId,
                weekStartDate
        );
        if (errorFile != null) {
            return new ReportImportResult().setErrorFile(errorFile);
        }
        log.info("[importFile] Bước 1 - Validate xong: {}ms", elapsed(step1Start));

        // Bước 2: Prepare (tạo bản ghi instance/version trong DB)
        long step2Start = System.nanoTime();
        ReportImportTransactionService.ImportAllocation allocation =
                importTxService.prepare(definitionId, weekStartDate, actor);
        log.info("[importFile] Bước 2 - Prepare DB xong: {}ms (instanceId={})",
                elapsed(step2Start), allocation.getInstanceId());

        StoredReportFile storedFile = null;
        boolean committed = false;
        try {
            // Bước 3: Upload file lên MinIO
            long step3Start = System.nanoTime();
            storedFile = reportFileService.upload(file, weekStartDate, definitionId,
                    allocation.getInstanceId(), allocation.getVersionNo());
            log.info("[importFile] Bước 3 - Upload MinIO xong: {}ms (object={})",
                    elapsed(step3Start), storedFile.getObjectName());

            // Bước 4: Commit transaction DB
            long step4Start = System.nanoTime();
            importTxService.commit(allocation, actor, storedFile);
            committed = true;
            log.info("[importFile] Bước 4 - Commit DB xong: {}ms", elapsed(step4Start));

        } finally {
            if (!committed) {
                if (storedFile != null) {
                    log.error("Commit DB thất bại, xóa object MinIO mới: {}", storedFile.getObjectName());
                    reportFileService.deleteIfExists(storedFile.getObjectName());
                }
                importTxService.markError(allocation.getInstanceId(), actor);
            }
        }

        // Bước 5: Prepare output đồng bộ, sau đó publish event chạy generation bất đồng bộ
        long step5Start = System.nanoTime();
        outputGenerationCoordinator.prepareAndPublish(definitionId, weekStartDate, "system");
        log.info("[importFile] Bước 5 - Prepare và publish output xong: {}ms", elapsed(step5Start));

        log.info("[importFile] HOÀN TẤT — serviceTotalMs={}", elapsed(totalStart));
        return new ReportImportResult();
    }

    /** Tính thời gian đã trôi qua từ mốc startNano, trả về millisecond. */
    private static long elapsed(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000;
    }

    // ----------------------------------------------------------------
    // Private helper
    // ----------------------------------------------------------------

    /**
     * Load definition và kiểm tra đủ điều kiện cho import:
     * - Tồn tại
     * - Direction = INPUT
     * - NodeType = FILE
     * - SourceMode = IMPORT
     * - Status = active (1)
     */
    private RptDefinition loadAndValidateDefinition(Long definitionId) {
        RptDefinition definition = definitionRepository.findById(definitionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Definition không tồn tại: id=" + definitionId
                ));

        if (definition.getDirection() != RptDirection.INPUT) {
            throw new InvalidReportStateException("Definition không phải loại đầu vào (INPUT)");
        }
        if (definition.getNodeType() != RptNodeType.FILE) {
            throw new InvalidReportStateException("Definition không phải loại file đơn (FILE)");
        }
        if (definition.getSourceMode() != RptSourceMode.IMPORT) {
            throw new InvalidReportStateException("Definition không hỗ trợ import thủ công");
        }
        if (!definition.isActive()) {
            throw new InvalidReportStateException("Definition không còn được sử dụng");
        }

        return definition;
    }
}
