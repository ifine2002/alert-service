package com.techasians.alertservice.enums;

public enum RptFileKind {

    // Không phải file cụ thể (dùng cho node nhóm)
    NONE(0),

    // File Word (.docx)
    WORD(1),

    // File Excel (.xlsx, .xlsm)
    EXCEL(2);

    private final int value;

    RptFileKind(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
