package com.techasians.alertservice.enums;

public enum RptSourceMode {

    // Người dùng upload file lên hệ thống
    IMPORT(0),

    // Hệ thống tự động sinh file output từ các nguồn đầu vào
    GENERATE(1);

    private final int value;

    RptSourceMode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
