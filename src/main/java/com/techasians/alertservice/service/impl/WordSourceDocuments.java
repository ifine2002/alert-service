package com.techasians.alertservice.service.impl;

import com.aspose.words.Document;
import com.techasians.alertservice.entity.RptFile;
import com.techasians.alertservice.service.ReportFileService;
import com.techasians.alertservice.service.WordGenerationSnapshot;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.Sheet;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;

/** Cache theo file ID trong một tác vụ; đóng workbook và xóa mọi file tải tạm. */
public class WordSourceDocuments implements AutoCloseable {
    private final ReportFileService fileService;
    private final Map<Long, ReportFileService.TemporaryReportFile> temporaryFiles = new LinkedHashMap<>();
    private final Map<Long, Document> documents = new HashMap<>();
    private final Map<Long, Workbook> workbooks = new HashMap<>();

    public WordSourceDocuments(ReportFileService fileService) { this.fileService = fileService; }

    public Document word(RptFile file) throws Exception {
        if (!documents.containsKey(file.getId())) {
            try (InputStream input = Files.newInputStream(temporary(file).getPath())) {
                documents.put(file.getId(), new Document(input));
            }
        }
        return documents.get(file.getId());
    }

    public Workbook excel(RptFile file) throws Exception {
        if (!workbooks.containsKey(file.getId())) {
            try (InputStream input = Files.newInputStream(temporary(file).getPath())) {
                workbooks.put(file.getId(), WorkbookFactory.create(input));
            }
        }
        return workbooks.get(file.getId());
    }

    /** Tên sheet lấy từ mapping; yyyy là năm của ngày kết thúc tuần báo cáo. */
    public ExcelSheet excelSheet(WordGenerationSnapshot.Source source, LocalDate week) throws Exception {
        String pattern = source.getMapping().getSourceSheetName();
        if (pattern == null || pattern.trim().isEmpty()) throw new IllegalArgumentException("Nguồn Excel thiếu source_sheet_name");
        String name = ReportSheetNameResolver.resolve(pattern, week);
        if (source.getFile() == null) return new ExcelSheet(name, null,
                "Thiếu file nguồn " + source.getMapping().getSourceDefinitionId() + " (sheet " + name + ")");
        return new ExcelSheet(name, excel(source.getFile()).getSheet(name));
    }

    public static class ExcelSheet {
        private final String name;
        private final Sheet sheet;
        private final String missingReason;
        public ExcelSheet(String name, Sheet sheet) { this(name, sheet, "Thiếu sheet " + name); }
        private ExcelSheet(String name, Sheet sheet, String missingReason) {
            this.name = name; this.sheet = sheet; this.missingReason = missingReason;
        }
        public String getName() { return name; }
        public Sheet getSheet() { return sheet; }
        public String getMissingReason() { return missingReason; }
    }

    private ReportFileService.TemporaryReportFile temporary(RptFile file) {
        if (!temporaryFiles.containsKey(file.getId())) {
            temporaryFiles.put(file.getId(), fileService.downloadToTemporaryFile(file.getStorageObjectName()));
        }
        return temporaryFiles.get(file.getId());
    }

    @Override
    public void close() throws Exception {
        Exception failure = null;
        for (Workbook workbook : workbooks.values()) {
            try { workbook.close(); }
            catch (Exception exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        for (ReportFileService.TemporaryReportFile file : temporaryFiles.values()) file.close();
        if (failure != null) throw failure;
    }
}
