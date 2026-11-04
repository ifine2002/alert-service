package com.techasians.alertservice.dto.forecast;

/**
 * Nguồn gốc của dữ liệu lưu lượng một ngày.
 * ACTUAL: dữ liệu thực tế từ database.
 * FORECAST: dữ liệu dự trù được tính toán trong memory.
 * FALLBACK: dữ liệu dự trù dùng cơ chế dự phòng khi không đủ dữ liệu chuẩn.
 */
public enum ForecastSource {
    ACTUAL,
    FORECAST,
    FALLBACK
}
