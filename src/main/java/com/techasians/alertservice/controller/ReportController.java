package com.techasians.alertservice.controller;

import com.techasians.alertservice.dto.ApiResponse;
import com.techasians.alertservice.dto.ReportActionHistoryResponse;
import com.techasians.alertservice.dto.ReportDeleteRequest;
import com.techasians.alertservice.dto.ReportDocumentSummaryResponse;
import com.techasians.alertservice.dto.ReportDownloadDto;
import com.techasians.alertservice.dto.ReportInputNodeResponse;
import com.techasians.alertservice.dto.ReportInputSearchRequest;
import com.techasians.alertservice.dto.ReportImportResult;
import com.techasians.alertservice.dto.ReportOutputResponse;
import com.techasians.alertservice.dto.ReportFileVersionResponse;
import com.techasians.alertservice.dto.ReportPreviewDto;
import com.techasians.alertservice.dto.ReportRejectRequest;
import com.techasians.alertservice.enums.RptAuditAction;
import com.techasians.alertservice.enums.RptDirection;
import com.techasians.alertservice.enums.RptInstanceStatus;
import com.techasians.alertservice.service.ReportDeleteService;
import com.techasians.alertservice.service.ReportImportService;
import com.techasians.alertservice.service.ReportQueryService;
import com.techasians.alertservice.service.ReportRejectService;
import com.techasians.alertservice.service.WordPreviewService;
import com.techasians.alertservice.service.impl.OutputGenerationCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * Các API quản lý báo cáo tuần.
 * <p>
 * Phase 1: GET /inputs, /outputs, /documents
 * Phase 2: POST /inputs/{definitionId}/import
 *          GET  /instances/{instanceId}/download
 *          POST /instances/{instanceId}/reject
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    private final ReportQueryService reportQueryService;
    private final ReportImportService reportImportService;
    private final ReportRejectService reportRejectService;
    private final ReportDeleteService reportDeleteService;
    private final WordPreviewService wordPreviewService;
    private final OutputGenerationCoordinator outputGenerationCoordinator;

    public ReportController(ReportQueryService reportQueryService,
                            ReportImportService reportImportService,
                            ReportRejectService reportRejectService,
                            ReportDeleteService reportDeleteService,
                            WordPreviewService wordPreviewService,
                            OutputGenerationCoordinator outputGenerationCoordinator) {
        this.reportQueryService = reportQueryService;
        this.reportImportService = reportImportService;
        this.reportRejectService = reportRejectService;
        this.reportDeleteService = reportDeleteService;
        this.wordPreviewService = wordPreviewService;
        this.outputGenerationCoordinator = outputGenerationCoordinator;
    }

    // =====================================================================
    // Phase 1: List API
    // =====================================================================

    @GetMapping("/inputs")
    public ApiResponse<List<ReportInputNodeResponse>> listInputs(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<RptInstanceStatus> statuses) {
        validateWeekStartDate(weekStartDate);
        ReportInputSearchRequest request = new ReportInputSearchRequest()
                .setWeekStartDate(weekStartDate)
                .setKeyword(keyword)
                .setStatuses(statuses);
        return ApiResponse.success("Lấy danh sách báo cáo đầu vào thành công",
                reportQueryService.listInputs(request));
    }

    @GetMapping("/outputs")
    public ApiResponse<List<ReportOutputResponse>> listOutputs(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate) {
        validateWeekStartDate(weekStartDate);
        return ApiResponse.success("Lấy danh sách báo cáo đầu ra thành công",
                reportQueryService.listOutputs(weekStartDate));
    }

    @GetMapping("/documents")
    public ApiResponse<List<ReportDocumentSummaryResponse>> listDocuments(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate,
            @RequestParam(required = false) RptDirection direction) {
        validateWeekStartDate(weekStartDate);
        return ApiResponse.success("Lấy danh sách document báo cáo thành công",
                reportQueryService.listDocuments(weekStartDate, direction));
    }

    @GetMapping("/action-history")
    public ApiResponse<Page<ReportActionHistoryResponse>> searchActionHistory(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String reportName,
            @RequestParam(required = false) List<RptAuditAction> actions,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success("Lấy lịch sử tác động báo cáo thành công",
                reportQueryService.searchActionHistory(
                        fromDate, toDate, reportName, actions, page, size));
    }

    // =====================================================================
    // Phase 2: Import / Download / Reject
    // =====================================================================

    /**
     * Import hoặc re-import file input cho một tuần báo cáo.
     * Backend tự phân loại IMPORT/REIMPORT dựa trên việc đã có rpt_file chưa.
     * <p>
     * Request: multipart/form-data với các field:
     * - weekStartDate: ngày Thứ Hai (ví dụ: 2026-03-23)
     * - actor: tên người thực hiện (tạm thời, Phase 2 chưa dùng authentication)
     * - file: file upload (.docx hoặc .xlsx)
     */
    @PostMapping(value = "/inputs/{definitionId}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ReportImportResult>> importFile(
            @PathVariable Long definitionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate,
            @RequestParam String actor,
            @RequestParam("file") MultipartFile file) {
        long requestStart = System.nanoTime();
        try {
            ReportImportResult result = reportImportService.importFile(definitionId, weekStartDate, file, actor);
            if (result.getErrorFile() != null) {
                return ResponseEntity.ok(ApiResponse.success("Import có lỗi, kiểm tra file", result));
            }
            return ResponseEntity.ok(ApiResponse.success("Import báo cáo thành công", result));
        } finally {
            log.info("[import-api] HOÀN TẤT — definitionId={}, weekStartDate={}, apiTotalMs={}",
                    definitionId, weekStartDate, elapsed(requestStart));
        }
    }

    @PostMapping("/outputs/generate")
    public ApiResponse<Void> generateOutputs(
            @RequestParam(required = false) List<Long> definitionIds,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate,
            @RequestParam(defaultValue = "system") String actor) {
        validateWeekStartDate(weekStartDate);
        outputGenerationCoordinator.prepareAndPublishOutputs(definitionIds, weekStartDate, actor);
        return ApiResponse.success("Đã bắt đầu generate báo cáo đầu ra", null);
    }

    /**
     * Download file hiện tại của một instance.
     * Trả binary stream với tên file gốc (UTF-8) trong Content-Disposition.
     * Không bọc trong ApiResponse — trả Resource trực tiếp.
     * <p>
     * Ghi audit DOWNLOAD sau khi resolve thành công.
     */
    @GetMapping("/instances/{instanceId}/download")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable Long instanceId,
            @RequestParam(required = false, defaultValue = "system") String actor) {

        return buildDownloadResponse(reportQueryService.downloadInstanceFile(instanceId, actor));
    }

    @GetMapping("/instances/{instanceId}/versions")
    public ApiResponse<List<ReportFileVersionResponse>> listFileVersions(@PathVariable Long instanceId) {
        return ApiResponse.success("Lấy lịch sử phiên bản file thành công",
                reportQueryService.listFileVersions(instanceId));
    }

    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<Resource> downloadFileVersion(
            @PathVariable Long fileId,
            @RequestParam(required = false, defaultValue = "system") String actor) {
        return buildDownloadResponse(reportQueryService.downloadFileVersion(fileId, actor));
    }

    private ResponseEntity<Resource> buildDownloadResponse(ReportDownloadDto downloadDto) {
        String encodedFilename = URLEncoder.encode(downloadDto.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(downloadDto.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFilename)
                .body(downloadDto.getResource());
    }

    /**
     * Từ chối file input đang ở trạng thái COMPLETED.
     * File vật lý được GIỮ NGUYÊN. Lý do được lưu vào rpt_instance.
     * <p>
     * Phase 2: chưa kiểm tra quyền/role.
     */
    @PostMapping("/instances/{instanceId}/reject")
    public ApiResponse<Void> rejectInput(
            @PathVariable Long instanceId,
            @Valid @RequestBody ReportRejectRequest request) {

        reportRejectService.rejectInput(instanceId, request.getReason(), request.getActor());
        return ApiResponse.success("Từ chối báo cáo thành công", null);
    }

    @DeleteMapping("/instances/{instanceId}/file")
    public ApiResponse<Void> deleteInputFile(
            @PathVariable Long instanceId,
            @Valid @RequestBody ReportDeleteRequest request) {
        reportDeleteService.deleteInputFile(instanceId, request.getActor(), request.getReason());
        return ApiResponse.success("Xóa file input thành công", null);
    }

    /**
     * Xem trước file Word hiện tại của instance dưới dạng PDF.
     * Chỉ hỗ trợ Word; Excel trả 415.
     * Mỗi request thực hiện một lần chuyển đổi, không cache kết quả.
     * <p>
     * Response: binary PDF với Content-Disposition: inline.
     * Frontend tự xác định trạng thái STALE từ API list output.
     */
    @GetMapping("/instances/{instanceId}/preview")
    public ResponseEntity<Resource> previewFile(@PathVariable Long instanceId) {

        ReportPreviewDto previewDto = wordPreviewService.previewInstance(instanceId);

        // Encode tên file UTF-8 theo RFC 5987
        String encodedFilename = URLEncoder.encode(previewDto.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename*=UTF-8''" + encodedFilename)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentLength(previewDto.getContentLength())
                .body(previewDto.getResource());
    }

    // =====================================================================
    // Private helpers
    // =====================================================================

    private void validateWeekStartDate(LocalDate date) {
        if (date.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("weekStartDate phải là ngày Thứ Hai");
        }
    }

    private static long elapsed(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000;
    }
}

