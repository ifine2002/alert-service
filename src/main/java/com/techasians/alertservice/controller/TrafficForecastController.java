package com.techasians.alertservice.controller;

import com.techasians.alertservice.dto.ApiResponse;
import com.techasians.alertservice.dto.TrafficForecastRequest;
import com.techasians.alertservice.dto.TrafficForecastTableResponse;
import com.techasians.alertservice.exception.ForecastDataException;
import com.techasians.alertservice.service.TrafficForecastService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * Controller cho chức năng dự trù lưu lượng.
 *
 * Endpoint: POST /api/traffic-forecast
 *
 * Request body:
 *   fromDate   : ngày bắt đầu cần dự trù (bắt buộc)
 *   toDate     : ngày kết thúc, tối đa fromDate + 3 tháng (bắt buộc)
 *   regionIds  : danh sách ID vùng (bắt buộc)
 *   channelIds : danh sách ID kênh (bắt buộc)
 *
 * Response:
 *   Danh sách TrafficForecastResponse, mỗi item là một region-channel.
 *   Mỗi region-channel chứa metadata và danh sách forecasts theo ngày.
 *   Giá trị đã round scale=0.
 */
@RestController
@RequestMapping("/api/traffic-forecast")
public class TrafficForecastController {

    private final TrafficForecastService trafficForecastService;

    public TrafficForecastController(TrafficForecastService trafficForecastService) {
        this.trafficForecastService = trafficForecastService;
    }

    /**
     * Tính dự trù lưu lượng metric "Cuộc gọi".
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TrafficForecastTableResponse>> forecast(
            @Valid @RequestBody TrafficForecastRequest request) {
        try {
            TrafficForecastTableResponse result = trafficForecastService.forecast(request);
            return ResponseEntity.ok(ApiResponse.success("Dự trù lưu lượng thành công", result));

        } catch (ForecastDataException e) {
            // Lỗi thiếu dữ liệu lịch sử - 422 Unprocessable Entity
            return ResponseEntity.unprocessableEntity()
                    .body(ApiResponse.error(422, e.getMessage()));

        } catch (IllegalArgumentException e) {
            // Lỗi validate request - 400 Bad Request
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));

        } catch (IllegalStateException e) {
            // Lỗi trạng thái hệ thống (không có actual data) - 400 Bad Request
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));

        } catch (Exception e) {
            // Lỗi không mong muốn - 500
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(500, "Lỗi hệ thống: " + e.getMessage()));
        }
    }
}
