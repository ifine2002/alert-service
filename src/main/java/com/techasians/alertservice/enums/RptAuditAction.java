package com.techasians.alertservice.enums;

/** Thứ tự enum phải khớp với rpt_audit_log.action trong DDL. */
public enum RptAuditAction {
    IMPORT(0),
    REJECT(1),
    DELETE(2),
    GENERATE(3),
    REGENERATE(4);

    private final int value;

    RptAuditAction(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
