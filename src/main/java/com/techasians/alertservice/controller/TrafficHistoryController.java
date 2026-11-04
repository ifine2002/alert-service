package com.techasians.alertservice.controller;

import com.techasians.alertservice.dto.ApiResponse;
import com.techasians.alertservice.dto.ImportResultDTO;
import com.techasians.alertservice.service.TrafficHistoryImportService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/traffic/histories")
public class TrafficHistoryController {

    private final TrafficHistoryImportService trafficHistoryImportService;

    public TrafficHistoryController(TrafficHistoryImportService trafficHistoryImportService) {
        this.trafficHistoryImportService = trafficHistoryImportService;
    }

    /**
     * API Import dữ liệu lưu lượng lịch sử từ file Excel.
     *
     * Cách gọi (Postman/curl):
     * POST /api/traffic/histories/import
     * Content-Type: multipart/form-data
     * Body: file = [chọn file Excel .xlsx]
     *
     * Cấu trúc file Excel:
     * - Mỗi Sheet = 1 khu vực (tên Sheet phải trùng với TrafficRegion.name trong
     * DB)
     * - 3 hàng Header: Kênh Cha | Kênh Con | Tên Metric
     * - Từ hàng 4 trở đi: mỗi hàng = 1 ngày dữ liệu
     */
    @PostMapping("/import")
    public ApiResponse<ImportResultDTO> importExcel(@RequestParam("file") MultipartFile file) {
        // Kiểm tra file có rỗng không
        if (file == null || file.isEmpty()) {
            return ApiResponse.error(400, "File không được để trống.");
        }

        // Kiểm tra đuôi file phải là .xlsx
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".xlsx")) {
            return ApiResponse.error(400, "Chỉ hỗ trợ file định dạng .xlsx");
        }

        try {
            ImportResultDTO result = trafficHistoryImportService.importExcel(file);
            return ApiResponse.success(result.getMessage(), result);
        } catch (Exception e) {
            return ApiResponse.error(500, "Import thất bại: " + e.getMessage());
        }
    }
}
