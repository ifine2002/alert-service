package com.techasians.alertservice.dto;

/**
 * Dùng nội bộ khi đọc header Excel.
 * Mỗi cột dữ liệu (metric) sẽ được ánh xạ vào một ColumnMetadata.
 *
 * Ví dụ header: "KÊNH CỐ ĐỊNH 18008119" > "Call - Econtact (4G)" > "Cuộc gọi"
 *   channelName     = "Call - Econtact (4G)"  (tên kênh con, hoặc tên kênh cha nếu không có kênh con)
 *   parentChannelName = "KÊNH CỐ ĐỊNH 18008119" (tên kênh cha, null nếu không có)
 *   metricName      = "Cuộc gọi"
 *   columnIndex     = vị trí cột trong Excel (0-based)
 */
public class ColumnMetadata {

    // Tên kênh (kênh con nếu có, ngược lại là kênh cha)
    private String channelName;

    // Tên kênh cha (null nếu đây là kênh gốc)
    private String parentChannelName;

    // Tên chỉ số (metric)
    private String metricName;

    // Vị trí cột trong Excel (0-based)
    private int columnIndex;

    public ColumnMetadata(String channelName, String parentChannelName, String metricName, int columnIndex) {
        this.channelName = channelName;
        this.parentChannelName = parentChannelName;
        this.metricName = metricName;
        this.columnIndex = columnIndex;
    }

    public String getChannelName() {
        return channelName;
    }

    public String getParentChannelName() {
        return parentChannelName;
    }

    public String getMetricName() {
        return metricName;
    }

    public int getColumnIndex() {
        return columnIndex;
    }
}
