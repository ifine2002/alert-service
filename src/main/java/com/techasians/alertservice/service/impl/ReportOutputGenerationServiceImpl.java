package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.event.OutputGenerationPreparedEvent;
import com.techasians.alertservice.service.ExcelOutputGenerator;
import com.techasians.alertservice.service.ReportFileService;
import com.techasians.alertservice.service.ReportOutputGenerationService;
import com.techasians.alertservice.service.StoredReportFile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class ReportOutputGenerationServiceImpl implements ReportOutputGenerationService {
    private static final Long APPENDIX_ONE_DEFINITION_ID = 2002L;
    private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final ExcelOutputGenerator generator;
    private final ReportOutputGenerationTransactionService transactionService;
    private final ReportFileService reportFileService;
    private final WeeklyWordOutputGenerator wordGenerator;
    private final WordGenerationSnapshotService wordSnapshots;

    public ReportOutputGenerationServiceImpl(ExcelOutputGenerator generator,
            ReportOutputGenerationTransactionService transactionService,
            ReportFileService reportFileService, WeeklyWordOutputGenerator wordGenerator, WordGenerationSnapshotService wordSnapshots) {
        this.generator = generator;
        this.transactionService = transactionService;
        this.reportFileService = reportFileService;
        this.wordGenerator = wordGenerator;
        this.wordSnapshots = wordSnapshots;
    }

    @Override
    public void execute(OutputGenerationPreparedEvent event) {
        ReportOutputGenerationTransactionService.GenerationAllocation allocation =
                new ReportOutputGenerationTransactionService.GenerationAllocation(
                        event.getInstanceId(), event.getVersionNo(), event.isRegeneration());
        Path generatedFile = null;
        StoredReportFile storedFile = null;
        boolean committed = false;
        try {
            if (Long.valueOf(2001L).equals(event.getOutputDefinitionId())) {
                generatedFile = wordGenerator.generate(
                        wordSnapshots.load(event.getOutputDefinitionId(), event.getWeekStartDate()));
            } else {
                generatedFile = generator.generate(event.getOutputDefinitionId(), event.getWeekStartDate());
            }
            String filename = buildFilename(event.getOutputDefinitionId(), event.getWeekStartDate());
            storedFile = reportFileService.uploadGenerated(generatedFile, filename, event.getWeekStartDate(),
                    event.getOutputDefinitionId(), event.getInstanceId(), event.getVersionNo());
            transactionService.commit(allocation, event.getActor(), storedFile);
            committed = true;
        } finally {
            if (!committed) {
                if (storedFile != null) {
                    reportFileService.deleteIfExists(storedFile.getObjectName());
                }
                transactionService.markError(event.getInstanceId(), event.getActor());
            }
            deleteTemporaryFile(generatedFile);
        }
    }

    private void deleteTemporaryFile(Path generatedFile) {
        if (generatedFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(generatedFile);
        } catch (IOException ignored) {
            // File tạm sẽ được hệ điều hành dọn sau; không che lỗi generate chính.
        }
    }

    private String buildFilename(Long outputDefinitionId, LocalDate weekStartDate) {
        if (Long.valueOf(2001L).equals(outputDefinitionId)) {
            return "Bao-cao-VCX " + weekStartDate.format(FILE_DATE_FORMATTER) + " - "
                    + weekStartDate.plusDays(6).format(FILE_DATE_FORMATTER) + ".docx";
        }
        String appendixName;
        if (APPENDIX_ONE_DEFINITION_ID.equals(outputDefinitionId)) {
            appendixName = "Phu-luc-1";
        } else {
            appendixName = "Phu-luc-2";
        }

        LocalDate weekEndDate = weekStartDate.plusDays(6);
        String startDate = weekStartDate.format(FILE_DATE_FORMATTER);
        String endDate = weekEndDate.format(FILE_DATE_FORMATTER);
        return appendixName + " " + startDate + " - " + endDate + ".xlsx";
    }
}
