package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.entity.RptExcelSheetDef;
import com.techasians.alertservice.exception.ReportFileValidationException;
import com.techasians.alertservice.repository.RptExcelSheetDefRepository;
import com.techasians.alertservice.service.ReportValidationService;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Validate file upload cho báo cáo tuần.
 * <p>
 * Thứ tự validate cố định để trả lỗi dễ hiểu nhất:
 * 1. File không null và không rỗng.
 * 2. Dung lượng không vượt quá giới hạn.
 * 3. Extension khớp cấu hình (không phân biệt hoa thường).
 * 4. Nếu là Excel: đủ toàn bộ sheet bắt buộc (rpt_excel_sheet_defs).
 * <p>
 * Chỉ đọc danh sách sheet, không đọc nội dung ô trong Excel.
 */
@Service
public class ReportValidationServiceImpl implements ReportValidationService {

    // Giới hạn dung lượng file — khớp với cấu hình max-file-size trong application.yml
    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024; // 20 MB

    private final RptExcelSheetDefRepository excelSheetDefRepository;

    public ReportValidationServiceImpl(RptExcelSheetDefRepository excelSheetDefRepository) {
        this.excelSheetDefRepository = excelSheetDefRepository;
    }

    @Override
    public void validate(MultipartFile multipartFile, String allowedExtension,
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
            validateExcel(multipartFile, definitionId, weekStartDate);
        } else if ("docx".equals(normalizedExtension)) {
            validateWord(multipartFile);
        }
        // Nếu allowedExtension là loại khác (chưa có trong Phase 2), bỏ qua validate nội dung
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

    /** Chỉ kiểm tra các tên sheet cấu hình, không đọc nội dung sheet. */
    private void validateExcel(MultipartFile multipartFile, Long definitionId, LocalDate weekStartDate) {
        List<RptExcelSheetDef> requiredSheetDefs =
                excelSheetDefRepository.findByDefinitionIdAndStatusOrderBySortOrderAsc(definitionId, 1);
        Set<String> requiredSheetNames = new LinkedHashSet<>();
        for (RptExcelSheetDef sheetDef : requiredSheetDefs) {
            String name = resolveSheetName(sheetDef.getSheetName(), weekStartDate);
            if (!requiredSheetNames.add(name)) {
                throw new ReportFileValidationException("Cấu hình trùng sheet bắt buộc: " + name);
            }
        }

        try (InputStream inputStream = multipartFile.getInputStream();
             OPCPackage opcPackage = OPCPackage.open(inputStream)) {
            XSSFReader reader = new XSSFReader(opcPackage);
            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
            while (sheets.hasNext()) {
                try (InputStream ignored = sheets.next()) {
                    requiredSheetNames.remove(sheets.getSheetName());
                }
            }
            if (!requiredSheetNames.isEmpty()) {
                throw new ReportFileValidationException(
                        "File Excel thiếu các sheet bắt buộc: " + String.join(", ", requiredSheetNames));
            }
        } catch (ReportFileValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ReportFileValidationException(
                    "File Excel không hợp lệ hoặc không mở được: " + exception.getMessage(), exception);
        }
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
