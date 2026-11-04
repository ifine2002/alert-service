package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.entity.RptExcelHeaderDef;
import com.techasians.alertservice.entity.RptExcelSheetDef;
import com.techasians.alertservice.exception.ReportFileValidationException;
import com.techasians.alertservice.repository.RptExcelHeaderDefRepository;
import com.techasians.alertservice.repository.RptExcelSheetDefRepository;
import com.techasians.alertservice.service.ReportValidationService;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

/**
 * Validate file upload cho báo cáo tuần.
 * <p>
 * Thứ tự validate cố định để trả lỗi dễ hiểu nhất:
 * 1. File không null và không rỗng.
 * 2. Dung lượng không vượt quá giới hạn.
 * 3. Extension khớp cấu hình (không phân biệt hoa thường).
 * 4. Nếu là Excel: đủ toàn bộ sheet bắt buộc (rpt_excel_sheet_defs).
 * 5. Nếu là Excel: có đủ tên header cấu hình, không phụ thuộc tọa độ.
 * <p>
 * HIỆU NĂNG: Dùng POI Event API (SAX) thay vì WorkbookFactory.
 * SAX đọc XML theo dòng, không load toàn bộ file vào RAM.
 * Khi đã đọc xong tất cả ô cần kiểm tra, dừng luôn — không đọc tiếp.
 */
@Service
public class ReportValidationServiceImpl implements ReportValidationService {

    // Giới hạn dung lượng file — khớp với cấu hình max-file-size trong application.yml
    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024; // 20 MB

    private final RptExcelSheetDefRepository excelSheetDefRepository;
    private final RptExcelHeaderDefRepository excelHeaderDefRepository;

    public ReportValidationServiceImpl(RptExcelSheetDefRepository excelSheetDefRepository,
                                       RptExcelHeaderDefRepository excelHeaderDefRepository) {
        this.excelSheetDefRepository = excelSheetDefRepository;
        this.excelHeaderDefRepository = excelHeaderDefRepository;
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

        // Bước 4 & 5: Validate nội dung Excel
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

    /**
     * Validate file Excel bằng POI Event API (SAX):
     * 1. Dùng XSSFReader để duyệt từng sheet theo tên — không load sheet thừa.
     * 2. Dùng SAX quét nguyên tên header, không giới hạn tọa độ hay dòng bắt đầu.
     * 3. Dừng khi đã tìm đủ tên header bắt buộc.
     */
    private void validateExcel(MultipartFile multipartFile, Long definitionId, LocalDate weekStartDate) {
        // Query 1: lấy toàn bộ sheet bắt buộc.
        List<RptExcelSheetDef> requiredSheetDefs =
                excelSheetDefRepository.findByDefinitionIdAndStatusOrderBySortOrderAsc(definitionId, 1);

        // Chuẩn bị dữ liệu để query toàn bộ header chỉ một lần.
        List<Long> sheetDefIds = new ArrayList<>();
        Map<String, RptExcelSheetDef> sheetDefByName = new HashMap<>();
        for (RptExcelSheetDef sheetDef : requiredSheetDefs) {
            sheetDefIds.add(sheetDef.getId());
            String requiredSheetName = resolveSheetName(sheetDef.getSheetName(), weekStartDate);
            if (sheetDefByName.putIfAbsent(requiredSheetName, sheetDef) != null) {
                throw new ReportFileValidationException("Cấu hình trùng sheet bắt buộc: " + requiredSheetName);
            }
        }

        // Query 2: lấy header của tất cả sheet, thay vì query riêng cho từng sheet.
        List<RptExcelHeaderDef> allRequiredHeaders = sheetDefIds.isEmpty() ? java.util.Collections.emptyList() :
                excelHeaderDefRepository
                        .findBySheetDefIdInAndStatusOrderBySheetDefIdAscIdAsc(
                                sheetDefIds, 1
                        );

        Map<Long, List<RptExcelHeaderDef>> headersBySheetDefId = new HashMap<>();
        for (RptExcelHeaderDef header : allRequiredHeaders) {
            List<RptExcelHeaderDef> sheetHeaders = headersBySheetDefId.get(header.getSheetDefId());
            if (sheetHeaders == null) {
                sheetHeaders = new ArrayList<>();
                headersBySheetDefId.put(header.getSheetDefId(), sheetHeaders);
            }
            sheetHeaders.add(header);
        }

        try (InputStream inputStream = multipartFile.getInputStream();
             OPCPackage opcPackage = OPCPackage.open(inputStream)) {

            XSSFReader xssfReader = new XSSFReader(opcPackage);
            SharedStrings sharedStrings = xssfReader.getSharedStringsTable();
            StylesTable stylesTable = xssfReader.getStylesTable();
            XSSFReader.SheetIterator sheetIterator =
                    (XSSFReader.SheetIterator) xssfReader.getSheetsData();

            Set<String> foundSheetNames = new HashSet<>();

            // Duyệt workbook đúng một lần. Mỗi stream được validate và đóng ngay.
            while (sheetIterator.hasNext()) {
                try (InputStream sheetStream = sheetIterator.next()) {
                    String sheetName = sheetIterator.getSheetName();
                    RptExcelSheetDef sheetDef = sheetDefByName.get(sheetName);

                    if (sheetDef == null) {
                        // Sheet không nằm trong cấu hình nên không cần đọc nội dung.
                        continue;
                    }

                    foundSheetNames.add(sheetName);
                    List<RptExcelHeaderDef> requiredHeaders = headersBySheetDefId.get(sheetDef.getId());
                    if (requiredHeaders != null && !requiredHeaders.isEmpty()) {
                        validateSheetHeaders(
                                sheetName, sheetStream, requiredHeaders, sharedStrings, stylesTable
                        );
                    }
                }
            }

            // Sau khi duyệt xong mới kết luận sheet bắt buộc nào không tồn tại.
            List<String> missingSheets = new ArrayList<>();
            for (RptExcelSheetDef sheetDef : requiredSheetDefs) {
                String requiredSheetName = resolveSheetName(sheetDef.getSheetName(), weekStartDate);
                if (!foundSheetNames.contains(requiredSheetName)) {
                    missingSheets.add(requiredSheetName);
                }
            }

            if (!missingSheets.isEmpty()) {
                throw new ReportFileValidationException(
                        "File Excel thiếu các sheet bắt buộc: " + String.join(", ", missingSheets)
                );
            }
        } catch (ReportFileValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ReportFileValidationException(
                    "File Excel không hợp lệ hoặc không mở được: " + e.getMessage(), e
            );
        }
    }

    /**
     * Dùng SAX tìm nguyên tên header trong một ô bất kỳ của worksheet.
     * Gom toàn bộ header thiếu rồi báo lỗi một lần.
     */
    private void validateSheetHeaders(String sheetName,
                                      InputStream sheetStream,
                                      List<RptExcelHeaderDef> requiredHeaders,
                                      SharedStrings sharedStrings,
                                      StylesTable stylesTable) {
        HeaderValidationHandler handler = new HeaderValidationHandler(requiredHeaders);

        try {
            SAXParserFactory saxFactory = SAXParserFactory.newInstance();
            // Worksheet XML dùng namespace. Nếu không bật, XSSFSheetXMLHandler
            // không nhận diện được thẻ row/c và callback cell() sẽ không chạy.
            saxFactory.setNamespaceAware(true);
            SAXParser saxParser = saxFactory.newSAXParser();
            XMLReader xmlReader = saxParser.getXMLReader();

            // XSSFSheetXMLHandler chuyển đổi cell reference (A1, B2,...) thành (row, col) số nguyên
            // và giải mã shared string tự động — không cần tự xử lý
            XSSFSheetXMLHandler sheetXMLHandler = new XSSFSheetXMLHandler(
                    stylesTable, sharedStrings, handler, false
            );
            xmlReader.setContentHandler(sheetXMLHandler);
            xmlReader.parse(new InputSource(sheetStream));

        } catch (StopParsingException e) {
            // Đây là cơ chế dừng sớm — không phải lỗi thật
        } catch (Exception e) {
            throw new ReportFileValidationException(
                    "Không thể đọc nội dung sheet \"" + sheetName + "\": " + e.getMessage(), e
            );
        }

        // Kiểm tra header nào chưa được tìm thấy
        List<String> missingHeaders = handler.getMissingHeaders();
        if (!missingHeaders.isEmpty()) {
            throw new ReportFileValidationException(
                    "Sheet \"" + sheetName + "\" thiếu hoặc sai header bắt buộc: "
                            + String.join(", ", missingHeaders)
            );
        }
    }


    // ----------------------------------------------------------------
    // Inner class — SAX Handler
    // ----------------------------------------------------------------

    /** So sánh nguyên tên sau chuẩn hóa Unicode/khoảng trắng, không dùng contains. */
    private static class HeaderValidationHandler implements XSSFSheetXMLHandler.SheetContentsHandler {

        // LinkedHashMap giữ thứ tự header từ query đã sort, giúp message lỗi ổn định.
        private final Map<String, String> expectedCellValues = new LinkedHashMap<>();

        // Set tránh phần tử trùng và cho phép contains() trung bình O(1).
        private final Set<String> foundHeaders = new HashSet<>();

        HeaderValidationHandler(List<RptExcelHeaderDef> requiredHeaders) {
            for (RptExcelHeaderDef header : requiredHeaders) {
                String key = normalizeHeader(header.getHeaderName());
                if (key.isEmpty()) throw new ReportFileValidationException("Tên header cấu hình không được để trống");
                expectedCellValues.putIfAbsent(key, header.getHeaderName().trim());
            }
        }

        @Override
        public void startRow(int rowIndex) {
            // Không cố định vị trí header.
        }

        @Override
        public void endRow(int rowIndex) {
            if (foundHeaders.size() == expectedCellValues.size()) throw new StopParsingException();
        }

        /**
         * Callback cho từng cell.
         *
         * @param cellReference  Ví dụ: "A1", "B3", "C10"
         * @param formattedValue Giá trị đã được XSSFSheetXMLHandler format (đã giải mã shared string)
         * @param comment        Comment của cell (không dùng)
         */
        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            String key = normalizeHeader(formattedValue);
            if (expectedCellValues.containsKey(key)) foundHeaders.add(key);
        }

        /**
         * Trả về danh sách header_name chưa tìm thấy hoặc không khớp.
         */
        List<String> getMissingHeaders() {
            List<String> missing = new ArrayList<>();
            for (Map.Entry<String, String> entry : expectedCellValues.entrySet()) {
                if (!foundHeaders.contains(entry.getKey())) {
                    missing.add(entry.getValue());
                }
            }
            return missing;
        }

    }

    private static String normalizeHeader(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFC).replaceAll("[\\s\\p{Zs}]+", " ")
                .trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Exception nội bộ dùng để dừng SAX parser sớm khi đã tìm đủ tên header.
     * <p>
     * SAX không có cơ chế dừng giữa chừng nên ném RuntimeException là cách
     * thông dụng nhất để thoát sớm khỏi xmlReader.parse().
     */
    private static class StopParsingException extends RuntimeException {
        StopParsingException() {
            // Bỏ qua stack trace để tránh tốn thời gian tạo stack trace không cần thiết
            super(null, null, true, false);
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
