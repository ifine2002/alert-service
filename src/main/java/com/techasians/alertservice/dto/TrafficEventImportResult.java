package com.techasians.alertservice.dto;

public class TrafficEventImportResult {
    private byte[] errorFile;

    public byte[] getErrorFile() {
        return errorFile;
    }

    public TrafficEventImportResult setErrorFile(byte[] errorFile) {
        this.errorFile = errorFile;
        return this;
    }
}

