package com.techasians.alertservice.dto;

public class ReportImportResult {
    private byte[] errorFile;

    public byte[] getErrorFile() {
        return errorFile;
    }

    public ReportImportResult setErrorFile(byte[] errorFile) {
        this.errorFile = errorFile;
        return this;
    }
}
