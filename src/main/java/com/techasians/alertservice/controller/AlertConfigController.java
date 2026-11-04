package com.techasians.alertservice.controller;

import com.techasians.alertservice.dto.AlertConfigRequest;
import com.techasians.alertservice.dto.AlertConfigResponse;
import com.techasians.alertservice.dto.AlertConfigDetailResponse;
import com.techasians.alertservice.dto.AlertConfigSearchRequest;
import com.techasians.alertservice.dto.ApiResponse;
import com.techasians.alertservice.service.AlertConfigService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import javax.validation.Valid;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/alert-configs")
public class AlertConfigController {

    private final AlertConfigService alertConfigService;

    public AlertConfigController(AlertConfigService alertConfigService) {
        this.alertConfigService = alertConfigService;
    }

    /**
     * API 1: Lấy danh sách cấu hình cảnh báo, có tìm kiếm và phân trang.
     *
     * Ví dụ gọi:
     * GET /api/alert-configs?categoryId=1&metricName=tỷ lệ&page=0&size=10
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AlertConfigResponse>>> search(
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String metricName,
            @RequestParam(required = false) String createdBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        // Gom các tham số tìm kiếm vào một đối tượng cho gọn
        AlertConfigSearchRequest searchRequest = new AlertConfigSearchRequest()
                .setCategoryId(categoryId)
                .setMetricName(metricName)
                .setCreatedBy(createdBy)
                .setPage(page)
                .setSize(size);

        Page<AlertConfigResponse> resultPage = alertConfigService.search(searchRequest);

        return ResponseEntity.ok(ApiResponse.success(resultPage));
    }

    /**
     * API 2: Thêm mới một cấu hình cảnh báo.
     *
     * Ví dụ gọi:
     * POST /api/alert-configs
     * Body: { "name": "...", "categoryId": 1, "metricId": 2, "status": 1, "levels":
     * [...] }
     */
    @PostMapping
    public ApiResponse<Integer> create(
            @Valid @RequestBody AlertConfigRequest request,
            @RequestHeader(value = "X-Username", defaultValue = "system") String username) {

        Integer configId = alertConfigService.create(request, username);
        return ApiResponse.success("Thêm mới cấu hình cảnh báo thành công", configId);
    }

    /**
     * API 3: Cập nhật cấu hình cảnh báo theo ID.
     *
     * Ví dụ gọi:
     * PUT /api/alert-configs/5
     * Body: { "name": "...", "categoryId": 1, "metricId": 2, "status": 0, "levels":
     * [...] }
     */
    @PutMapping("/{id}")
    public ApiResponse<Integer> update(
            @PathVariable Integer id,
            @Valid @RequestBody AlertConfigRequest request,
            @RequestHeader(value = "X-Username", defaultValue = "system") String username) {

        Integer configId = alertConfigService.update(id, request, username);
        return ApiResponse.success("Cập nhật cấu hình cảnh báo thành công", configId);
    }

    /**
     * API 4: Xóa mềm cấu hình cảnh báo theo ID.
     * Không xóa hẳn khỏi DB, chỉ bật cờ deleted = true.
     *
     * Ví dụ gọi:
     * DELETE /api/alert-configs/5
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Integer id,
            @RequestHeader(value = "X-Username", defaultValue = "system") String username) {

        alertConfigService.delete(id, username);
        return ApiResponse.success("Xóa cấu hình cảnh báo thành công", null);
    }

    /**
     * API 5: Lấy chi tiết cấu hình cảnh báo theo ID.
     *
     * Ví dụ gọi:
     * GET /api/alert-configs/5
     */
    @GetMapping("/{id}")
    public ApiResponse<AlertConfigDetailResponse> getDetail(@PathVariable Integer id) {
        AlertConfigDetailResponse response = alertConfigService.getDetail(id);
        return ApiResponse.success("Lấy chi tiết cấu hình cảnh báo thành công", response);
    }

    /**
     * API 6: Download file template import Excel rỗng.
     * Người dùng tải về, điền dữ liệu rồi upload lại qua API 7.
     *
     * Ví dụ gọi:
     * GET /api/alert-configs/import/template
     */
    @GetMapping("/import/template")
    public ResponseEntity<byte[]> downloadImportTemplate() throws IOException {
        // Danh sách tên cột header của template
        List<String> headers = Arrays.asList(
                "Loại chỉ số",
                "Tên chỉ số",
                "Mức độ cảnh báo",
                "Phép so sánh",
                "Giá trị ngưỡng (V1/Min)",
                "Giá trị ngưỡng (V2/Max)",
                "Nhận SMS (Y/N)",
                "Nhận Email (Y/N)",
                "Đầu mối nhận cảnh báo");

        // Tạo file Excel với 1 sheet có header
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Template Import");
            Row headerRow = sheet.createRow(0);

            for (int i = 0; i < headers.size(); i++) {
                headerRow.createCell(i).setCellValue(headers.get(i));
            }

            workbook.write(out);

            // Trả về file Excel với header Content-Disposition để trình duyệt tự động
            // download
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(
                    MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            httpHeaders.setContentDisposition(
                    ContentDisposition.attachment().filename("template_import_canh_bao.xlsx", StandardCharsets.UTF_8)
                            .build());

            return ResponseEntity.ok().headers(httpHeaders).body(out.toByteArray());
        }
    }

    /**
     * API 7: Upload file Excel để import cấu hình cảnh báo hàng loạt.
     *
     * Luồng xử lý:
     * - File sai format (.xlsx, header sai) → HTTP 400, body JSON lỗi
     * - Tất cả thành công → HTTP 200, body JSON thành công
     * - Có dòng lỗi → HTTP 200, body là file Excel lỗi để download
     *
     * Ví dụ gọi:
     * POST /api/alert-configs/import
     * Content-Type: multipart/form-data
     * file: <file .xlsx>
     * X-Username: user01
     */
    @PostMapping("/import")
    public ResponseEntity<?> importFromExcel(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Username", defaultValue = "system") String username) {

        // Gọi service xử lý import
        // - Ném IllegalArgumentException nếu file sai định dạng →
        // GlobalExceptionHandler bắt
        // - Trả về null nếu thành công hoàn toàn
        // - Trả về byte[] (file lỗi) nếu có dòng nào lỗi
        byte[] errorFileBytes = alertConfigService.importFromExcel(file, username);

        if (errorFileBytes != null) {
            // Có dòng lỗi → trả về file Excel lỗi để người dùng download và xem chi tiết
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(
                    MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            httpHeaders.setContentDisposition(
                    ContentDisposition.attachment().filename("ket_qua_import_canh_bao.xlsx", StandardCharsets.UTF_8)
                            .build());

            return ResponseEntity.ok().headers(httpHeaders).body(errorFileBytes);
        }

        return ResponseEntity.ok(ApiResponse.success("Import cấu hình cảnh báo thành công", null));
    }
}
