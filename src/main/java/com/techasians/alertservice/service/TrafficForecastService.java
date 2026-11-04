package com.techasians.alertservice.service;

import com.techasians.alertservice.dto.TrafficForecastRequest;
import com.techasians.alertservice.dto.TrafficForecastTableResponse;

/**
 * Service interface cho chức năng dự trù lưu lượng.
 */
public interface TrafficForecastService {

    /**
     * Tính dự trù lưu lượng metric "Cuộc gọi" cho khoảng thời gian request.
     *
     * Response group theo khu vực, mỗi khu vực gồm:
     *   - channels: danh sách metadata kênh (dùng để build header bảng)
     *   - days: danh sách ngày, mỗi ngày chứa thông tin ngày + kết quả từng kênh
     *
     * @param request chứa fromDate, toDate, regionIds, channelIds
     * @return kết quả dự trù group theo region → days → channelResults
     */
    TrafficForecastTableResponse forecast(TrafficForecastRequest request);
}
