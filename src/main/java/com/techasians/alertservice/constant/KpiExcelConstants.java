package com.techasians.alertservice.constant;

public class KpiExcelConstants {

    public static final String[] TEMPLATE_HEADERS = {
        "Tên kênh", "KPI", "Năng suất", "_channel_id"
    };

    public static final String RESULT_HEADER_NAME = "Kết quả";

    public static final int COL_INDEX_CHANNEL_NAME = 0;
    public static final int COL_INDEX_KPI = 1;
    public static final int COL_INDEX_PRODUCTIVITY = 2;
    public static final int COL_INDEX_CHANNEL_ID = 3;
    public static final int COL_INDEX_RESULT = 4;

    public static final String PERCENTAGE_CUSTOM_FORMAT = "0.##\"%\"";

    public static final String ERROR_KPI_OR_PRODUCTIVITY_MISSING = "KPI và Năng suất phải được nhập cùng nhau";
    public static final String ERROR_INVALID_NUMBER_FORMAT = "Dữ liệu không đúng định dạng số";
    public static final String ERROR_KPI_OUT_OF_RANGE = "KPI phải nằm trong khoảng từ 0 đến 100";
    public static final String ERROR_PRODUCTIVITY_NEGATIVE = "Năng suất không được là số âm";
    public static final String ERROR_CHANNEL_ID_MISSING = "Không xác định được ID kênh";
    public static final String ERROR_DUPLICATE_CHANNEL = "Kênh lưu lượng bị trùng lặp cấu hình trong file";
}
