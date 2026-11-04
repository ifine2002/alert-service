package com.techasians.alertservice.enums;

public enum RptSourceRole {

    TEXT(0),
    DATA(1),
    CHART(2),
    /** Đọc một file Excel duy nhất của tuần báo cáo; reader tự tổng hợp 8 tuần từ sheet lũy kế. */
    CHART_ACCUMULATED(3);

    private final int value;

    RptSourceRole(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
