package com.techasians.alertservice.enums;

public enum RptNodeType {

    GROUP(0),

    // File input/output thực tế (ví dụ: file Word, file Excel)
    FILE(1);

    private final int value;

    RptNodeType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
