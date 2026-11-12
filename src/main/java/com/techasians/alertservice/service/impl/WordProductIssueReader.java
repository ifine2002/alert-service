package com.techasians.alertservice.service.impl;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Đọc bất cập theo header, không phụ thuộc vị trí cột của một file mẫu. */
@Component
public class WordProductIssueReader {
    private static final DateTimeFormatter ISSUE_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("d/M/uuuu")
                    .withResolverStyle(ResolverStyle.STRICT);
    // Năm hai chữ số trong chuỗi hiển thị được hiểu trong khoảng 2000–2099.
    private static final DateTimeFormatter ISSUE_SHORT_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("d/M/uu")
                    .withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern CONTENT_LABEL = Pattern.compile(
            "(?:\\d+[.)]\\h*|[*•+\\-]\\h*)?(Thực\\s+trạng|Bất\\s+cập|Nguyên\\s+nhân|Đề\\s+xuất)\\h*[:：]\\h*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public Result read(Workbook workbook, String sheetName, LocalDate weekStart) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) throw new MissingIssueDataException("Không tìm thấy sheet " + sheetName);
        DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("vi-VN"));
        Map<String, Integer> columns = new HashMap<>();
        int headerIndex = -1;
        for (int index = 0; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (row == null) continue;
            Map<String, Integer> candidate = new HashMap<>();
            for (Cell cell : row) candidate.put(normalize(formatCellValue(cell, formatter)), cell.getColumnIndex());
            if (candidate.containsKey("ngày phát hiện") && candidate.containsKey("tiến độ")
                    && candidate.containsKey("nội dung")) {
                columns = candidate;
                headerIndex = index;
                break;
            }
        }
        if (headerIndex < 0) throw new MissingIssueDataException("Sheet " + sheetName + " thiếu header bắt buộc");
        Result result = new Result();
        for (int index = headerIndex + 1; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (row == null) continue;
            String title = text(row, columns.get("tên bất cập/lỗi"), formatter);
            String content = text(row, columns.get("nội dung"), formatter);
            if (title.isEmpty() && content.isEmpty()
                    && text(row, columns.get("ngày phát hiện"), formatter).isEmpty()
                    && text(row, columns.get("tiến độ"), formatter).isEmpty()) continue;
            LocalDate date = readDate(row.getCell(columns.get("ngày phát hiện")), formatter);
            if (date == null || date.isBefore(weekStart) || date.isAfter(weekStart.plusDays(6))) continue;
            String status = text(row, columns.get("tiến độ"), formatter);
            Issue issue = new Issue(index + 1, title, content,
                    text(row, columns.get("kết quả"), formatter), status);
            result.issues.add(issue);
            String normalizedStatus = normalize(status);
            if (normalizedStatus.equals("đã hoàn thành")) result.completed++;
            else if (normalizedStatus.equals("đang thực hiện")) result.inProgress++;
            else if (normalizedStatus.equals("đang đánh giá")) result.evaluating++;
            else if (normalizedStatus.equals("không khả thi")) result.infeasible++;
            else {
                result.unclassified++;
                result.warnings.add("Sheet " + sheetName + ", dòng " + (index + 1) + ": Tiến độ chưa phân loại");
            }
        }
        return result;
    }

    /** Dùng chung khi import và sinh báo cáo để thống nhất định dạng ngày được chấp nhận. */
    static LocalDate readDate(Cell cell, DataFormatter formatter) {
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

    private String text(Row row, Integer column, DataFormatter formatter) {
        if (column == null) return "";
        return formatCellValue(row.getCell(column), formatter).trim();
    }

    /** POI 4.1.2 chưa có setUseCachedValuesForFormulaCells; chỉ đọc cache, không tính lại công thức. */
    static String formatCellValue(Cell cell, DataFormatter formatter) {
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

    private String normalize(String value) { return WordSectionExtractor.normalize(value); }

    public static class Result {
        private final List<Issue> issues = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private int completed;
        private int inProgress;
        private int evaluating;
        private int infeasible;
        private int unclassified;
        public List<Issue> getIssues() { return Collections.unmodifiableList(issues); }
        public List<String> getWarnings() { return Collections.unmodifiableList(warnings); }
        public int getCompleted() { return completed; }
        public int getInProgress() { return inProgress; }
        public int getEvaluating() { return evaluating; }
        public int getInfeasible() { return infeasible; }
        public int getUnclassified() { return unclassified; }
        public int getOptimized() { return completed + inProgress; }
        public String getOptimizedRate() { return rate(getOptimized()); }
        public String getEvaluatingRate() { return rate(evaluating); }
        private String rate(int count) {
            if (issues.isEmpty()) return "0";
            return BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(issues.size()), 1, RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString().replace('.', ',');
        }
    }

    public static class Issue {
        private final int rowNumber;
        private final String title;
        private final String content;
        private final String result;
        private final String status;
        public Issue(int rowNumber, String title, String content, String result, String status) {
            this.rowNumber = rowNumber;
            this.title = title;
            this.content = content;
            this.result = result;
            this.status = status;
        }
        public int getRowNumber() { return rowNumber; }
        public String getTitle() { return title; }
        public String getContent() { return content; }
        public String getResult() { return result; }
        public String getStatus() { return status; }
        public String getActuality() { return contentPart("thực trạng"); }
        public String getCause() { return contentPart("nguyên nhân"); }
        public String getProposal() { return contentPart("đề xuất"); }

        private String contentPart(String label) {
            String value = Normalizer.normalize(content.replace('\u00a0', ' '), Normalizer.Form.NFC)
                    .replace("\r\n", "\n").replace('\r', '\n');
            Matcher matcher = CONTENT_LABEL.matcher(value);
            int start = -1;
            while (matcher.find()) {
                if (start >= 0) return value.substring(start, matcher.start()).trim();
                if (WordSectionExtractor.normalize(matcher.group(1)).equals(label)) start = matcher.end();
            }
            return start < 0 ? "" : value.substring(start).trim();
        }
    }

    public static class MissingIssueDataException extends RuntimeException {
        public MissingIssueDataException(String message) { super(message); }
    }
}
