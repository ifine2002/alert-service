package com.techasians.alertservice.controller;

import com.techasians.alertservice.dto.ApiResponse;
import com.techasians.alertservice.dto.TrafficChannelKpiRequest;
import com.techasians.alertservice.dto.TrafficChannelKpiResponse;
import com.techasians.alertservice.dto.TrafficChannelKpiSearchRequest;
import com.techasians.alertservice.service.TrafficChannelKpiService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.techasians.alertservice.service.TrafficChannelKpiTemplateService;
import com.techasians.alertservice.service.TrafficChannelKpiImportService;
import com.techasians.alertservice.dto.FileTemplateResponse;
import com.techasians.alertservice.dto.TrafficChannelKpiImportResult;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1/traffic-channel-kpis")
public class TrafficChannelKpiController {

    private final TrafficChannelKpiService trafficChannelKpiService;
    private final TrafficChannelKpiTemplateService templateService;
    private final TrafficChannelKpiImportService importService;

    public TrafficChannelKpiController(TrafficChannelKpiService trafficChannelKpiService,
                                       TrafficChannelKpiTemplateService templateService,
                                       TrafficChannelKpiImportService importService) {
        this.trafficChannelKpiService = trafficChannelKpiService;
        this.templateService = templateService;
        this.importService = importService;
    }

    @GetMapping("/template")
    public ResponseEntity<ApiResponse<FileTemplateResponse>> downloadTemplate() {
        FileTemplateResponse response = templateService.buildImportTemplate();
        return ResponseEntity.ok(ApiResponse.success("Lấy template thành công", response));
    }

    @PostMapping("/import")
    public ResponseEntity<ApiResponse<TrafficChannelKpiImportResult>> importKpi(
            @RequestParam("file") MultipartFile file) {
        TrafficChannelKpiImportResult result = importService.importFile(file);
        if (result.getErrorFile() != null) {
            return ResponseEntity.ok(ApiResponse.success("Import có lỗi, kiểm tra file", result));
        }
        return ResponseEntity.ok(ApiResponse.success("Import KPI thành công", result));
    }

    @PostMapping
    public ApiResponse<Integer> create(
            @Valid @RequestBody TrafficChannelKpiRequest request,
            @RequestHeader(value = "X-Username", defaultValue = "system") String username) {
        Integer kpiId = trafficChannelKpiService.create(request, username);
        return ApiResponse.success("Tạo mới KPI kênh lưu lượng thành công", kpiId);
    }

    @PutMapping("/{id}")
    public ApiResponse<Integer> update(
            @PathVariable Integer id,
            @Valid @RequestBody TrafficChannelKpiRequest request,
            @RequestHeader(value = "X-Username", defaultValue = "system") String username) {
        Integer kpiId = trafficChannelKpiService.update(id, request, username);
        return ApiResponse.success("Cập nhật KPI kênh lưu lượng thành công", kpiId);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Integer id,
            @RequestHeader(value = "X-Username", defaultValue = "system") String username) {
        trafficChannelKpiService.delete(id, username);
        return ApiResponse.success("Xóa KPI kênh lưu lượng thành công", null);
    }

    @GetMapping("/{id}")
    public ApiResponse<TrafficChannelKpiResponse> getDetail(@PathVariable Integer id) {
        TrafficChannelKpiResponse response = trafficChannelKpiService.getById(id);
        return ApiResponse.success("Lấy chi tiết KPI kênh lưu lượng thành công", response);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<TrafficChannelKpiResponse>>> search(
            @RequestParam(required = false) Integer channelId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort) {

        TrafficChannelKpiSearchRequest searchRequest = new TrafficChannelKpiSearchRequest()
                .setChannelId(channelId)
                .setKeyword(keyword)
                .setPage(page)
                .setSize(size)
                .setSort(sort);

        Page<TrafficChannelKpiResponse> resultPage = trafficChannelKpiService.search(searchRequest);
        return ResponseEntity.ok(ApiResponse.success(resultPage));
    }
}
