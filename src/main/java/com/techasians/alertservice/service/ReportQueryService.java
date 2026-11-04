package com.techasians.alertservice.service;

import com.techasians.alertservice.dto.ReportActionHistoryResponse;
import com.techasians.alertservice.dto.ReportDocumentSummaryResponse;
import com.techasians.alertservice.dto.ReportDownloadDto;
import com.techasians.alertservice.dto.ReportInputNodeResponse;
import com.techasians.alertservice.dto.ReportInputSearchRequest;
import com.techasians.alertservice.dto.ReportOutputResponse;
import com.techasians.alertservice.dto.ReportFileVersionResponse;
import com.techasians.alertservice.enums.RptAuditAction;
import com.techasians.alertservice.enums.RptDirection;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;

public interface ReportQueryService {
    List<ReportInputNodeResponse> listInputs(ReportInputSearchRequest request);
    List<ReportOutputResponse> listOutputs(LocalDate weekStartDate);
    List<ReportDocumentSummaryResponse> listDocuments(LocalDate weekStartDate, RptDirection direction);
    ReportDownloadDto downloadInstanceFile(Long instanceId, String actor);
    List<ReportFileVersionResponse> listFileVersions(Long instanceId);
    ReportDownloadDto downloadFileVersion(Long fileId, String actor);
    Page<ReportActionHistoryResponse> searchActionHistory(LocalDate fromDate, LocalDate toDate,
                                                          String reportName, List<RptAuditAction> actions,
                                                          int page, int size);
}
