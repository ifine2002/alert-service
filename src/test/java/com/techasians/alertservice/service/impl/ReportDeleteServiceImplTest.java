package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.entity.RptDefinition;
import com.techasians.alertservice.entity.RptFile;
import com.techasians.alertservice.entity.RptInstance;
import com.techasians.alertservice.enums.RptAuditAction;
import com.techasians.alertservice.enums.RptDirection;
import com.techasians.alertservice.enums.RptFileKind;
import com.techasians.alertservice.enums.RptInstanceStatus;
import com.techasians.alertservice.enums.RptNodeType;
import com.techasians.alertservice.enums.RptSourceMode;
import com.techasians.alertservice.exception.InvalidReportStateException;
import com.techasians.alertservice.repository.RptDefinitionRepository;
import com.techasians.alertservice.repository.RptFileRepository;
import com.techasians.alertservice.repository.RptInstanceRepository;
import com.techasians.alertservice.repository.RptOutputSourceMapRepository;
import com.techasians.alertservice.service.AuditLogService;
import com.techasians.alertservice.service.ReportValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReportDeleteServiceImplTest {
    private RptInstanceRepository instances;
    private RptFileRepository files;
    private RptDefinitionRepository definitions;
    private RptOutputSourceMapRepository sourceMaps;
    private AuditLogService auditLogs;
    private ReportValidationService validation;
    private ReportDeleteServiceImpl service;

    @BeforeEach
    void setUp() {
        instances = mock(RptInstanceRepository.class);
        files = mock(RptFileRepository.class);
        definitions = mock(RptDefinitionRepository.class);
        sourceMaps = mock(RptOutputSourceMapRepository.class);
        auditLogs = mock(AuditLogService.class);
        validation = mock(ReportValidationService.class);
        service = new ReportDeleteServiceImpl(instances, files, definitions,
                sourceMaps, auditLogs, validation);
    }

    @Test
    void clearsInputAndDependentOutputsWithoutDeletingFiles() {
        RptInstance input = inputInstance(RptInstanceStatus.COMPLETED);
        RptInstance output = new RptInstance().setId(20L).setDefinitionId(2001L)
                .setWeekStartDate(input.getWeekStartDate()).setStatus(RptInstanceStatus.COMPLETED)
                .setCurrentFileId(200L).setRejectedReason("old").setRejectedBy("manager");
        stubValidDelete(input);
        when(sourceMaps.findActiveOutputDefinitionIdsBySourceDefinitionId(1108L))
                .thenReturn(Collections.singletonList(2001L));
        when(instances.findByWeekStartDateAndDefinitionIdInOrderByIdAsc(
                input.getWeekStartDate(), Collections.singletonList(2001L)))
                .thenReturn(Collections.singletonList(output));

        service.deleteInputFile(10L, "tester", " Xóa nhầm file ");

        assertEquals(RptInstanceStatus.EMPTY, input.getStatus());
        assertNull(input.getCurrentFileId());
        assertNull(input.getRejectedReason());
        assertEquals(RptInstanceStatus.EMPTY, output.getStatus());
        assertNull(output.getCurrentFileId());
        verify(auditLogs).log(input.getWeekStartDate(), 10L, 100L,
                RptAuditAction.DELETE, "tester", "Xóa nhầm file");
        verify(files, never()).delete(any());
        verify(instances).saveAll(Collections.singletonList(output));
    }

    @Test
    void acceptsRejectedInputAndNullReason() {
        RptInstance input = inputInstance(RptInstanceStatus.REJECTED);
        stubValidDelete(input);
        when(sourceMaps.findActiveOutputDefinitionIdsBySourceDefinitionId(1108L))
                .thenReturn(Collections.emptyList());

        service.deleteInputFile(10L, "tester", null);

        assertEquals(RptInstanceStatus.EMPTY, input.getStatus());
        verify(auditLogs).log(input.getWeekStartDate(), 10L, 100L,
                RptAuditAction.DELETE, "tester", null);
    }

    @Test
    void rejectsDeleteWhenDependentOutputIsGenerating() {
        RptInstance input = inputInstance(RptInstanceStatus.COMPLETED);
        RptInstance output = new RptInstance().setId(20L).setDefinitionId(2001L)
                .setWeekStartDate(input.getWeekStartDate()).setStatus(RptInstanceStatus.GENERATING);
        stubValidDelete(input);
        when(sourceMaps.findActiveOutputDefinitionIdsBySourceDefinitionId(1108L))
                .thenReturn(Collections.singletonList(2001L));
        when(instances.findByWeekStartDateAndDefinitionIdInOrderByIdAsc(any(), any()))
                .thenReturn(Collections.singletonList(output));

        assertThrows(InvalidReportStateException.class,
                () -> service.deleteInputFile(10L, "tester", null));

        verifyNoInteractions(auditLogs);
        assertEquals(RptInstanceStatus.COMPLETED, input.getStatus());
        assertEquals(100L, input.getCurrentFileId());
    }

    @Test
    void onlyLoadsDependentOutputsFromSameWeek() {
        RptInstance input = inputInstance(RptInstanceStatus.COMPLETED);
        stubValidDelete(input);
        when(sourceMaps.findActiveOutputDefinitionIdsBySourceDefinitionId(1108L))
                .thenReturn(Arrays.asList(2001L, 2002L));
        when(instances.findByWeekStartDateAndDefinitionIdInOrderByIdAsc(
                input.getWeekStartDate(), Arrays.asList(2001L, 2002L)))
                .thenReturn(Collections.emptyList());

        service.deleteInputFile(10L, "tester", null);

        verify(instances).findByWeekStartDateAndDefinitionIdInOrderByIdAsc(
                input.getWeekStartDate(), Arrays.asList(2001L, 2002L));
        verify(instances).saveAll(Collections.emptyList());
    }

    private void stubValidDelete(RptInstance input) {
        when(instances.findByIdWithLock(10L)).thenReturn(Optional.of(input));
        when(definitions.findById(1108L)).thenReturn(Optional.of(inputDefinition()));
        when(files.findById(100L)).thenReturn(Optional.of(
                new RptFile().setId(100L).setReportInstanceId(10L)
                        .setOriginalFilename("input.xlsx")));
    }

    private RptInstance inputInstance(RptInstanceStatus status) {
        return new RptInstance().setId(10L).setDefinitionId(1108L)
                .setWeekStartDate(LocalDate.of(2026, 9, 14))
                .setStatus(status).setCurrentFileId(100L)
                .setRejectedReason("reason").setRejectedBy("manager");
    }

    private RptDefinition inputDefinition() {
        return new RptDefinition().setId(1108L).setName("Input")
                .setDirection(RptDirection.INPUT).setNodeType(RptNodeType.FILE)
                .setSourceMode(RptSourceMode.IMPORT).setFileKind(RptFileKind.EXCEL)
                .setAllowedExtension("xlsx").setStatus(1);
    }
}