package com.techasians.alertservice.controller;

import com.techasians.alertservice.dto.*;
import com.techasians.alertservice.service.TrafficEventService;
import com.techasians.alertservice.service.TrafficEventTemplateService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/traffic-events")
public class TrafficEventController {

    private final TrafficEventService trafficEventService;
    private final TrafficEventTemplateService templateService;

    public TrafficEventController(TrafficEventService trafficEventService,
                                  TrafficEventTemplateService templateService) {
        this.trafficEventService = trafficEventService;
        this.templateService = templateService;
    }

    /**
     * Tải xuống file template Excel để import sự kiện lưu lượng.
     * File gồm 4 sheet: Sự kiện định kỳ, Sự kiện phát sinh,
     * Reference Sự kiện định kỳ, Reference Sự kiện phát sinh.
     * Hai sheet Reference được tự động điền danh sách kênh đang hoạt động.
     */
    @GetMapping("/template")
    public ResponseEntity<ApiResponse<FileTemplateResponse>> downloadTemplate() {
        try {
            FileTemplateResponse response = templateService.buildImportTemplate();
            return ResponseEntity.ok(ApiResponse.success("Lấy template thành công", response));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(500, "Lỗi hệ thống: " + e.getMessage()));
        }
    }

    /**
     * Import sự kiện lưu lượng từ file Excel template.
     */
    @PostMapping("/import")
    public ResponseEntity<ApiResponse<TrafficEventImportResult>> importEvents(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User", defaultValue = "system") String importedBy) {
        try {
            TrafficEventImportResult result = trafficEventService.importFile(file, importedBy);
            if (result.getErrorFile() != null) {
                return ResponseEntity.ok(ApiResponse.success("Import có lỗi, kiểm tra file", result));
            }
            return ResponseEntity.ok(ApiResponse.success("Import sự kiện thành công", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(500, "Lỗi hệ thống: " + e.getMessage()));
        }
    }

    /**
     * Tạo mới sự kiện lưu lượng.
     * Body: TrafficEventRequest (gồm schedules và channels)
     * Header X-User hoặc tham số createdBy để ghi nhận người tạo.
     */
    @PostMapping
    public ApiResponse<TrafficEventResponse> create(
            @Valid @RequestBody TrafficEventRequest request,
            @RequestHeader(value = "X-User", defaultValue = "system") String createdBy) {
        try {
            TrafficEventResponse response = trafficEventService.create(request, createdBy);
            return ApiResponse.success("Tạo sự kiện thành công", response);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "Lỗi hệ thống: " + e.getMessage());
        }
    }

    /**
     * Cập nhật sự kiện (diff-sync schedules và channels).
     */
    @PutMapping("/{id}")
    public ApiResponse<TrafficEventResponse> update(
            @PathVariable Integer id,
            @Valid @RequestBody TrafficEventRequest request,
            @RequestHeader(value = "X-User", defaultValue = "system") String updatedBy) {
        try {
            TrafficEventResponse response = trafficEventService.update(id, request, updatedBy);
            return ApiResponse.success("Cập nhật sự kiện thành công", response);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "Lỗi hệ thống: " + e.getMessage());
        }
    }

    /**
     * Xóa mềm sự kiện (chỉ set deleted_at, schedules và channels giữ nguyên).
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Integer id,
            @RequestHeader(value = "X-User", defaultValue = "system") String deletedBy) {
        try {
            trafficEventService.delete(id, deletedBy);
            return ApiResponse.success("Xóa sự kiện thành công", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "Lỗi hệ thống: " + e.getMessage());
        }
    }

    /**
     * Lấy chi tiết 1 sự kiện kèm schedules và channels.
     */
    @GetMapping("/{id}")
    public ApiResponse<TrafficEventResponse> getById(@PathVariable Integer id) {
        try {
            TrafficEventResponse response = trafficEventService.getById(id);
            return ApiResponse.success(response);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "Lỗi hệ thống: " + e.getMessage());
        }
    }

    /**
     * Tìm kiếm phân trang sự kiện theo filter tùy chọn (name, eventType, status).
     */
    @PostMapping("/search")
    public ApiResponse<PageResult<TrafficEventResponse>> search(
            @RequestBody TrafficEventSearchRequest request) {
        try {
            PageResult<TrafficEventResponse> response = trafficEventService.search(request);
            return ApiResponse.success(response);
        } catch (Exception e) {
            return ApiResponse.error(500, "Lỗi hệ thống: " + e.getMessage());
        }
    }
}
