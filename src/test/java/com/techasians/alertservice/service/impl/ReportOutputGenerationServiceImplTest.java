//package com.techasians.alertservice.service.impl;
//
//import com.techasians.alertservice.event.OutputGenerationPreparedEvent;
//import com.techasians.alertservice.service.ExcelOutputGenerator;
//import com.techasians.alertservice.service.ReportFileService;
//import com.techasians.alertservice.service.StoredReportFile;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.time.LocalDate;
//
//import static org.junit.jupiter.api.Assertions.assertFalse;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
//class ReportOutputGenerationServiceImplTest {
//    private ExcelOutputGenerator generator;
//    private ReportOutputGenerationTransactionService transactionService;
//    private ReportFileService reportFileService;
//    private ReportOutputGenerationServiceImpl service;
//
//    @BeforeEach
//    void setUp() {
//        generator = mock(ExcelOutputGenerator.class);
//        transactionService = mock(ReportOutputGenerationTransactionService.class);
//        reportFileService = mock(ReportFileService.class);
//        service = new ReportOutputGenerationServiceImpl(generator, transactionService, reportFileService);
//    }
//
//    @Test
//    void executesPreparedGenerationAndCommits() throws IOException {
//        Path generatedFile = Files.createTempFile("output-generation-test", ".xlsx");
//        LocalDate week = LocalDate.of(2026, 9, 21);
//        OutputGenerationPreparedEvent event = new OutputGenerationPreparedEvent(
//                2002L, week, 31L, 4, true, "system");
//        StoredReportFile storedFile = new StoredReportFile("output.xlsx", "reports/output.xlsx", 100L, "sha");
//        when(generator.generate(2002L, week)).thenReturn(generatedFile);
//        when(reportFileService.uploadGenerated(eq(generatedFile), anyString(), eq(week),
//                eq(2002L), eq(31L), eq(4))).thenReturn(storedFile);
//
//        service.execute(event);
//
//        verify(transactionService).commit(argThat(allocation -> allocation.getInstanceId().equals(31L)
//                && allocation.getVersionNo().equals(4) && allocation.isRegeneration()), eq("system"), eq(storedFile));
//        verify(transactionService, never()).prepare(any(), any(), any());
//        verify(transactionService, never()).markError(anyLong(), anyString());
//        assertFalse(Files.exists(generatedFile));
//    }
//
//    @Test
//    void executesWordGenerationAndCommitsItsSnapshot() throws IOException {
//        WeeklyWordOutputGenerator wordGenerator = mock(WeeklyWordOutputGenerator.class);
//        WordGenerationSnapshotService snapshots = mock(WordGenerationSnapshotService.class);
//        service = new ReportOutputGenerationServiceImpl(generator, transactionService, reportFileService,
//                wordGenerator, snapshots);
//        Path generatedFile = Files.createTempFile("word-output-test", ".docx");
//        LocalDate week = LocalDate.of(2026, 9, 21);
//        com.techasians.alertservice.service.WordGenerationSnapshot snapshot =
//                new com.techasians.alertservice.service.WordGenerationSnapshot(week, "template", java.util.Collections.emptyList());
//        StoredReportFile storedFile = new StoredReportFile("report.docx", "reports/report.docx", 100L, "sha");
//        when(snapshots.load(2001L, week)).thenReturn(snapshot);
//        when(wordGenerator.generate(snapshot)).thenReturn(generatedFile);
//        when(reportFileService.uploadGenerated(eq(generatedFile), endsWith(".docx"), eq(week),
//                eq(2001L), eq(31L), eq(4))).thenReturn(storedFile);
//
//        service.execute(new OutputGenerationPreparedEvent(2001L, week, 31L, 4, true, "system"));
//
//        verify(transactionService).commit(any(), eq("system"), eq(storedFile));
//        verifyNoInteractions(generator);
//        assertFalse(Files.exists(generatedFile));
//    }
//
//    @Test
//    void marksErrorWhenGenerationFails() {
//        LocalDate week = LocalDate.of(2026, 9, 21);
//        OutputGenerationPreparedEvent event = new OutputGenerationPreparedEvent(
//                2002L, week, 32L, 1, false, "system");
//        when(generator.generate(2002L, week)).thenThrow(new IllegalStateException("Không thể tạo file"));
//
//        try {
//            service.execute(event);
//        } catch (IllegalStateException ignored) {
//            // Listener sẽ log lỗi; test kiểm tra trạng thái bù trừ bên dưới.
//        }
//
//        verify(transactionService).markError(32L, "system");
//        verify(transactionService, never()).commit(any(), anyString(), any());
//    }
//}