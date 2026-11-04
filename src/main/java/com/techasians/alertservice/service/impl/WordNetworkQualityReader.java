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
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Đọc tỷ lệ PA, KPI và các bảng top tỉnh CLM theo nhãn bảng và kỳ báo cáo. */
@Component
public class WordNetworkQualityReader {
    private static final Logger log = LoggerFactory.getLogger(WordNetworkQualityReader.class);
    private static final Pattern PERIOD = Pattern.compile("^tuần từ\\s+(\\d{1,2})[.,/](\\d{1,2})(?:[.,/](\\d{4}))?"
            + "\\s+đến\\s+(\\d{1,2})[.,/](\\d{1,2})(?:[.,/](\\d{4}))?$");
    private static final Pattern PROVINCE_PREFIX = Pattern.compile("^(?:(?:thành\\s+phố|tỉnh)\\s+)+", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public Result read(Workbook workbook, String sheetName, LocalDate week, List<String> warnings) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) throw missing("Thiếu sheet " + sheetName);
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        Overview overview = part("Tỷ lệ chung", warnings, () -> overview(sheet, week, formatter));
        ServiceResult voice = part("Chỉ tiêu thoại", warnings,
                () -> service(sheet, week, formatter, "Chỉ tiêu thoại", "TOP 5 Tỉnh Thoại tăng cao nhất Tuần", warnings));
        ServiceResult data = part("Chỉ tiêu Data", warnings,
                () -> service(sheet, week, formatter, "Chỉ tiêu Data", "TOP 5 Tỉnh Data tăng cao nhất Tuần", warnings));
        return new Result(overview, voice, data);
    }

    private Overview overview(Sheet sheet, LocalDate week, DataFormatter formatter) {
        Cell total = anchor(sheet, "Tỷ lệ chung", formatter);
        WeekColumns current = weekColumns(sheet, 0, total.getRowIndex(), week, formatter);
        WeekColumns previous = weekColumns(sheet, 0, total.getRowIndex(), week.minusWeeks(1), formatter);
        if (current.headerRow != previous.headerRow) throw missing("Hai kỳ Tỷ lệ chung không cùng bảng");
        BigDecimal pa = count(cell(sheet, total.getRowIndex(), current.pa));
        BigDecimal subscribers = count(cell(sheet, total.getRowIndex(), current.subscribers));
        if (subscribers.signum() == 0) throw missing("Thuê bao Tỷ lệ chung bằng 0");
        BigDecimal rate = nonNegative(cell(sheet, total.getRowIndex(), current.rate));
        BigDecimal previousRate = nonNegative(cell(sheet, total.getRowIndex(), previous.rate));
        log.info("CLM: Sheet {}, tuần {} - {}: PA={}, thuê bao={}, KQ={}, KQ tuần trước={}",
                sheet.getSheetName(), week, week.plusDays(6), pa, subscribers, rate, previousRate);
        return new Overview(pa, rate, previousRate);
    }

    private ServiceResult service(Sheet sheet, LocalDate week, DataFormatter formatter, String title,
                                  String topTitle, List<String> warnings) {
        Cell titleCell = anchor(sheet, title, formatter);
        int start = titleCell.getRowIndex() + 1, end = sheet.getLastRowNum() + 1;
        for (int index = start; index < end; index++) {
            Row row = sheet.getRow(index);
            if (row == null) continue;
            for (Cell candidate : row) {
                if (normalize(display(candidate, formatter)).startsWith("chỉ tiêu ")) { end = index; break; }
            }
        }
        WeekColumns columns = weekColumns(sheet, start, end, week, formatter);
        int kpiColumn = namedColumn(sheet, columns.headerRow, columns.headerRow + 1, "kpis", 0,
                lastColumn(sheet, columns.headerRow, columns.headerRow + 1), formatter);
        int evaluation = namedColumn(sheet, columns.headerRow, columns.headerRow + 1, "đánh giá", 0,
                lastColumn(sheet, columns.headerRow, columns.headerRow + 1), formatter);
        int sourceColumn = namedColumn(sheet, columns.headerRow, columns.headerRow + 1, "nguồn tiếp nhận", 0,
                lastColumn(sheet, columns.headerRow, columns.headerRow + 1), formatter);
        int rowIndex = columns.dataStart;
        while (rowIndex < end && blankSummaryRow(sheet, rowIndex, columns, kpiColumn, sourceColumn, formatter)) rowIndex++;
        if (rowIndex >= end) throw missing("Thiếu dòng tổng của " + title);
        String label = normalize(display(cell(sheet, rowIndex, sourceColumn), formatter));
        if (!Arrays.asList("", "tổng", "toàn quốc", "thoại", "data").contains(label)) {
            throw missing("Thiếu dòng tổng của " + title + "; không lấy số liệu từ dòng tỉnh");
        }
        count(cell(sheet, rowIndex, columns.pa));
        if (count(cell(sheet, rowIndex, columns.subscribers)).signum() == 0) throw missing("Thuê bao " + title + " bằng 0");
        BigDecimal rate = nonNegative(cell(sheet, rowIndex, columns.rate));
        BigDecimal kpi = nonNegative(cell(sheet, rowIndex, kpiColumn));
        String assessment = normalize(display(cell(sheet, rowIndex, evaluation), formatter)).replaceFirst("\\s+kpi$", "");
        if (!"đạt".equals(assessment) && !"không đạt".equals(assessment)) throw missing("Đánh giá " + title + " trống hoặc không hợp lệ");
        List<String> top = part(topTitle, warnings, () -> topProvinces(sheet, topTitle, formatter, warnings));
        log.info("CLM: {}, tuần {} - {}: KQ={}, KPI={}, đánh giá={}, top tỉnh={}",
                title, week, week.plusDays(6), rate, kpi, assessment, top);
        return new ServiceResult(rate, kpi, "đạt".equals(assessment) ? "Đạt" : "Không đạt", top);
    }

    private boolean blankSummaryRow(Sheet sheet, int row, WeekColumns columns, int kpi, int source, DataFormatter formatter) {
        for (int column : new int[] {columns.pa, columns.subscribers, columns.rate, kpi, source}) {
            if (!display(cell(sheet, row, column), formatter).trim().isEmpty()) return false;
        }
        return true;
    }

    private List<String> topProvinces(Sheet sheet, String title, DataFormatter formatter, List<String> warnings) {
        Cell anchor = anchor(sheet, title, formatter);
        CellRangeAddress merged = merged(sheet, anchor.getRowIndex(), anchor.getColumnIndex());
        int headerRow = merged == null ? anchor.getRowIndex() + 1 : merged.getLastRow() + 1;
        int first = anchor.getColumnIndex(), last = merged == null ? lastColumn(sheet, headerRow, headerRow) : merged.getLastColumn();
        int province = namedColumn(sheet, headerRow, headerRow, "tỉnh", first, last, formatter);
        int current = namedColumn(sheet, headerRow, headerRow, "tuần hiện tại", first, last, formatter);
        List<String> result = new ArrayList<>();
        for (int offset = 1; offset <= 5; offset++) {
            int row = headerRow + offset;
            String name = display(cell(sheet, row, province), formatter).replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
            if (name.isEmpty()) break;
            name = PROVINCE_PREFIX.matcher(name).replaceFirst("").trim();
            if (name.isEmpty()) { warnings.add("CLM: " + title + " dòng " + (row + 1) + " thiếu tên tỉnh"); continue; }
            try { result.add(name + ": " + decimal(nonNegative(cell(sheet, row, current))) + "PA"); }
            catch (MissingQualityDataException exception) { warnings.add("CLM: " + title + " dòng " + (row + 1) + ": " + exception.getMessage()); }
        }
        if (result.isEmpty()) throw missing("Không có tỉnh hợp lệ trong bảng " + title);
        if (result.size() < 5) warnings.add("CLM: " + title + " chỉ đọc được " + result.size() + "/5 tỉnh");
        return result;
    }

    private WeekColumns weekColumns(Sheet sheet, int start, int end, LocalDate week, DataFormatter formatter) {
        List<WeekColumns> matches = new ArrayList<>();
        for (int index = start; index < end; index++) {
            Row row = sheet.getRow(index);
            if (row == null) continue;
            for (Cell candidate : row) {
                if (!matchesPeriod(normalize(display(candidate, formatter)), week)) continue;
                CellRangeAddress merged = merged(sheet, index, candidate.getColumnIndex());
                int last = merged == null ? lastGroupColumn(row, candidate.getColumnIndex(), formatter) : merged.getLastColumn();
                int subheader = merged == null ? index + 1 : merged.getLastRow() + 1;
                int first = candidate.getColumnIndex();
                matches.add(new WeekColumns(index, subheader + 1,
                        namedColumn(sheet, subheader, subheader, "pa", first, last, formatter),
                        namedColumn(sheet, subheader, subheader, "thuê bao", first, last, formatter),
                        namedColumn(sheet, subheader, subheader, "kq", first, last, formatter)));
            }
        }
        if (matches.size() != 1) throw missing(matches.isEmpty() ? "Thiếu nhóm header tuần " + week + " - " + week.plusDays(6)
                : "Trùng nhóm header tuần " + week + " - " + week.plusDays(6));
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

    private Cell anchor(Sheet sheet, String label, DataFormatter formatter) {
        Cell result = null;
        for (Row row : sheet) for (Cell cell : row) {
            if (!normalize(label).equals(normalize(display(cell, formatter)))) continue;
            if (result != null) throw missing("Trùng nhãn " + label);
            result = cell;
        }
        if (result == null) throw missing("Không tìm thấy " + label);
        return result;
    }

    private int namedColumn(Sheet sheet, int startRow, int endRow, String label, int first, int last, DataFormatter formatter) {
        Set<Integer> matches = new HashSet<>();
        for (int row = startRow; row <= endRow; row++) for (int column = first; column <= last; column++) {
            if (label.equals(normalize(display(cell(sheet, row, column), formatter)))) matches.add(column);
        }
        if (matches.size() != 1) throw missing((matches.isEmpty() ? "Thiếu" : "Trùng") + " header " + label);
        return matches.iterator().next();
    }

    private int lastGroupColumn(Row row, int first, DataFormatter formatter) {
        for (int index = first + 1; index < row.getLastCellNum(); index++) {
            if (!display(row.getCell(index), formatter).trim().isEmpty()) return index - 1;
        }
        return row.getLastCellNum() - 1;
    }

    private int lastColumn(Sheet sheet, int firstRow, int lastRow) {
        int result = 0;
        for (int index = firstRow; index <= lastRow; index++) {
            Row row = sheet.getRow(index);
            if (row != null) result = Math.max(result, row.getLastCellNum() - 1);
        }
        return result;
    }

    private Cell cell(Sheet sheet, int row, int column) {
        CellRangeAddress range = merged(sheet, row, column);
        if (range != null) { row = range.getFirstRow(); column = range.getFirstColumn(); }
        Row source = sheet.getRow(row);
        return source == null ? null : source.getCell(column);
    }

    private CellRangeAddress merged(Sheet sheet, int row, int column) {
        for (CellRangeAddress range : sheet.getMergedRegions()) if (range.isInRange(row, column)) return range;
        return null;
    }

    private String display(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        if (cell.getCellType() != CellType.FORMULA) return formatter.formatCellValue(cell);
        switch (cell.getCachedFormulaResultType()) {
            case STRING: return cell.getStringCellValue();
            case NUMERIC: return formatter.formatRawCellContents(cell.getNumericCellValue(), cell.getCellStyle().getDataFormat(), cell.getCellStyle().getDataFormatString());
            case BOOLEAN: return cell.getBooleanCellValue() ? "TRUE" : "FALSE";
            case ERROR: return FormulaError.forInt(cell.getErrorCellValue()).getString();
            default: return "";
        }
    }

    private BigDecimal count(Cell cell) {
        BigDecimal value = nonNegative(cell);
        try { value.toBigIntegerExact(); return value; }
        catch (ArithmeticException exception) { throw missing("Số PA/Thuê bao không phải số nguyên tại " + cell.getAddress()); }
    }

    private BigDecimal nonNegative(Cell cell) {
        if (cell == null) throw missing("Thiếu ô số liệu");
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        BigDecimal result = null;
        if (type == CellType.NUMERIC && Double.isFinite(cell.getNumericCellValue())) result = BigDecimal.valueOf(cell.getNumericCellValue());
        if (type == CellType.STRING) {
            String text = cell.getStringCellValue().replace('\u00a0', ' ').trim();
            if (text.contains(",") && text.contains(".")) text = text.lastIndexOf(',') > text.lastIndexOf('.')
                    ? text.replace(".", "").replace(',', '.') : text.replace(",", "");
            else text = text.replace(',', '.');
            try { result = new BigDecimal(text); } catch (NumberFormatException ignored) { }
        }
        if (result == null || result.signum() < 0) throw missing("Số liệu trống hoặc không hợp lệ tại " + cell.getAddress());
        return result;
    }

    private <T> T part(String label, List<String> warnings, Supplier<T> action) {
        try { return action.get(); }
        catch (MissingQualityDataException exception) {
            String warning = "CLM: " + label + ": " + exception.getMessage();
            log.warn(warning);
            warnings.add(warning);
            return null;
        }
    }

    private String normalize(String value) { return WordSectionExtractor.normalize(value); }
    private MissingQualityDataException missing(String reason) { return new MissingQualityDataException(reason); }
    private static String decimal(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static class WeekColumns {
        final int headerRow, dataStart, pa, subscribers, rate;
        WeekColumns(int headerRow, int dataStart, int pa, int subscribers, int rate) {
            this.headerRow = headerRow; this.dataStart = dataStart; this.pa = pa; this.subscribers = subscribers; this.rate = rate;
        }
    }

    public static class Result {
        private final Overview overview;
        private final ServiceResult voice, data;
        private Result(Overview overview, ServiceResult voice, ServiceResult data) { this.overview = overview; this.voice = voice; this.data = data; }
        public Overview getOverview() { return overview; }
        public ServiceResult getVoice() { return voice; }
        public ServiceResult getData() { return data; }
    }

    public static class Overview {
        private final BigDecimal pa, rate, previous;
        private Overview(BigDecimal pa, BigDecimal rate, BigDecimal previous) { this.pa = pa; this.rate = rate; this.previous = previous; }
        public String getDailyPa() { return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"))
                .format(pa.divide(BigDecimal.valueOf(7), 0, RoundingMode.HALF_UP)); }
        public String getRate() { return decimal(rate); }
        public String getPreviousRate() { return decimal(previous); }
        public String getTrend() { return rate.compareTo(previous) > 0 ? "tăng" : rate.compareTo(previous) < 0 ? "giảm" : "không đổi"; }
    }

    public static class ServiceResult {
        private final BigDecimal rate, kpi;
        private final String assessment;
        private final List<String> provinces;
        private ServiceResult(BigDecimal rate, BigDecimal kpi, String assessment, List<String> provinces) {
            this.rate = rate; this.kpi = kpi; this.assessment = assessment; this.provinces = provinces;
        }
        public String getRate() { return decimal(rate); }
        public String getKpi() { return decimal(kpi); }
        public String getAssessment() { return assessment; }
        public boolean hasWarnings() { return provinces != null && !provinces.isEmpty(); }
        public String getWarningCount() { return provinces == null ? "0" : Integer.toString(provinces.size()); }
        public String getWarningsText() { return provinces == null ? "" : String.join("; ", provinces); }
    }

    public static class MissingQualityDataException extends RuntimeException {
        public MissingQualityDataException(String message) { super(message); }
    }
}
