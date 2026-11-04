package com.techasians.alertservice.enums;

public enum RptDirection {

    INPUT(0),
    OUTPUT(1);

    private final int value;

    RptDirection(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
