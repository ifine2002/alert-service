package com.techasians.alertservice.exception;

import com.techasians.alertservice.dto.FileTemplateResponse;

public class ImportValidationException extends RuntimeException {
    
    private final FileTemplateResponse fileTemplateResponse;

    public ImportValidationException(String message, FileTemplateResponse fileTemplateResponse) {
        super(message);
        this.fileTemplateResponse = fileTemplateResponse;
    }

    public FileTemplateResponse getFileTemplateResponse() {
        return fileTemplateResponse;
    }
}
