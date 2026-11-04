package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.dto.OutputReadinessResult;
import com.techasians.alertservice.dto.ReportActionHistoryProjection;
import com.techasians.alertservice.dto.ReportActionHistoryResponse;
import com.techasians.alertservice.dto.ReportDocumentSummaryResponse;
import com.techasians.alertservice.dto.ReportInputNodeResponse;
import com.techasians.alertservice.dto.ReportInputSearchRequest;
import com.techasians.alertservice.dto.ReportOutputResponse;
import com.techasians.alertservice.entity.RptDefinition;
import com.techasians.alertservice.entity.RptDefinitionLink;
import com.techasians.alertservice.entity.RptInstance;
import com.techasians.alertservice.enums.*;
import com.techasians.alertservice.repository.*;
import com.techasians.alertservice.service.ReportStatusService;
import com.techasians.alertservice.service.OutputReadinessService;
import com.techasians.alertservice.service.ReportFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportQueryServiceImplTest {
    private RptDefinitionRepository definitions;
    private RptDefinitionLinkRepository links;
    private RptInstanceRepository instances;
    private RptFileRepository files;
    private RptAuditLogRepository auditLogs;
    private OutputReadinessService outputReadinessService;
    private ReportQueryServiceImpl service;
    @BeforeEach void setUp() {
        definitions=mock(RptDefinitionRepository.class); links=mock(RptDefinitionLinkRepository.class);
        instances=mock(RptInstanceRepository.class); files=mock(RptFileRepository.class);
        auditLogs=mock(RptAuditLogRepository.class);
        ReportFileService reportFileService=mock(ReportFileService.class);
        outputReadinessService=mock(OutputReadinessService.class);
        service=new ReportQueryServiceImpl(definitions,links,instances,files,new ReportStatusService(),reportFileService,outputReadinessService,auditLogs);
        when(instances.findByWeekStartDate(any())).thenReturn(Collections.emptyList());
    }

    @Test void listsInputAsEmptyWithoutCreatingInstances() {
        RptDefinition group=definition(1003L,"B?o c?o Ch?t L??ng M?ng Tu?n",RptDirection.INPUT,RptNodeType.GROUP,RptFileKind.NONE);
        RptDefinition child=definition(1108L,"B?o c?o Word CLM v? SME tu?n",RptDirection.INPUT,RptNodeType.FILE,RptFileKind.WORD);
        when(definitions.findByDirectionAndNodeTypeAndStatusOrderBySortOrderAscIdAsc(RptDirection.INPUT,RptNodeType.GROUP,1)).thenReturn(Arrays.asList(group));
        when(definitions.findByDirectionAndNodeTypeAndStatusOrderBySortOrderAscIdAsc(RptDirection.INPUT,RptNodeType.FILE,1)).thenReturn(Arrays.asList(child));
        when(links.findByChildDefinitionIdInAndStatusOrderByChildDefinitionIdAscSortOrderAsc(anyCollection(),eq(1)))
                .thenReturn(Arrays.asList(new RptDefinitionLink().setParentDefinitionId(1003L).setChildDefinitionId(1108L)));
        List<ReportInputNodeResponse> result=service.listInputs(new ReportInputSearchRequest().setWeekStartDate(LocalDate.of(2026,3,23)));
        assertEquals(1,result.size()); assertEquals(RptInstanceStatus.EMPTY,result.get(0).getStatus());
        assertEquals(1,result.get(0).getTotalFiles()); assertEquals(1,result.get(0).getChildren().size()); verify(instances,never()).save(any());
    }
    @Test void searchesInputByNormalizedKeywordInReportNameOrUpdatedBy() {
        LocalDate weekStartDate = LocalDate.of(2026, 3, 23);
        RptDefinition group = definition(1003L, "Nhóm vận hành", RptDirection.INPUT,
                RptNodeType.GROUP, RptFileKind.NONE);
        RptDefinition child = definition(1108L, "Báo cáo đường truyền", RptDirection.INPUT,
                RptNodeType.FILE, RptFileKind.WORD);
        RptInstance instance = new RptInstance().setId(501L).setDefinitionId(1108L)
                .setWeekStartDate(weekStartDate).setStatus(RptInstanceStatus.COMPLETED)
                .setUpdatedBy("Nguyễn Văn Đạt");

        when(definitions.findByDirectionAndNodeTypeAndStatusOrderBySortOrderAscIdAsc(
                RptDirection.INPUT, RptNodeType.GROUP, 1)).thenReturn(Collections.singletonList(group));
        when(definitions.findByDirectionAndNodeTypeAndStatusOrderBySortOrderAscIdAsc(
                RptDirection.INPUT, RptNodeType.FILE, 1)).thenReturn(Collections.singletonList(child));
        when(links.findByChildDefinitionIdInAndStatusOrderByChildDefinitionIdAscSortOrderAsc(
                anyCollection(), eq(1))).thenReturn(Collections.singletonList(
                        new RptDefinitionLink().setParentDefinitionId(1003L).setChildDefinitionId(1108L)));
        when(instances.findByWeekStartDate(weekStartDate)).thenReturn(Collections.singletonList(instance));

        List<ReportInputNodeResponse> foundByReportName = service.listInputs(
                new ReportInputSearchRequest().setWeekStartDate(weekStartDate)
                        .setKeyword("BAO-cao DUONG"));
        List<ReportInputNodeResponse> foundByUpdatedBy = service.listInputs(
                new ReportInputSearchRequest().setWeekStartDate(weekStartDate)
                        .setKeyword("nguyen van dat"));

        assertEquals(1, foundByReportName.size());
        assertEquals(1, foundByUpdatedBy.size());
        assertEquals(1108L, foundByUpdatedBy.get(0).getChildren().get(0).getDefinitionId());
    }

    @Test void outputContainsReadinessResult() {
        RptDefinition output=definition(2001L,"B?o c?o tu?n VCX",RptDirection.OUTPUT,RptNodeType.FILE,RptFileKind.WORD);
        LocalDate weekStartDate = LocalDate.of(2026,3,23);
        when(definitions.findByDirectionAndNodeTypeAndStatusOrderBySortOrder(RptDirection.OUTPUT,RptNodeType.FILE,1)).thenReturn(Arrays.asList(output));
        OutputReadinessResult readiness = new OutputReadinessResult().setConfigured(true)
                .setReadyToGenerate(false).setMissingSourceIds(Arrays.asList(1103L));
        when(outputReadinessService.checkReadinessBatch(Arrays.asList(output), weekStartDate))
                .thenReturn(Collections.singletonMap(2001L, readiness));
        ReportOutputResponse result=service.listOutputs(weekStartDate).get(0);
        assertTrue(result.isReadinessConfigured()); assertFalse(result.isReadyToGenerate());
        assertEquals(Arrays.asList(1103L),result.getMissingSourceIds());
        verify(outputReadinessService).checkReadinessBatch(Arrays.asList(output), weekStartDate);
        verify(outputReadinessService, never()).checkReadiness(anyLong(), any());
        verify(instances,never()).save(any());
    }
    @Test void listsAllDocumentSummariesWithOnlyInstanceStatusData() {
        RptDefinition input=definition(1108L,"B?o c?o Word CLM v? SME tu?n",RptDirection.INPUT,RptNodeType.FILE,RptFileKind.WORD);
        RptDefinition output=definition(2001L,"B?o c?o tu?n VCX",RptDirection.OUTPUT,RptNodeType.FILE,RptFileKind.WORD);
        when(definitions.findByNodeTypeAndStatusOrderByDirectionAscSortOrderAsc(RptNodeType.FILE,1))
                .thenReturn(Arrays.asList(input,output));
        List<ReportDocumentSummaryResponse> result=service.listDocuments(LocalDate.of(2026,3,23),null);
        assertEquals(2,result.size());
        assertEquals(RptDirection.INPUT,result.get(0).getDirection());
        assertEquals(RptInstanceStatus.EMPTY,result.get(0).getStatus());
        assertNull(result.get(0).getUpdatedAt());
        verify(files,never()).findAllById(anyCollection());
        verify(instances,never()).save(any());
    }

    @Test void filtersDocumentSummariesByDirection() {
        RptDefinition output=definition(2001L,"B?o c?o tu?n VCX",RptDirection.OUTPUT,RptNodeType.FILE,RptFileKind.WORD);
        when(definitions.findByDirectionAndNodeTypeAndStatusOrderBySortOrder(RptDirection.OUTPUT,RptNodeType.FILE,1))
                .thenReturn(Arrays.asList(output));
        List<ReportDocumentSummaryResponse> result=service.listDocuments(LocalDate.of(2026,3,23),RptDirection.OUTPUT);
        assertEquals(1,result.size());
        assertEquals(RptDirection.OUTPUT,result.get(0).getDirection());
    }

    @Test void searchesAllActionsAndMapsHistory() {
        ReportActionHistoryProjection projection = historyProjection();
        when(auditLogs.searchActionHistory(eq(LocalDateTime.of(2026, 9, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 10, 1, 0, 0)), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.singletonList(projection)));

        Page<ReportActionHistoryResponse> result = service.searchActionHistory(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "   ",
                Collections.emptyList(), 1, 20);

        assertEquals(1, result.getTotalElements());
        ReportActionHistoryResponse item = result.getContent().get(0);
        assertEquals(1108L, item.getDefinitionId());
        assertEquals(501L, item.getInstanceId());
        assertEquals(LocalDate.of(2026, 9, 14), item.getWeekStartDate());
        assertEquals(LocalDate.of(2026, 9, 20), item.getWeekEndDate());
        assertEquals(801L, item.getFileId());
        assertEquals("bao-cao.xlsx", item.getFileName());
        assertNull(item.getReason());
        verify(auditLogs).searchActionHistory(eq(LocalDateTime.of(2026, 9, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 10, 1, 0, 0)), isNull(),
                argThat(p -> p.getPageNumber() == 0 && p.getPageSize() == 20));
    }

    @Test void searchesSelectedActions() {
        when(auditLogs.searchActionHistoryByActions(any(), any(), any(), anyList(), any(Pageable.class)))
                .thenReturn(Page.empty());
        List<RptAuditAction> actions = Arrays.asList(RptAuditAction.IMPORT, RptAuditAction.REJECT);

        service.searchActionHistory(null, null, " BÃ¡o cÃ¡o ", actions, 2, 10);

        verify(auditLogs).searchActionHistoryByActions(isNull(), isNull(), eq("BÃ¡o cÃ¡o"), eq(actions),
                argThat(p -> p.getPageNumber() == 1 && p.getPageSize() == 10));
        verify(auditLogs, never()).searchActionHistory(any(), any(), any(), any());
    }

    @Test void rejectsInvalidHistorySearchParameters() {
        assertThrows(IllegalArgumentException.class, () -> service.searchActionHistory(
                LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1), null, null, 1, 20));
        assertThrows(IllegalArgumentException.class, () -> service.searchActionHistory(
                null, null, null, null, 0, 20));
        assertThrows(IllegalArgumentException.class, () -> service.searchActionHistory(
                null, null, null, null, 1, 0));
        verifyNoInteractions(auditLogs);
    }

    private ReportActionHistoryProjection historyProjection() {
        return new ReportActionHistoryProjection() {
            public Long getDefinitionId() { return 1108L; }
            public Long getInstanceId() { return 501L; }
            public String getDefinitionName() { return "BÃ¡o cÃ¡o tuáº§n"; }
            public LocalDate getWeekStartDate() { return LocalDate.of(2026, 9, 14); }
            public RptAuditAction getAction() { return RptAuditAction.IMPORT; }
            public String getActor() { return "tester"; }
            public LocalDateTime getCreatedAt() { return LocalDateTime.of(2026, 9, 14, 9, 0); }
            public String getReason() { return null; }
            public Long getFileId() { return 801L; }
            public String getFileName() { return "bao-cao.xlsx"; }
        };
    }

    private RptDefinition definition(Long id,String name,RptDirection direction,RptNodeType nodeType,RptFileKind kind) {
        return new RptDefinition().setId(id).setName(name).setDirection(direction).setNodeType(nodeType)
                .setSourceMode(direction==RptDirection.INPUT?RptSourceMode.IMPORT:RptSourceMode.GENERATE)
                .setFileKind(kind).setAllowedExtension(kind==RptFileKind.WORD?"docx":"").setSortOrder(1).setStatus(1);
    }
}
