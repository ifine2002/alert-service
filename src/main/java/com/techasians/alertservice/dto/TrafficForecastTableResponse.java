package com.techasians.alertservice.dto;

import java.util.List;

/**
 * Response tổng hợp dự trù lưu lượng cho toàn bộ khu vực được yêu cầu.
 *
 * Cấu trúc JSON:
 * {
 *   "regions": [
 *     {
 *       "regionId": 5,
 *       "regionName": "Hà Nội",
 *       "channels": [ { "regionChannelId": 101, "channelId": 1, "channelName": "..." }, ... ],
 *       "days": [
 *         {
 *           "date": "2026-09-01", "year": "N2026", "quarter": "Q3/2026",
 *           "month": "T09", "day": "N01", "dayOfWeek": "T3",
 *           "channelResults": [
 *             { "regionChannelId": 101, "events": ["Ngày Quốc khánh"],
 *               "impactRate": 4.5, "value": 15200, "personnelCount": 65 }, ...
 *           ]
 *         },
 *         ...
 *       ]
 *     },
 *     ...
 *   ]
 * }
 */
public class TrafficForecastTableResponse {

    /**
     * Danh sách kết quả theo từng khu vực.
     * Mỗi khu vực có metadata kênh và danh sách ngày.
     */
    private List<TrafficForecastRegionResult> regions;

    public List<TrafficForecastRegionResult> getRegions() {
        return regions;
    }

    public TrafficForecastTableResponse setRegions(List<TrafficForecastRegionResult> regions) {
        this.regions = regions;
        return this;
    }
}
