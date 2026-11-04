package com.techasians.alertservice.enums;

public enum RptInstanceStatus {

    // Chưa có dữ liệu, chưa thao tác gì
    EMPTY(0),

    // Đang import hoặc xử lý
    PROCESSING(1),

    // Đã hoàn thành, dữ liệu đầy đủ
    COMPLETED(2),

    // Bị từ chối bởi người quản lý
    REJECTED(3),

    // Lỗi trong quá trình xử lý
    ERROR(4),

    // Đang sinh file output
    GENERATING(5),

    // Dữ liệu cũ, cần cập nhật lại
    STALE(6);

    private final int value;

    RptInstanceStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
