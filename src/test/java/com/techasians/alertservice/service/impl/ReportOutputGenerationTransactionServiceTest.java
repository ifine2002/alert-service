package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.entity.*;
import com.techasians.alertservice.enums.*;
import com.techasians.alertservice.exception.InvalidReportStateException;
import com.techasians.alertservice.repository.*;
import com.techasians.alertservice.service.AuditLogService;
import com.techasians.alertservice.service.StoredReportFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportOutputGenerationTransactionServiceTest {
    private final RptInstanceRepository instances = mock(RptInstanceRepository.class);
    private final RptFileRepository files = mock(RptFileRepository.class);
    private final RptOutputSourceFileRepository sourceFiles = mock(RptOutputSourceFileRepository.class);
    private final RptOutputBlockDefRepository blocks = mock(RptOutputBlockDefRepository.class);
    private final RptOutputSourceMapRepository maps = mock(RptOutputSourceMapRepository.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final LocalDate week = LocalDate.of(2026, 5, 5);
    private final RptInstance output = new RptInstance().setId(41L).setDefinitionId(2001L)
            .setWeekStartDate(week).setStatus(RptInstanceStatus.GENERATING);
    private final ReportOutputGenerationTransactionService service = new ReportOutputGenerationTransactionService(
            instances, files, sourceFiles, blocks, maps, audit);

    @BeforeEach
    void setUp() {
        when(instances.findByIdWithLock(41L)).thenReturn(Optional.of(output));
        when(files.save(any(RptFile.class))).thenAnswer(call -> ((RptFile)call.getArgument(0)).setId(390L));
        when(instances.findByDefinitionIdAndWeekStartDate(1102L, week)).thenReturn(Optional.of(
                new RptInstance().setCurrentFileId(388L)));
        when(instances.findByDefinitionIdAndWeekStartDate(1112L, week)).thenReturn(Optional.of(
                new RptInstance().setCurrentFileId(389L)));
        // Mô phỏng đúng unique (output_file_id, output_block_id, source_file_id) của DB.
        Set<List<Long>> unique = new HashSet<>();
        when(sourceFiles.save(any(RptOutputSourceFile.class))).thenAnswer(call -> {
            RptOutputSourceFile link = call.getArgument(0);
            assertTrue(unique.add(Arrays.asList(link.getOutputFileId(), link.getOutputBlockId(), link.getSourceFileId())),
                    "Không được ghi trùng quan hệ output/block/file");
            return link;
        });
    }

    @Test
    void generatingWithinFiveMinutesRejectsAnotherGeneration() {
        output.setUpdatedAt(LocalDateTime.now().minusMinutes(2));
        when(instances.findByDefinitionIdAndWeekStartDateWithLock(2001L, week))
                .thenReturn(Optional.of(output));

        assertThrows(InvalidReportStateException.class, () -> service.prepare(2001L, week, "system"));

        verify(instances, never()).save(any());
    }

    @Test
    void expiredGenerationCanRestartAndRefreshesTimeoutForTheSameActor() {
        output.setUpdatedAt(LocalDateTime.now().minusMinutes(6)).setUpdatedBy("system");
        when(instances.findByDefinitionIdAndWeekStartDateWithLock(2001L, week))
                .thenReturn(Optional.of(output));
        when(instances.save(output)).thenReturn(output);
        when(files.findTopByReportInstanceIdOrderByVersionNoDesc(41L))
                .thenReturn(new RptFile().setVersionNo(3));
        LocalDateTime beforeRestart = LocalDateTime.now();

        ReportOutputGenerationTransactionService.GenerationAllocation allocation =
                service.prepare(2001L, week, "system");

        assertEquals(41L, allocation.getInstanceId());
        assertEquals(4, allocation.getVersionNo());
        assertTrue(allocation.isRegeneration());
        assertEquals(RptInstanceStatus.GENERATING, output.getStatus());
        assertFalse(output.getUpdatedAt().isBefore(beforeRestart));
        assertThrows(InvalidReportStateException.class, () -> service.prepare(2001L, week, "system"));
        verify(instances, times(1)).save(output);
    }

    @Test
    void generatingWithoutUpdatedAtDoesNotAllowAnUnverifiedRestart() {
        when(instances.findByDefinitionIdAndWeekStartDateWithLock(2001L, week))
                .thenReturn(Optional.of(output));

        assertThrows(InvalidReportStateException.class, () -> service.prepare(2001L, week, "system"));

        verify(instances, never()).save(any());
    }

    @Test
    void completedOutputCanGenerateAgainWithoutWaitingForTimeout() {
        output.setStatus(RptInstanceStatus.COMPLETED).setUpdatedAt(LocalDateTime.now());
        when(instances.findByDefinitionIdAndWeekStartDateWithLock(2001L, week))
                .thenReturn(Optional.of(output));
        when(instances.save(output)).thenReturn(output);

        service.prepare(2001L, week, "system");

        assertEquals(RptInstanceStatus.GENERATING, output.getStatus());
        verify(instances).save(output);
    }

    @Test
    void multipleYearSheetsInOneBlockSaveTheSharedFileOnceAndCompleteOutput() {
        block(8202L, source(1102L, 1, "Lũy kế lỗi phát sinh năm {yyyy}"), source(1102L, 2, "LPS năm {yyyy-1}"));
        when(blocks.findByOutputDefinitionIdAndStatusOrderBySortOrder(2001L, 1))
                .thenReturn(Collections.singletonList(new RptOutputBlockDef().setId(8202L)));

        RptFile result = commit();

        assertEquals(390L, result.getId());
        assertEquals(Collections.singleton("390/8202/388"), savedLinks(1));
        assertEquals(RptInstanceStatus.COMPLETED, output.getStatus());
        assertEquals(390L, output.getCurrentFileId());
        verify(instances).save(output);
        verify(audit).log(week, 41L, 390L, RptAuditAction.GENERATE, "system");
    }

    @Test
    void sameInputFileUsedByDifferentBlocksIsRecordedForEachBlock() {
        block(8202L, source(1102L, 1, "Năm hiện tại"), source(1102L, 2, "Năm trước"));
        block(8214L, source(1102L, 1, "Trạm hiện tại"), source(1102L, 2, "Trạm năm trước"));
        when(blocks.findByOutputDefinitionIdAndStatusOrderBySortOrder(2001L, 1)).thenReturn(Arrays.asList(
                new RptOutputBlockDef().setId(8202L), new RptOutputBlockDef().setId(8214L)));

        commit();

        assertEquals(new HashSet<>(Arrays.asList("390/8202/388", "390/8214/388")), savedLinks(2));
    }

    @Test
    void distinctFilesWithinOneBlockAreBothRecordedDespiteDuplicateSheetMappings() {
        block(8211L, source(1112L, 1, "NDPA"), source(1112L, 2, "NNLOI"), source(1102L, 3, "TH"));
        when(blocks.findByOutputDefinitionIdAndStatusOrderBySortOrder(2001L, 1))
                .thenReturn(Collections.singletonList(new RptOutputBlockDef().setId(8211L)));

        commit();

        assertEquals(new HashSet<>(Arrays.asList("390/8211/389", "390/8211/388")), savedLinks(2));
    }

    private void block(Long blockId, RptOutputSourceMap... sources) {
        when(maps.findByOutputBlockIdAndStatusOrderBySortOrder(blockId, 1)).thenReturn(Arrays.asList(sources));
    }

    private RptOutputSourceMap source(Long definition, int order, String sheet) {
        return new RptOutputSourceMap().setSourceDefinitionId(definition).setSortOrder(order).setSourceSheetName(sheet);
    }

    private RptFile commit() {
        return service.commit(new ReportOutputGenerationTransactionService.GenerationAllocation(41L, 1, false),
                "system", new StoredReportFile("report.docx", "reports/report.docx", 100L, "sha"));
    }

    private Set<String> savedLinks(int count) {
        ArgumentCaptor<RptOutputSourceFile> captured = ArgumentCaptor.forClass(RptOutputSourceFile.class);
        verify(sourceFiles, times(count)).save(captured.capture());
        return captured.getAllValues().stream().map(link -> link.getOutputFileId() + "/" + link.getOutputBlockId()
                + "/" + link.getSourceFileId()).collect(Collectors.toSet());
    }
}
