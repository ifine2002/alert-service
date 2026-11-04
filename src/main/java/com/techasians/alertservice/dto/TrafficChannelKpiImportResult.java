package com.techasians.alertservice.dto;

public class TrafficChannelKpiImportResult {
    private byte[] errorFile;

    public byte[] getErrorFile() {
        return errorFile;
    }

    public void setErrorFile(byte[] errorFile) {
        this.errorFile = errorFile;
    }
}
