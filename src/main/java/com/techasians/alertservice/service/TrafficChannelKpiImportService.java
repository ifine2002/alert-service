package com.techasians.alertservice.service;

import com.techasians.alertservice.dto.TrafficChannelKpiImportResult;
import org.springframework.web.multipart.MultipartFile;

public interface TrafficChannelKpiImportService {
    TrafficChannelKpiImportResult importFile(MultipartFile file);
}
