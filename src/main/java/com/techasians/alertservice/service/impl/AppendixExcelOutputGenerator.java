package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.entity.*;
import com.techasians.alertservice.exception.ResourceNotFoundException;
import com.techasians.alertservice.repository.*;
import com.techasians.alertservice.service.ExcelOutputGenerator;
import com.techasians.alertservice.service.ReportFileService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.*;

@Service
public class AppendixExcelOutputGenerator implements ExcelOutputGenerator {
    private static final DateTimeFormatter ISSUE_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("d/M/uuuu")
                    .withResolverStyle(ResolverStyle.STRICT);
    // Năm hai chữ số trong chuỗi hiển thị được hiểu trong khoảng 2000–2099.
    private static final DateTimeFormatter ISSUE_SHORT_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("d/M/uu")
                    .withResolverStyle(ResolverStyle.STRICT);
    private static final long APPENDIX_ONE = 2002L;
    private static final long APPENDIX_TWO = 2003L;
    private static final Set<String> ALLOWED_SERVICES = new HashSet<>(Arrays.asList(
            "di động", "cố định", "sme", "viettel telecom"));

    private final RptDefinitionRepository definitionRepository;
    private final RptOutputBlockDefRepository blockRepository;
    private final RptOutputSourceMapRepository sourceMapRepository;
    private final RptInstanceRepository instanceRepository;
    private final RptFileRepository fileRepository;
    private final ReportFileService reportFileService;

    public AppendixExcelOutputGenerator(RptDefinitionRepository definitionRepository,
                                        RptOutputBlockDefRepository blockRepository,
                                        RptOutputSourceMapRepository sourceMapRepository,
                                        RptInstanceRepository instanceRepository,
                                        RptFileRepository fileRepository,
                                        ReportFileService reportFileService) {
        this.definitionRepository = definitionRepository;
        this.blockRepository = blockRepository;
        this.sourceMapRepository = sourceMapRepository;
        this.instanceRepository = instanceRepository;
        this.fileRepository = fileRepository;
        this.reportFileService = reportFileService;
    }

    @Override
    public boolean supports(Long outputDefinitionId) {
        return Long.valueOf(APPENDIX_ONE).equals(outputDefinitionId)
                || Long.valueOf(APPENDIX_TWO).equals(outputDefinitionId);
    }

    @Override
    public Path generate(Long outputDefinitionId, LocalDate weekStartDate) {
        RptDefinition definition = definitionRepository.findById(outputDefinitionId)
                .orElseThrow(() -> new ResourceNotFoundException("Output definition không tồn tại: " + outputDefinitionId));
        String templateRelativePath = definition.getTemplateRelativePath();
        ClassPathResource templateResource = new ClassPathResource(templateRelativePath);
        if (!templateResource.exists()) {
            throw new IllegalStateException("Không tìm thấy template: " + templateRelativePath);
        }
        Path outputPath = null;
        try {
            outputPath = Files.createTempFile("report-output-", ".xlsx");
            try (InputStream templateInput = templateResource.getInputStream();
                 XSSFWorkbook outputWorkbook = new XSSFWorkbook(templateInput)) {
                replaceTemplateVariables(outputWorkbook, weekStartDate);
                List<RptOutputBlockDef> blocks = blockRepository
                        .findByOutputDefinitionIdAndStatusOrderBySortOrder(outputDefinitionId, 1);
                if (blocks.isEmpty()) throw new IllegalStateException("Output chưa cấu hình block: " + outputDefinitionId);
                for (RptOutputBlockDef block : blocks) {
                    populateBlock(outputWorkbook, block, weekStartDate, outputDefinitionId);
                }
                try (OutputStream output = Files.newOutputStream(outputPath)) {
                    outputWorkbook.write(output);
                }
            }
            return outputPath;
        } catch (RuntimeException | IOException exception) {
            if (outputPath != null) try { Files.deleteIfExists(outputPath); } catch (IOException ignored) { }
            throw new IllegalStateException("Không thể sinh file Excel output " + outputDefinitionId, exception);
        }
    }

    private void replaceTemplateVariables(Workbook workbook, LocalDate weekStartDate) {
        LocalDate weekEndDate = weekStartDate.plusDays(6);
        String weekRange = weekStartDate.format(DateTimeFormatter.ofPattern("dd/MM"))
                + "-"
                + weekEndDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String year = String.valueOf(weekEndDate.getYear());

        for (Sheet sheet : workbook) {
            for (Row row : sheet) {
                for (Cell cell : row) {
                    if (cell.getCellType() == CellType.STRING) {
                        String currentValue = cell.getStringCellValue();
                        String newValue = currentValue
                                .replace("{{WEEK_RANGE}}", weekRange)
                                .replace("{{YEAR}}", year);
                        if (!currentValue.equals(newValue)) {
                            cell.setCellValue(newValue);
                        }
                    }
                }
            }
        }
    }

    private void populateBlock(XSSFWorkbook outputWorkbook, RptOutputBlockDef block,
                               LocalDate weekStartDate, Long outputDefinitionId) throws IOException {
        List<RptOutputSourceMap> maps = sourceMapRepository
                .findByOutputBlockIdAndStatusOrderBySortOrder(block.getId(), 1);
        if (maps.size() != 1) {
            throw new IllegalStateException("Block phải có đúng một nguồn: " + block.getBlockCode());
        }
        RptOutputSourceMap map = maps.get(0);
        RptInstance sourceInstance = instanceRepository
                .findByDefinitionIdAndWeekStartDate(map.getSourceDefinitionId(), weekStartDate)
                .orElseThrow(() -> new IllegalStateException("Thiếu source instance: " + map.getSourceDefinitionId()));
        if (sourceInstance.getCurrentFileId() == null) {
            throw new IllegalStateException("Source chưa có file hiện tại: " + map.getSourceDefinitionId());
        }
        RptFile sourceFile = fileRepository.findById(sourceInstance.getCurrentFileId())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy source file: " + sourceInstance.getCurrentFileId()));
        try (ReportFileService.TemporaryReportFile temporary =
                     reportFileService.downloadToTemporaryFile(sourceFile.getStorageObjectName());
             InputStream input = Files.newInputStream(temporary.getPath());
             XSSFWorkbook sourceWorkbook = new XSSFWorkbook(input)) {
            String configuredSourceSheetName = map.getSourceSheetName();
            String sourceSheetName = resolveSheetName(configuredSourceSheetName, weekStartDate);
            Sheet sourceSheet = sourceWorkbook.getSheet(sourceSheetName);
            if (sourceSheet == null) {
                throw new IllegalStateException("Không tìm thấy sheet nguồn: " + sourceSheetName
                        + ". Cấu hình database: " + configuredSourceSheetName);
            }

            Sheet targetSheet = findAndRenameTargetSheet(
                    outputWorkbook,
                    block.getTargetName(),
                    weekStartDate
            );
            if (Long.valueOf(APPENDIX_TWO).equals(outputDefinitionId)) {
                copyFilteredAppendixTwo(sourceSheet, targetSheet, outputWorkbook, weekStartDate);
            } else if ("INCIDENT_WEEK".equals(block.getBlockCode())
                    || "INCIDENT_YEAR".equals(block.getBlockCode())) {
                copyFilteredByOwner(sourceSheet, targetSheet, outputWorkbook);
            } else {
                copyAllDataRows(sourceSheet, targetSheet, outputWorkbook);
            }
        }
    }

    private String resolveSheetName(String configuredSheetName, LocalDate weekStartDate) {
        if (configuredSheetName == null || configuredSheetName.trim().isEmpty()) {
            throw new IllegalStateException("Tên sheet không được để trống");
        }

        return ReportSheetNameResolver.resolve(configuredSheetName, weekStartDate);
    }

    private Sheet findAndRenameTargetSheet(XSSFWorkbook workbook, String configuredSheetName,
                                           LocalDate weekStartDate) {
        String targetSheetName = resolveSheetName(configuredSheetName, weekStartDate);
        Sheet targetSheet = workbook.getSheet(configuredSheetName);
        if (targetSheet == null) {
            throw new IllegalStateException("Template thiếu sheet: " + configuredSheetName
                    + ". Tên sheet sau khi xử lý: " + targetSheetName);
        }

        if (!configuredSheetName.equals(targetSheetName)) {
            Sheet duplicateSheet = workbook.getSheet(targetSheetName);
            if (duplicateSheet != null && duplicateSheet != targetSheet) {
                throw new IllegalStateException("Tên sheet output bị trùng: " + targetSheetName);
            }

            int sheetIndex = workbook.getSheetIndex(targetSheet);
            workbook.setSheetName(sheetIndex, targetSheetName);
        }

        return targetSheet;
    }

    private void copyFilteredByOwner(Sheet source, Sheet target, Workbook targetWorkbook) {
        int sourceHeaderRowIndex = findHeaderRow(source, "Chủ dịch vụ");
        HeaderStructure sourceHeader = analyzeHeader(source, sourceHeaderRowIndex);
        int ownerColumn = findColumn(source.getRow(sourceHeaderRowIndex), "Chủ dịch vụ");
        TemplateWriteContext context = prepareTemplateWriteContext(sourceHeader, target);
        copyRows(source, target, targetWorkbook, context, sourceHeader.dataStartRowIndex,
                row -> "vtt".equals(normalize(readText(row.getCell(ownerColumn)))));
    }

    private void copyFilteredAppendixTwo(Sheet source, Sheet target, Workbook targetWorkbook,
                                         LocalDate weekStartDate) {
        int sourceHeaderRowIndex = findHeaderRow(source, "Ngày phát hiện");
        HeaderStructure sourceHeader = analyzeHeader(source, sourceHeaderRowIndex);
        Row header = source.getRow(sourceHeaderRowIndex);
        int dateColumn = findColumn(header, "Ngày phát hiện");
        int serviceColumn = findColumn(header, "Dịch vụ");
        LocalDate weekEndDate = weekStartDate.plusDays(6);
        TemplateWriteContext context = prepareTemplateWriteContext(sourceHeader, target);
        DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("vi-VN"));

        copyRows(source, target, targetWorkbook, context, sourceHeader.dataStartRowIndex, row -> {
            LocalDate discoveredDate = readDate(row.getCell(dateColumn), formatter);
            String service = normalize(readText(row.getCell(serviceColumn)));
            return discoveredDate != null
                    && !discoveredDate.isBefore(weekStartDate)
                    && !discoveredDate.isAfter(weekEndDate)
                    && ALLOWED_SERVICES.contains(service);
        });
    }

    private void copyAllDataRows(Sheet source, Sheet target, Workbook targetWorkbook) {
        HeaderStructure targetHeader = findTemplateHeader(target);
        HeaderStructure sourceHeader = findBestSourceHeader(source, targetHeader);
        TemplateWriteContext context = prepareTemplateWriteContext(sourceHeader, target, targetHeader);
        copyRows(source, target, targetWorkbook, context, sourceHeader.dataStartRowIndex, row -> true);
    }

    private void copyRows(Sheet source, Sheet target, Workbook targetWorkbook,
                          TemplateWriteContext context, int sourceDataStartRowIndex,
                          java.util.function.Predicate<Row> keepRow) {
        int targetRowIndex = context.dataStartRowIndex;
        int sequence = 1;

        for (int rowIndex = sourceDataStartRowIndex;
             rowIndex <= source.getLastRowNum();
             rowIndex++) {
            Row sourceRow = source.getRow(rowIndex);

            if (sourceRow == null || !keepRow.test(sourceRow)) {
                continue;
            }

            /*
             * Excel vẫn tính các dòng chỉ có border/style vào getLastRowNum().
             * Không tạo dòng output và không tăng STT nếu các cột được map
             * từ nguồn đều không có dữ liệu thực tế.
             */
            if (!hasMappedData(sourceRow, context.columnMappings)) {
                continue;
            }

            Row targetRow = target.createRow(targetRowIndex);

            if (context.templateRowHeight >= 0) {
                targetRow.setHeight(context.templateRowHeight);
            }

            copyRowByTemplateColumns(
                    sourceRow,
                    targetRow,
                    targetWorkbook,
                    context.columnMappings,
                    sequence
            );

            targetRowIndex++;
            sequence++;
        }
    }

    private boolean hasMappedData(
            Row sourceRow,
            List<ColumnMapping> columnMappings
    ) {
        for (ColumnMapping mapping : columnMappings) {
            if (mapping.generatedSequence) {
                continue;
            }

            Cell sourceCell = sourceRow.getCell(
                    mapping.sourceColumnIndex,
                    Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
            );

            if (sourceCell != null && !readText(sourceCell).isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private void copyRowByTemplateColumns(Row sourceRow, Row targetRow, Workbook targetWorkbook,
                                          List<ColumnMapping> columnMappings, int sequence) {
        for (ColumnMapping mapping : columnMappings) {
            Cell targetCell = targetRow.createCell(mapping.targetColumnIndex);
            if (mapping.generatedSequence) {
                targetCell.setCellValue(sequence);
            } else {
                Cell sourceCell = sourceRow.getCell(mapping.sourceColumnIndex);
                if (sourceCell == null) targetCell.setBlank();
                else copyCellValue(sourceCell, targetCell);
            }
            if (mapping.dataStyle != null) {
                targetCell.setCellStyle(mapping.dataStyle);
            }
        }
    }

    private void copyCellValue(Cell sourceCell, Cell targetCell) {
        switch (sourceCell.getCellType()) {
            case STRING:
                targetCell.setCellValue(sourceCell.getStringCellValue());
                break;
            case NUMERIC:
                targetCell.setCellValue(sourceCell.getNumericCellValue());
                break;
            case BOOLEAN:
                targetCell.setCellValue(sourceCell.getBooleanCellValue());
                break;
            case FORMULA:
                copyFormulaCachedValue(sourceCell, targetCell);
                break;
            case ERROR:
                targetCell.setCellErrorValue(sourceCell.getErrorCellValue());
                break;
            default:
                targetCell.setBlank();
                break;
        }
    }

    /**
     * Chỉ copy kết quả đã tính của công thức.
     * Không copy nguyên công thức vì workbook output có thể không chứa
     * sheet hoặc named range mà công thức trong workbook nguồn tham chiếu.
     */
    private void copyFormulaCachedValue(Cell sourceCell, Cell targetCell) {
        CellType cachedType = sourceCell.getCachedFormulaResultType();

        switch (cachedType) {
            case STRING:
                targetCell.setCellValue(sourceCell.getStringCellValue());
                break;
            case NUMERIC:
                targetCell.setCellValue(sourceCell.getNumericCellValue());
                break;
            case BOOLEAN:
                targetCell.setCellValue(sourceCell.getBooleanCellValue());
                break;
            case ERROR:
                targetCell.setCellErrorValue(sourceCell.getErrorCellValue());
                break;
            default:
                targetCell.setBlank();
                break;
        }
    }

    private TemplateWriteContext prepareTemplateWriteContext(HeaderStructure sourceHeader, Sheet target) {
        return prepareTemplateWriteContext(sourceHeader, target, findTemplateHeader(target));
    }

    private TemplateWriteContext prepareTemplateWriteContext(HeaderStructure sourceHeader, Sheet target,
                                                              HeaderStructure targetHeader) {
        validateUniqueKeys(targetHeader);
        List<ColumnMapping> mappings = new ArrayList<>();
        Map<String, List<Integer>> sourceColumnsByLeafKey = buildColumnsByLeafKey(sourceHeader);

        for (int targetColumn = 0; targetColumn < targetHeader.columnKeys.size(); targetColumn++) {
            String targetKey = targetHeader.columnKeys.get(targetColumn);
            if (targetKey.isEmpty()) continue;
            CellStyle dataStyle = createDataStyle(target, targetHeader, targetColumn);
            if ("stt".equals(leafKey(targetKey))) {
                mappings.add(ColumnMapping.generatedSequence(targetColumn, dataStyle));
                continue;
            }

            Integer sourceColumn = sourceHeader.uniqueColumnByKey.get(targetKey);
            if (sourceColumn == null) {
                List<Integer> columnsWithSameLeaf = sourceColumnsByLeafKey.get(leafKey(targetKey));
                if (columnsWithSameLeaf != null && columnsWithSameLeaf.size() == 1) {
                    sourceColumn = columnsWithSameLeaf.get(0);
                }
            }
            if (sourceColumn != null) {
                mappings.add(ColumnMapping.fromSource(targetColumn, sourceColumn, dataStyle));
            }
        }

        short templateRowHeight = findTemplateRowHeight(
                target,
                targetHeader
        );

        clearDataRows(target, targetHeader.dataStartRowIndex);

        return new TemplateWriteContext(
                targetHeader.dataStartRowIndex,
                mappings,
                templateRowHeight
        );
    }

    private CellStyle createDataStyle(
            Sheet target,
            HeaderStructure targetHeader,
            int columnIndex
    ) {
        Cell sampleCell = findTemplateStyleCell(
                target,
                targetHeader,
                columnIndex
        );

        if (sampleCell == null) {
            return null;
        }

        CellStyle style = target
                .getWorkbook()
                .createCellStyle();

        style.cloneStyleFrom(
                sampleCell.getCellStyle()
        );

        return style;
    }

    /**
     * Tìm ô mẫu style trong vùng dữ liệu của cùng một cột.
     *
     * Ô mẫu có thể không chứa giá trị nhưng vẫn có font, border và định dạng.
     * Vì vậy phải dùng RETURN_NULL_AND_BLANK để giữ lại ô BLANK có style.
     * Không lấy style từ header vì style header thường in đậm hoặc có màu nền.
     */
    private Cell findTemplateStyleCell(
            Sheet target,
            HeaderStructure targetHeader,
            int columnIndex
    ) {
        for (int rowIndex = targetHeader.dataStartRowIndex;
             rowIndex <= target.getLastRowNum();
             rowIndex++) {
            Row row = target.getRow(rowIndex);

            if (row == null) {
                continue;
            }

            Cell cell = row.getCell(
                    columnIndex,
                    Row.MissingCellPolicy.RETURN_NULL_AND_BLANK
            );

            if (cell != null) {
                return cell;
            }
        }

        return null;
    }

    /**
     * Lấy chiều cao từ dòng dữ liệu mẫu đầu tiên trong template.
     * Row height là thuộc tính của Row, không nằm trong CellStyle.
     */
    private short findTemplateRowHeight(
            Sheet target,
            HeaderStructure targetHeader
    ) {
        for (int rowIndex = targetHeader.dataStartRowIndex;
             rowIndex <= target.getLastRowNum();
             rowIndex++) {
            Row row = target.getRow(rowIndex);

            if (row != null && row.getPhysicalNumberOfCells() > 0) {
                return row.getHeight();
            }
        }

        return -1;
    }

    private HeaderStructure findTemplateHeader(Sheet sheet) {
        for (int rowIndex = 0; rowIndex <= Math.min(sheet.getLastRowNum(), 30); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null && findOptionalColumn(row, "STT") >= 0) {
                return analyzeHeader(sheet, rowIndex);
            }
        }
        throw new IllegalStateException("Template sheet " + sheet.getSheetName() + " thiếu header STT");
    }

    private HeaderStructure findBestSourceHeader(Sheet source, HeaderStructure targetHeader) {
        HeaderStructure bestHeader = null;
        int bestMatchCount = -1;
        Set<String> expectedNames = new HashSet<>();
        for (String key : targetHeader.columnKeys) if (!key.isEmpty()) expectedNames.add(leafKey(key));
        int maximumRowIndex = source.getLastRowNum();
        for (int rowIndex = 0; rowIndex <= maximumRowIndex; rowIndex++) {
            Row row = source.getRow(rowIndex);
            if (row == null || row.getLastCellNum() <= 0) continue;
            boolean candidateRow = false;
            for (Cell cell : row) {
                if (expectedNames.contains(normalizeHeaderPart(readText(cell)))) { candidateRow = true; break; }
            }
            if (!candidateRow) continue;
            HeaderStructure candidate = analyzeHeader(source, rowIndex);
            int matchCount = countMatchingKeys(candidate, targetHeader);
            if (matchCount > bestMatchCount) {
                bestMatchCount = matchCount;
                bestHeader = candidate;
            }
        }
        if (bestHeader == null || bestMatchCount == 0) {
            throw new IllegalStateException("Không xác định được header nguồn tại sheet " + source.getSheetName());
        }
        return bestHeader;
    }

    private int countMatchingKeys(HeaderStructure source, HeaderStructure target) {
        Set<String> sourceFullKeys = new HashSet<>(source.columnKeys);
        Set<String> sourceLeafKeys = new HashSet<>();
        for (String key : source.columnKeys) sourceLeafKeys.add(leafKey(key));
        int count = 0;
        for (String key : target.columnKeys) {
            if (key.isEmpty() || "stt".equals(leafKey(key))) continue;
            if (sourceFullKeys.contains(key) || sourceLeafKeys.contains(leafKey(key))) count++;
        }
        return count;
    }

    private HeaderStructure analyzeHeader(Sheet sheet, int firstHeaderRowIndex) {
        int lastHeaderRowIndex = firstHeaderRowIndex;
        for (CellRangeAddress mergedRegion : sheet.getMergedRegions()) {
            if (mergedRegion.getFirstRow() <= firstHeaderRowIndex
                    && mergedRegion.getLastRow() >= firstHeaderRowIndex) {
                lastHeaderRowIndex = Math.max(lastHeaderRowIndex, mergedRegion.getLastRow());
            }
        }

        int lastColumnIndex = findLastHeaderColumn(sheet, firstHeaderRowIndex, lastHeaderRowIndex);
        List<String> columnKeys = new ArrayList<>();
        Map<String, List<Integer>> columnsByKey = new LinkedHashMap<>();
        for (int columnIndex = 0; columnIndex <= lastColumnIndex; columnIndex++) {
            List<String> parts = new ArrayList<>();
            for (int rowIndex = firstHeaderRowIndex; rowIndex <= lastHeaderRowIndex; rowIndex++) {
                String part = normalizeHeaderPart(readMergedHeaderText(sheet, rowIndex, columnIndex));
                if (!part.isEmpty() && (parts.isEmpty() || !parts.get(parts.size() - 1).equals(part))) {
                    parts.add(part);
                }
            }
            String key = String.join(" > ", parts);
            columnKeys.add(key);
            if (!key.isEmpty()) columnsByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(columnIndex);
        }

        Map<String, Integer> uniqueColumnByKey = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : columnsByKey.entrySet()) {
            if (entry.getValue().size() == 1) uniqueColumnByKey.put(entry.getKey(), entry.getValue().get(0));
        }
        return new HeaderStructure(sheet, firstHeaderRowIndex, lastHeaderRowIndex,
                lastHeaderRowIndex + 1, columnKeys, columnsByKey, uniqueColumnByKey);
    }

    private int findLastHeaderColumn(Sheet sheet, int firstRowIndex, int lastRowIndex) {
        int lastColumnIndex = -1;
        for (int rowIndex = firstRowIndex; rowIndex <= lastRowIndex; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null) lastColumnIndex = Math.max(lastColumnIndex, row.getLastCellNum() - 1);
        }
        for (CellRangeAddress mergedRegion : sheet.getMergedRegions()) {
            boolean overlapsHeader = mergedRegion.getFirstRow() <= lastRowIndex
                    && mergedRegion.getLastRow() >= firstRowIndex;
            if (overlapsHeader) lastColumnIndex = Math.max(lastColumnIndex, mergedRegion.getLastColumn());
        }
        return lastColumnIndex;
    }

    private String readMergedHeaderText(Sheet sheet, int rowIndex, int columnIndex) {
        Cell cell = getCell(sheet, rowIndex, columnIndex);
        String value = readText(cell);
        if (!value.isEmpty()) return value;
        for (CellRangeAddress mergedRegion : sheet.getMergedRegions()) {
            if (mergedRegion.isInRange(rowIndex, columnIndex)) {
                return readText(getCell(sheet, mergedRegion.getFirstRow(), mergedRegion.getFirstColumn()));
            }
        }
        return "";
    }

    private Cell getCell(Sheet sheet, int rowIndex, int columnIndex) {
        Row row = sheet.getRow(rowIndex);
        return row == null ? null : row.getCell(columnIndex);
    }

    private String normalizeHeaderPart(String value) {
        return normalize(value).replaceAll("\\s+", " ");
    }

    private String leafKey(String key) {
        int separatorIndex = key.lastIndexOf(" > ");
        return separatorIndex < 0 ? key : key.substring(separatorIndex + 3);
    }

    private Map<String, List<Integer>> buildColumnsByLeafKey(HeaderStructure header) {
        Map<String, List<Integer>> result = new LinkedHashMap<>();
        for (int columnIndex = 0; columnIndex < header.columnKeys.size(); columnIndex++) {
            String key = header.columnKeys.get(columnIndex);
            if (!key.isEmpty()) result.computeIfAbsent(leafKey(key), ignored -> new ArrayList<>()).add(columnIndex);
        }
        return result;
    }

    private void validateUniqueKeys(HeaderStructure header) {
        for (Map.Entry<String, List<Integer>> entry : header.columnsByKey.entrySet()) {
            if (entry.getValue().size() > 1) {
                throw new IllegalStateException("Header key bị trùng tại sheet " + header.sheet.getSheetName()
                        + ": \"" + entry.getKey() + "\" ở các cột " + entry.getValue());
            }
        }
    }

    private void clearDataRows(Sheet sheet, int dataStartRowIndex) {
        for (int index = sheet.getLastRowNum(); index >= dataStartRowIndex; index--) {
            Row row = sheet.getRow(index);
            if (row != null) sheet.removeRow(row);
        }
    }

    private static class HeaderStructure {
        private final Sheet sheet;
        private final int firstHeaderRowIndex;
        private final int lastHeaderRowIndex;
        private final int dataStartRowIndex;
        private final List<String> columnKeys;
        private final Map<String, List<Integer>> columnsByKey;
        private final Map<String, Integer> uniqueColumnByKey;

        private HeaderStructure(Sheet sheet, int firstHeaderRowIndex, int lastHeaderRowIndex,
                                int dataStartRowIndex, List<String> columnKeys,
                                Map<String, List<Integer>> columnsByKey,
                                Map<String, Integer> uniqueColumnByKey) {
            this.sheet = sheet;
            this.firstHeaderRowIndex = firstHeaderRowIndex;
            this.lastHeaderRowIndex = lastHeaderRowIndex;
            this.dataStartRowIndex = dataStartRowIndex;
            this.columnKeys = columnKeys;
            this.columnsByKey = columnsByKey;
            this.uniqueColumnByKey = uniqueColumnByKey;
        }
    }

    private static class ColumnMapping {
        private final int targetColumnIndex;
        private final int sourceColumnIndex;
        private final boolean generatedSequence;
        private final CellStyle dataStyle;

        private ColumnMapping(int targetColumnIndex, int sourceColumnIndex,
                              boolean generatedSequence, CellStyle dataStyle) {
            this.targetColumnIndex = targetColumnIndex;
            this.sourceColumnIndex = sourceColumnIndex;
            this.generatedSequence = generatedSequence;
            this.dataStyle = dataStyle;
        }

        private static ColumnMapping generatedSequence(int targetColumnIndex, CellStyle dataStyle) {
            return new ColumnMapping(targetColumnIndex, -1, true, dataStyle);
        }

        private static ColumnMapping fromSource(int targetColumnIndex, int sourceColumnIndex, CellStyle dataStyle) {
            return new ColumnMapping(targetColumnIndex, sourceColumnIndex, false, dataStyle);
        }
    }

    private static class TemplateWriteContext {
        private final int dataStartRowIndex;
        private final List<ColumnMapping> columnMappings;
        private final short templateRowHeight;

        private TemplateWriteContext(
                int dataStartRowIndex,
                List<ColumnMapping> columnMappings,
                short templateRowHeight
        ) {
            this.dataStartRowIndex = dataStartRowIndex;
            this.columnMappings = columnMappings;
            this.templateRowHeight = templateRowHeight;
        }
    }

    private int findHeaderRow(Sheet sheet, String requiredHeader) {
        for (int index = 0; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (row != null && findOptionalColumn(row, requiredHeader) >= 0) return index;
        }
        throw new IllegalStateException("Sheet " + sheet.getSheetName() + " thiếu cột " + requiredHeader);
    }

    private int findColumn(Row row, String header) {
        int result = findOptionalColumn(row, header);
        if (result < 0) throw new IllegalStateException("Thiếu cột " + header);
        return result;
    }

    private int findOptionalColumn(Row row, String header) {
        for (Cell cell : row) if (normalize(readText(cell)).equals(normalize(header))) return cell.getColumnIndex();
        return -1;
    }

    private String readText(Cell cell) {
        if (cell == null) return "";
        return new DataFormatter(Locale.forLanguageTag("vi-VN")).formatCellValue(cell).trim();
    }

    private String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }

    private LocalDate readDate(Cell cell, DataFormatter formatter) {
        if (cell == null) return null;
        String value = formatCellValue(cell, formatter).trim();
        if (value.isEmpty()) return null;
        try {
            return LocalDate.parse(value, ISSUE_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(value, ISSUE_SHORT_DATE_FORMATTER);
            } catch (DateTimeParseException invalidDate) {
                return null;
            }
        }
    }

    private String formatCellValue(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        if (cell.getCellType() != CellType.FORMULA) return formatter.formatCellValue(cell);
        switch (cell.getCachedFormulaResultType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                CellStyle style = cell.getCellStyle();
                Workbook workbook = cell.getSheet().getWorkbook();
                boolean date1904 = workbook instanceof XSSFWorkbook && ((XSSFWorkbook) workbook).isDate1904();
                return formatter.formatRawCellContents(cell.getNumericCellValue(), style.getDataFormat(),
                        style.getDataFormatString(), date1904);
            case BOOLEAN:
                return cell.getBooleanCellValue() ? "TRUE" : "FALSE";
            case ERROR:
                return FormulaError.forInt(cell.getErrorCellValue()).getString();
            default:
                return "";
        }
    }
}
