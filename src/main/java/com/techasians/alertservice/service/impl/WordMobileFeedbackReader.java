package com.techasians.alertservice.service.impl;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Đọc so sánh PA di động theo kỳ báo cáo và chọn nhóm dịch vụ biến động cùng chiều mạnh nhất. */
@Component
public class WordMobileFeedbackReader {
    private static final Logger log = LoggerFactory.getLogger(WordMobileFeedbackReader.class);
    private static final Pattern PERIOD = Pattern.compile("^tb tuần\\s*\\(\\s*(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?"
            + "\\s*[-–—]\\s*(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?\\s*\\)$");

    public Result read(Workbook workbook, String sheetName, LocalDate week, List<String> warnings) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) throw missing("Thiếu sheet " + sheetName);
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        Headers headers = headers(sheet, week, formatter);
        int summaryRow = headers.row + 1;
        if (!"phản ánh báo hỏng dịch vụ".equals(text(sheet, summaryRow, headers.service, formatter))) {
            throw missing("Thiếu dòng Phản ánh báo hỏng dịch vụ ngay dưới header của tuần báo cáo");
        }
        Metric total = metric(sheet, summaryRow, headers);
        Candidate selected = null;
        int expectedOrder = 1;
        // Các nhóm tổng hợp có STT 1..n; khi STT bắt đầu lại ở 1 là bảng dịch vụ chi tiết, không trộn hai cấp.
        if (total.difference.signum() != 0) {
            for (int row = summaryRow + 1; row <= sheet.getLastRowNum(); row++) {
                String name = text(sheet, row, headers.service, formatter);
                String order = text(sheet, row, headers.order, formatter);
                if (name.isEmpty() || !Integer.toString(expectedOrder).equals(order)) break;
                expectedOrder++;
                try {
                    BigDecimal difference = number(cell(sheet, row, headers.difference), false);
                    if (difference.signum() != total.difference.signum()) continue;
                    Metric value = metric(sheet, row, headers);
                    if (selected == null || value.rate.abs().compareTo(selected.metric.rate.abs()) > 0) {
                        selected = new Candidate(formattedValue(cell(sheet, row, headers.service), formatter).trim(), value);
                    }
                } catch (MissingMobileDataException exception) {
                    warnings.add("MOBILE: " + sheetName + " dòng " + (row + 1) + ": " + exception.getMessage());
                }
            }
            if (selected == null) warnings.add("MOBILE: Không có nhóm dịch vụ có tỷ lệ hợp lệ cùng chiều với biến động tổng");
        }
        log.info("MOBILE: Sheet {}, tuần {} - {}: TB/ngày={}, chênh lệch={}, tỷ lệ={}%, PA điển hình={}",
                sheetName, week, week.plusDays(6), total.average, total.difference, total.rate,
                selected == null ? "Không có" : selected.name + " (" + selected.metric.rate + "%)");
        return new Result(total, selected);
    }

    private Headers headers(Sheet sheet, LocalDate week, DataFormatter formatter) {
        List<Headers> matches = new ArrayList<>();
        for (Row row : sheet) {
            for (Cell source : row) {
                if (!matchesPeriod(WordSectionExtractor.normalize(formattedValue(source, formatter)), week)) continue;
                Integer difference = column(sheet, row.getRowNum(), "tăng giảm", formatter);
                Integer rate = column(sheet, row.getRowNum(), "tỷ lệ", formatter);
                Integer service = column(sheet, row.getRowNum(), "dịch vụ", formatter);
                Integer order = column(sheet, row.getRowNum(), "stt", formatter);
                if (service == null && row.getRowNum() > 0) service = column(sheet, row.getRowNum() - 1, "dịch vụ", formatter);
                if (order == null && row.getRowNum() > 0) order = column(sheet, row.getRowNum() - 1, "stt", formatter);
                if (difference == null || rate == null || service == null || order == null) {
                    throw missing("Header tuần báo cáo thiếu Dịch vụ, STT, Tăng giảm hoặc Tỷ lệ");
                }
                matches.add(new Headers(row.getRowNum(), source.getColumnIndex(), difference, rate, service, order));
            }
        }
        if (matches.size() != 1) throw missing(matches.isEmpty()
                ? "Không tìm thấy header TB tuần đúng kỳ " + week + " - " + week.plusDays(6)
                : "Có nhiều header TB tuần cùng kỳ báo cáo");
        return matches.get(0);
    }

    private boolean matchesPeriod(String text, LocalDate week) {
        Matcher match = PERIOD.matcher(text);
        if (!match.matches()) return false;
        LocalDate end = week.plusDays(6);
        return Integer.parseInt(match.group(1)) == week.getDayOfMonth()
                && Integer.parseInt(match.group(2)) == week.getMonthValue()
                && (match.group(3) == null || Integer.parseInt(match.group(3)) == week.getYear())
                && Integer.parseInt(match.group(4)) == end.getDayOfMonth()
                && Integer.parseInt(match.group(5)) == end.getMonthValue()
                && (match.group(6) == null || Integer.parseInt(match.group(6)) == end.getYear());
    }

    private Integer column(Sheet sheet, int row, String name, DataFormatter formatter) {
        Row source = sheet.getRow(row);
        if (source == null) return null;
        Integer result = null;
        for (int index = 0; index < source.getLastCellNum(); index++) {
            if (!name.equals(text(sheet, row, index, formatter))) continue;
            if (result != null) throw missing("Trùng header " + name);
            result = index;
        }
        return result;
    }

    private Metric metric(Sheet sheet, int row, Headers headers) {
        BigDecimal average = number(cell(sheet, row, headers.average), false);
        BigDecimal difference = number(cell(sheet, row, headers.difference), false);
        BigDecimal rate = number(cell(sheet, row, headers.rate), true);
        if (average.signum() < 0) throw missing("TB tuần không được âm");
        if (difference.signum() != rate.signum()) throw missing("Tăng giảm và Tỷ lệ không cùng chiều");
        return new Metric(average, difference, rate);
    }

    private BigDecimal number(Cell cell, boolean percent) {
        if (cell == null) throw missing("Thiếu ô số liệu");
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (!Double.isFinite(value)) throw missing("Số liệu không hợp lệ tại " + cell.getAddress());
            BigDecimal result = BigDecimal.valueOf(value);
            return percent && cell.getCellStyle().getDataFormatString().contains("%") ? result.movePointRight(2) : result;
        }
        if (type == CellType.STRING) {
            String text = cell.getStringCellValue().replace('\u00a0', ' ').trim().replace('\u2212', '-');
            if (percent && text.endsWith("%")) text = text.substring(0, text.length() - 1).trim();
            if (text.contains(",") && text.contains(".")) {
                text = text.lastIndexOf(',') > text.lastIndexOf('.') ? text.replace(".", "").replace(',', '.') : text.replace(",", "");
            } else text = text.replace(',', '.');
            try { return new BigDecimal(text); } catch (NumberFormatException ignored) { }
        }
        throw missing("Ô số liệu trống hoặc lỗi tại " + cell.getAddress());
    }

    private Cell cell(Sheet sheet, int row, int column) {
        for (CellRangeAddress range : sheet.getMergedRegions()) {
            if (range.isInRange(row, column)) { row = range.getFirstRow(); column = range.getFirstColumn(); break; }
        }
        Row source = sheet.getRow(row);
        return source == null ? null : source.getCell(column);
    }

    private String text(Sheet sheet, int row, int column, DataFormatter formatter) {
        return WordSectionExtractor.normalize(formattedValue(cell(sheet, row, column), formatter));
    }

    /** POI 4.1.2 chưa có setUseCachedValuesForFormulaCells; đọc cache mà không tính lại công thức. */
    private String formattedValue(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        if (cell.getCellType() != CellType.FORMULA) return formatter.formatCellValue(cell);
        switch (cell.getCachedFormulaResultType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                CellStyle style = cell.getCellStyle();
                return formatter.formatRawCellContents(cell.getNumericCellValue(), style.getDataFormat(), style.getDataFormatString());
            case BOOLEAN:
                return cell.getBooleanCellValue() ? "TRUE" : "FALSE";
            case ERROR:
                return FormulaError.forInt(cell.getErrorCellValue()).getString();
            default:
                return "";
        }
    }

    private MissingMobileDataException missing(String reason) { return new MissingMobileDataException(reason); }

    private static String integer(BigDecimal value) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(value.setScale(0, RoundingMode.HALF_UP));
    }

    private static String decimal(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static class Headers {
        final int row, average, difference, rate, service, order;
        Headers(int row, int average, int difference, int rate, int service, int order) {
            this.row = row; this.average = average; this.difference = difference;
            this.rate = rate; this.service = service; this.order = order;
        }
    }

    private static class Metric {
        final BigDecimal average, difference, rate;
        Metric(BigDecimal average, BigDecimal difference, BigDecimal rate) {
            this.average = average; this.difference = difference; this.rate = rate;
        }
        String direction() { return difference.signum() > 0 ? "tăng" : "giảm"; }
    }

    private static class Candidate {
        final String name;
        final Metric metric;
        Candidate(String name, Metric metric) { this.name = name; this.metric = metric; }
    }

    public static class Result {
        private final Metric total;
        private final Candidate selected;
        private Result(Metric total, Candidate selected) { this.total = total; this.selected = selected; }
        public String getAverage() { return integer(total.average); }
        public String getChange() {
            if (isUnchanged()) return "không có biến động";
            return total.direction() + " " + decimal(total.rate.abs()) + "%";
        }
        public String getChangeDetails() {
            if (isUnchanged()) return "";
            return " (" + (total.difference.signum() > 0 ? "+" : "-")
                    + integer(total.difference.abs()) + "PA/ngày)";
        }
        public boolean isUnchanged() { return total.difference.signum() == 0; }
        public boolean hasTypicalService() { return selected != null; }
        public String getDirection() { return total.direction(); }
        public String getTypicalService() {
            if (selected == null) return "";
            return WordSectionExtractor.normalize(selected.name).startsWith("lỗi ") ? selected.name : "Lỗi " + selected.name;
        }
        public String getTypicalChange() {
            if (selected == null) return "";
            BigDecimal rate = selected.metric.rate.abs();
            return selected.metric.direction() + " " + (selected.metric.difference.signum() > 0
                    && rate.compareTo(BigDecimal.valueOf(100)) > 0
                    ? decimal(rate.movePointLeft(2)) + " lần" : decimal(rate) + "%");
        }
    }

    public static class MissingMobileDataException extends RuntimeException {
        public MissingMobileDataException(String message) { super(message); }
    }
}
