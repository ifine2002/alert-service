package com.techasians.alertservice.enums;

public enum RptRenderType {

    // Copy nguyên section từ file Word nguồn sang output
    COPY_SECTION(0),

    // Gọi Java handler tùy chỉnh để xử lý logic phức tạp
    CUSTOM_HANDLER(1);

    private final int value;

    RptRenderType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
