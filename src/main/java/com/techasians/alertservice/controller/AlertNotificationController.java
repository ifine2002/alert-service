package com.techasians.alertservice.controller;

import com.techasians.alertservice.dto.ApiResponse;
import com.techasians.alertservice.scheduler.AlertSchedulerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * Controller cung cấp API gửi cảnh báo thủ công.
 *
 * Người dùng nhấn nút trên UI → gọi POST /api/alert-notifications/send-now
 * → Hệ thống kiểm tra KPI từ 00:00 đến thời điểm hiện tại
 * → Gửi email/SMS ngay lập tức nếu phát hiện vi phạm.
 *
 * API trả về ngay lập tức (không chờ gửi xong) vì NotificationService dùng @Async.
 */
@RestController
@RequestMapping("/api/alert-notifications")
public class AlertNotificationController {

    private final AlertSchedulerService alertSchedulerService;

    public AlertNotificationController(AlertSchedulerService alertSchedulerService) {
        this.alertSchedulerService = alertSchedulerService;
    }

    /**
     * API gửi cảnh báo thủ công ngay lập tức.
     *
     * Luồng xử lý:
     * 1. Hệ thống lấy dữ liệu KPI từ 00:00 hôm nay đến thời điểm hiện tại
     * 2. So sánh với tất cả cấu hình cảnh báo đang active
     * 3. Gửi email/SMS bất đồng bộ nếu phát hiện vi phạm
     *
     * Ví dụ gọi:
     *   POST /api/alert-notifications/send-now
     *   Header: X-Username: user01
     *
     * Response:
     *   { "code": 200, "success": true, "message": "Đã kích hoạt...", "data": null }
     */
    @PostMapping("/send-now")
    public ResponseEntity<ApiResponse<Void>> sendNow(
            @RequestHeader(value = "X-Username", defaultValue = "system") String username) {

        // Lấy toàn bộ dữ liệu từ đầu ngày đến thời điểm hiện tại
        LocalDateTime toTime   = LocalDateTime.now();
        LocalDateTime fromTime = toTime.toLocalDate().atStartOfDay(); // 00:00:00 hôm nay

        // Gọi method dùng chung với AlertSchedulerService
        // Việc gửi email/SMS diễn ra bất đồng bộ (@Async) nên không block API này
        alertSchedulerService.runAlertCheck("Thủ công", fromTime, toTime);

        return ResponseEntity.ok(
                ApiResponse.success("Đã kích hoạt gửi cảnh báo. Vui lòng kiểm tra email/SMS trong giây lát.", null)
        );
    }
}
