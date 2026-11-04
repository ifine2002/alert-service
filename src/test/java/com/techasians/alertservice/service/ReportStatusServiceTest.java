package com.techasians.alertservice.service;

import com.techasians.alertservice.dto.ReportInputNodeResponse;
import com.techasians.alertservice.enums.RptInstanceStatus;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportStatusServiceTest {
    private final ReportStatusService service = new ReportStatusService();
    private ReportInputNodeResponse file(Long instanceId, RptInstanceStatus status) {
        return new ReportInputNodeResponse().setInstanceId(instanceId).setStatus(status);
    }
    @Test void returnsEmptyWhenNoInstanceExists() {
        assertEquals(RptInstanceStatus.EMPTY, service.calculateGroupStatus(Arrays.asList(file(null, RptInstanceStatus.EMPTY))));
    }
    @Test void returnsCompletedOnlyWhenAllFilesCompleted() {
        assertEquals(RptInstanceStatus.COMPLETED, service.calculateGroupStatus(Arrays.asList(
                file(1L, RptInstanceStatus.COMPLETED), file(2L, RptInstanceStatus.COMPLETED))));
        assertEquals(RptInstanceStatus.PROCESSING, service.calculateGroupStatus(Arrays.asList(
                file(1L, RptInstanceStatus.COMPLETED), file(null, RptInstanceStatus.EMPTY))));
    }
    @Test void returnsProcessingWhenAChildHasImportHistory() {
        assertEquals(RptInstanceStatus.PROCESSING, service.calculateGroupStatus(Arrays.asList(
                file(1L, RptInstanceStatus.PROCESSING), file(2L, RptInstanceStatus.ERROR))));
        assertEquals(RptInstanceStatus.PROCESSING, service.calculateGroupStatus(Arrays.asList(
                file(1L, RptInstanceStatus.PROCESSING), file(2L, RptInstanceStatus.REJECTED))));
        assertEquals(RptInstanceStatus.PROCESSING, service.calculateGroupStatus(Arrays.asList(
                file(1L, RptInstanceStatus.REJECTED), file(null, RptInstanceStatus.EMPTY))));
        assertEquals(RptInstanceStatus.PROCESSING, service.calculateGroupStatus(Arrays.asList(
                file(1L, RptInstanceStatus.ERROR), file(null, RptInstanceStatus.EMPTY))));
    }
}
