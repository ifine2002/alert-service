package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.entity.RptExcelSheetDef;
import com.techasians.alertservice.exception.ReportFileValidationException;
import com.techasians.alertservice.repository.RptExcelSheetDefRepository;
import com.techasians.alertservice.service.ReportValidationService;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validate file upload cho báo cáo tuần.
 * <p>
 * Thứ tự validate cố định để trả lỗi dễ hiểu nhất:
 * 1. File không null và không rỗng.
 * 2. Dung lượng không vượt quá giới hạn.
 * 3. Extension khớp cấu hình (không phân biệt hoa thường).
 * 4. Nếu là Excel: đủ toàn bộ sheet bắt buộc (rpt_excel_sheet_defs).
 * 5. Riêng definition 1010: Ngày phát hiện đọc được theo quy tắc của WordProductIssueReader.
 */
@Service
public class ReportValidationServiceImpl implements ReportValidationService {

    // Giới hạn dung lượng file — khớp với cấu hình max-file-size trong application.yml
    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024; // 20 MB
    private static final long PRODUCT_ISSUE_DEFINITION_ID = 1010L;
    private static final String DISCOVERY_DATE_HEADER = "Ngày phát hiện";
    private static final String WARNING_HEADER = "Kết quả";

    private final RptExcelSheetDefRepository excelSheetDefRepository;

    public ReportValidationServiceImpl(RptExcelSheetDefRepository excelSheetDefRepository) {
        this.excelSheetDefRepository = excelSheetDefRepository;
    }

    @Override
    public byte[] validate(MultipartFile multipartFile, String allowedExtension,
                         Long definitionId, LocalDate weekStartDate) {
        // Bước 1: File không null và không rỗng
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new ReportFileValidationException("File không được để trống");
        }

        // Bước 2: Kiểm tra dung lượng
        if (multipartFile.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ReportFileValidationException(
                    "File vượt quá giới hạn cho phép. Kích thước tối đa: 20MB"
            );
        }

        // Bước 3: Validate extension
        String uploadedExtension = extractExtension(multipartFile.getOriginalFilename());
        String normalizedExtension = allowedExtension == null ? "" : allowedExtension.trim().toLowerCase(Locale.ROOT);
        if (normalizedExtension.isEmpty()) throw new ReportFileValidationException("Chưa cấu hình extension cho phép");
        String expectedExtension = "." + normalizedExtension;
        if (!uploadedExtension.equals(expectedExtension)) {
            throw new ReportFileValidationException(
                    "Extension không hợp lệ. File phải có định dạng ." + normalizedExtension
            );
        }

        // Bước 4: Kiểm tra file Excel mở được và đủ sheet bắt buộc
        if ("xlsx".equals(normalizedExtension) || "xlsm".equals(normalizedExtension)) {
            return validateExcel(multipartFile, definitionId, weekStartDate);
        } else if ("docx".equals(normalizedExtension)) {
            validateWord(multipartFile);
        }
        // Nếu allowedExtension là loại khác (chưa có trong Phase 2), bỏ qua validate nội dung
        return null;
    }

    @Override
    public void validateWeekStartDate(LocalDate weekStartDate) {
        if (weekStartDate == null) {
            throw new IllegalArgumentException("weekStartDate không được để trống");
        }
        if (weekStartDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("weekStartDate phải là ngày Thứ Hai (Thứ " +
                    weekStartDate.getDayOfWeek().getValue() + " được nhập vào)");
        }
    }

    @Override
    public void validateActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("Thông tin người thực hiện (actor) không được để trống");
        }
        if (actor.length() > 100) {
            throw new IllegalArgumentException("Thông tin người thực hiện không được vượt quá 100 ký tự");
        }
    }

    // ----------------------------------------------------------------
    // Private helpers — Excel
    // ----------------------------------------------------------------

    /** Kiểm tra tên sheet; chỉ đọc nội dung để validate ngày phát hiện của definition 1010. */
    private byte[] validateExcel(MultipartFile multipartFile, Long definitionId, LocalDate weekStartDate) {
        List<RptExcelSheetDef> requiredSheetDefs =
                excelSheetDefRepository.findByDefinitionIdAndStatusOrderBySortOrderAsc(definitionId, 1);
        Set<String> requiredSheetNames = new LinkedHashSet<>();
        for (RptExcelSheetDef sheetDef : requiredSheetDefs) {
            String name = resolveSheetName(sheetDef.getSheetName(), weekStartDate);
            if (!requiredSheetNames.add(name)) {
                throw new ReportFileValidationException("Cấu hình trùng sheet bắt buộc: " + name);
            }
        }

        Set<String> missingSheetNames = new LinkedHashSet<>(requiredSheetNames);
        try (InputStream inputStream = multipartFile.getInputStream();
             OPCPackage opcPackage = OPCPackage.open(inputStream)) {
            XSSFReader reader = new XSSFReader(opcPackage);
            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
            while (sheets.hasNext()) {
                try (InputStream ignored = sheets.next()) {
                    missingSheetNames.remove(sheets.getSheetName());
                }
            }
            if (!missingSheetNames.isEmpty()) {
                throw new ReportFileValidationException(
                        "File Excel thiếu các sheet bắt buộc: " + String.join(", ", missingSheetNames));
            }
            if (Long.valueOf(PRODUCT_ISSUE_DEFINITION_ID).equals(definitionId)) {
                return validateProductIssueDates(opcPackage, requiredSheetNames);
            }
            return null;
        } catch (ReportFileValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ReportFileValidationException(
                    "File Excel không hợp lệ hoặc không mở được: " + exception.getMessage(), exception);
        }
    }

    private byte[] validateProductIssueDates(OPCPackage opcPackage, Set<String> sheetNames) throws IOException {
        if (sheetNames.isEmpty()) {
            throw new ReportFileValidationException("Chưa cấu hình sheet bắt buộc cho definition 1010");
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(opcPackage)) {
            DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("vi-VN"));
            Map<Short, CellStyle> warningStyles = new HashMap<>();
            boolean hasErrors = false;
            for (String sheetName : sheetNames) {
                hasErrors |= writeProductIssueWarnings(workbook.getSheet(sheetName), formatter, warningStyles);
            }
            if (!hasErrors) return null;
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                workbook.write(output);
                return output.toByteArray();
            }
        }
    }

    private boolean writeProductIssueWarnings(Sheet sheet, DataFormatter formatter, Map<Short, CellStyle> warningStyles) {
        Cell header = null;
        for (Row row : sheet) {
            for (Cell cell : row) {
                String value = WordProductIssueReader.formatCellValue(cell, formatter).replace('\u00a0', ' ')
                        .replaceAll("\\s+", " ").trim();
                if (DISCOVERY_DATE_HEADER.equalsIgnoreCase(value)) {
                    header = cell;
                    break;
                }
            }
            if (header != null) break;
        }
        if (header == null) {
            throw new ReportFileValidationException(
                    "Sheet " + sheet.getSheetName() + " thiếu header " + DISCOVERY_DATE_HEADER);
        }

        Row headerRow = header.getRow();
        int warningColumn = -1;
        for (Cell cell : headerRow) {
            if (WARNING_HEADER.equalsIgnoreCase(WordProductIssueReader.formatCellValue(cell, formatter).trim())) {
                warningColumn = cell.getColumnIndex();
                break;
            }
        }
        if (warningColumn < 0) {
            warningColumn = 0;
            for (Row row : sheet) warningColumn = Math.max(warningColumn, row.getLastCellNum());
        }
        Cell warningHeader = headerRow.getCell(warningColumn, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        warningHeader.setCellValue(WARNING_HEADER);
        warningHeader.setCellStyle(copyWarningHeaderStyle(sheet, headerRow, warningColumn, formatter));
        sheet.setColumnWidth(warningColumn, 80 * 256);

        boolean hasErrors = false;
        for (Row row : sheet) {
            if (row.getRowNum() <= header.getRowIndex()) continue;
            Cell previousWarning = row.getCell(warningColumn);
            if (previousWarning != null) previousWarning.setCellType(CellType.BLANK);
            if (isEmptyRow(row, formatter)) continue;
            Cell dateCell = row.getCell(header.getColumnIndex());
            String warning = null;
            if (WordProductIssueReader.formatCellValue(dateCell, formatter).trim().isEmpty()) {
                warning = "Ngày phát hiện không được để trống.";
            } else if (WordProductIssueReader.readDate(dateCell, formatter) == null) {
                warning = "Ngày phát hiện không đúng định dạng d/M/uuuu hoặc d/M/uu, hoặc ngày không hợp lệ.";
            }
            if (warning != null) {
                hasErrors = true;
                Cell warningCell = row.getCell(warningColumn, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                warningCell.setCellValue(warning);
                warningCell.setCellStyle(copyWarningDataStyle(sheet, row, warningColumn, formatter, warningStyles));
            }
        }
        return hasErrors;
    }

    private CellStyle copyWarningHeaderStyle(Sheet sheet, Row headerRow, int warningColumn, DataFormatter formatter) {
        Cell previousHeader = null;
        for (int column = warningColumn - 1; column >= 0; column--) {
            Cell candidate = headerRow.getCell(column);
            if (!WordProductIssueReader.formatCellValue(candidate, formatter).trim().isEmpty()) {
                previousHeader = candidate;
                break;
            }
        }

        return copyWarningStyle(sheet, previousHeader == null ? null : previousHeader.getCellStyle());
    }

    private CellStyle copyWarningDataStyle(Sheet sheet, Row row, int warningColumn,
                                          DataFormatter formatter, Map<Short, CellStyle> warningStyles) {
        CellStyle sourceStyle = null;
        CellStyle emptyCellStyle = null;
        for (int column = warningColumn - 1; column >= 0; column--) {
            Cell candidate = row.getCell(column);
            if (candidate == null) continue;
            // Ưu tiên ô có dữ liệu, tránh lấy font mặc định của ô trống cuối dòng.
            if (!WordProductIssueReader.formatCellValue(candidate, formatter).isBlank()) {
                sourceStyle = candidate.getCellStyle();
                break;
            }
            if (emptyCellStyle == null && candidate.getCellStyle().getIndex() != 0) {
                emptyCellStyle = candidate.getCellStyle();
            }
        }
        if (sourceStyle == null) sourceStyle = emptyCellStyle == null ? sheet.getWorkbook().getCellStyleAt(0) : emptyCellStyle;
        CellStyle cached = warningStyles.get(sourceStyle.getIndex());
        if (cached != null) return cached;
        CellStyle style = copyWarningStyle(sheet, sourceStyle);
        style.setWrapText(true);
        warningStyles.put(sourceStyle.getIndex(), style);
        return style;
    }

    private CellStyle copyWarningStyle(Sheet sheet, CellStyle sourceStyle) {
        XSSFWorkbook workbook = (XSSFWorkbook) sheet.getWorkbook();
        CellStyle style = workbook.createCellStyle();
        XSSFFont font = workbook.createFont();
        if (sourceStyle != null) {
            style.cloneStyleFrom(sourceStyle);
            // Sao chép font riêng để đổi màu cảnh báo mà không làm đổi font ô nguồn.
            font.getCTFont().set(workbook.getFontAt(sourceStyle.getFontIndex()).getCTFont());
        } else {
            font.setBold(true);
        }
        font.setColor(IndexedColors.RED.getIndex());
        style.setFont(font);
        return style;
    }

    private boolean isEmptyRow(Row row, DataFormatter formatter) {
        for (Cell cell : row) {
            if (!WordProductIssueReader.formatCellValue(cell, formatter).isBlank()) return false;
        }
        return true;
    }

    // ----------------------------------------------------------------
    // Private helpers — Word
    // ----------------------------------------------------------------

    /**
     * Mở Word bằng XWPFDocument để xác nhận file thực sự là .docx hợp lệ.
     * Không đọc nội dung — chỉ kiểm tra cấu trúc.
     */
    private void validateWord(MultipartFile multipartFile) {
        try (InputStream inputStream = multipartFile.getInputStream();
             XWPFDocument ignored = new XWPFDocument(inputStream)) {
            // Mở thành công là đủ — XWPFDocument ném exception nếu file hỏng
        } catch (IOException e) {
            throw new ReportFileValidationException("File Word không hợp lệ hoặc bị hỏng: " + e.getMessage(), e);
        }
    }

    private String resolveSheetName(String configuredSheetName, LocalDate weekStartDate) {
        try { return ReportSheetNameResolver.resolve(configuredSheetName, weekStartDate); }
        catch (IllegalArgumentException exception) {
            throw new ReportFileValidationException(exception.getMessage(), exception);
        }
    }

    // ----------------------------------------------------------------
    // Private helpers — Chung
    // ----------------------------------------------------------------

    /**
     * Lấy extension từ tên file, bao gồm dấu chấm. Trả "" nếu không có.
     * Ví dụ: "bao-cao.XLSX" → ".xlsx"
     */
    private String extractExtension(String filename) {
        if (filename == null) {
            return "";
        }
        // Chỉ lấy basename để chống path traversal
        Path p = Path.of(filename).getFileName();
        String basename = p == null ? filename : p.toString();
        int dotIndex = basename.lastIndexOf('.');
        return dotIndex >= 0 ? basename.substring(dotIndex).toLowerCase() : "";
    }
}
