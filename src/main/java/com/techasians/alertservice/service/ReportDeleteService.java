package com.techasians.alertservice.service;

public interface ReportDeleteService {
    void deleteInputFile(Long instanceId, String actor, String reason);
}