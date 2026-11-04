package com.techasians.alertservice.service;

import com.techasians.alertservice.dto.ReportInputNodeResponse;
import com.techasians.alertservice.enums.RptInstanceStatus;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ReportStatusService {
    public RptInstanceStatus calculateGroupStatus(List<ReportInputNodeResponse> files) {
        if (files == null || files.isEmpty()) {
            return RptInstanceStatus.EMPTY;
        }

        boolean hasImportedFile = false;
        boolean allFilesCompleted = true;

        for (ReportInputNodeResponse file : files) {
            if (file.getInstanceId() != null) {
                hasImportedFile = true;
            }
            if (file.getStatus() != RptInstanceStatus.COMPLETED) {
                allFilesCompleted = false;
            }
        }

        if (allFilesCompleted) {
            return RptInstanceStatus.COMPLETED;
        }
        if (hasImportedFile) {
            return RptInstanceStatus.PROCESSING;
        }
        return RptInstanceStatus.EMPTY;
    }
}
