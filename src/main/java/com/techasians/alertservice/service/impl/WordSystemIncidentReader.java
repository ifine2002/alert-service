package com.techasians.alertservice.service.impl;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Đọc các bảng so sánh sự cố hệ thống, trạm và cáp trong TH bằng cùng bộ xử lý kỳ và ô số liệu. */
@Component
public class WordSystemIncidentReader {
    private static final Pattern PERIOD = Pattern.compile(
            "(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?\\s*[-–—]\\s*"
                    + "(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?");
    private static final List<String> LABELS = Arrays.asList("số lượng", "pa", "tb ảnh hưởng");

    public Result read(Workbook workbook, String sheetName, LocalDate week) {
        Table table = findTable(workbook, sheetName, week, TableType.SYSTEM);
        // Phần 1.1 chỉ dùng chênh lệch tuyệt đối của số sự cố, không yêu cầu ô công thức SC/TB.
        Metric incidents = readCounts(table, "số lượng");
        Metric complaints = readComparison(table, "pa");
        return new Result(incidents, complaints);
    }

    public StationResult readStation(Workbook workbook, String sheetName, LocalDate week) {
        return readStationComparison(workbook, sheetName, week, TableType.STATION);
    }

    public StationResult readCable(Workbook workbook, String sheetName, LocalDate week) {
        return readStationComparison(workbook, sheetName, week, TableType.CABLE);
    }

    private StationResult readStationComparison(Workbook workbook, String sheetName, LocalDate week, TableType type) {
        Table table = findTable(workbook, sheetName, week, type);
        return new StationResult(readComparison(table, "số lượng"), readComparison(table, "pa"),
                readComparison(table, "tb ảnh hưởng"));
    }

    private Table findTable(Workbook workbook, String sheetName, LocalDate week, TableType type) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) throw missing("Thiếu sheet " + sheetName);
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        List<Headers> matches = new ArrayList<>();
        String reason = "Không tìm thấy bảng " + type.title;
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (!normalize(type.title).equals(normalize(formatter.formatCellValue(cell)))) continue;
                try { matches.add(headers(sheet, cell, formatter, week, type)); }
                catch (MissingIncidentDataException exception) { reason = exception.getMessage(); }
            }
        }
        if (matches.isEmpty()) throw missing(reason);
        if (matches.size() != 1) throw missing("Có nhiều bảng " + type.title + " cùng kỳ báo cáo");
        Headers headers = matches.get(0);
        Map<String, Row> rows = new HashMap<>();
        for (int index = headers.row + 1; index <= sheet.getLastRowNum(); index++) {
            String label = normalize(formatter.formatCellValue(cellAt(sheet, index, headers.labelColumn)));
            // Không tìm tiếp qua ô trống hoặc mượn dòng từ bảng bên dưới.
            if (!LABELS.contains(label)) break;
            if (rows.put(label, sheet.getRow(index)) != null) throw missing("Trùng dòng " + label + " trong bảng " + type.title);
        }
        List<String> required = type != TableType.SYSTEM ? LABELS : LABELS.subList(0, 2);
        if (!rows.keySet().containsAll(required)) throw missing("Bảng " + type.title + " thiếu dòng "
                + (type != TableType.SYSTEM ? "Số lượng, PA hoặc TB ảnh hưởng" : "Số lượng hoặc PA"));
        return new Table(sheet, headers, rows, type);
    }

    private Headers headers(Sheet sheet, Cell anchor, DataFormatter formatter, LocalDate week, TableType type) {
        CellRangeAddress merged = mergedRange(sheet, anchor.getRowIndex(), anchor.getColumnIndex());
        int rowIndex = anchor.getRowIndex();
        int column = merged == null ? anchor.getColumnIndex() + 1 : merged.getLastColumn() + 1;
        // Tiêu đề có thể gộp ngang trên một dòng riêng, header ở ngay dưới.
        if (normalize(formatter.formatCellValue(cellAt(sheet, rowIndex, column))).isEmpty()) {
            rowIndex = merged == null ? rowIndex + 1 : merged.getLastRow() + 1;
            column = anchor.getColumnIndex() + 1;
        }
        Row row = sheet.getRow(rowIndex);
        if (row == null) throw missing("Thiếu header của bảng " + type.title);
        Map<String, Integer> columns = new HashMap<>();
        for (int position = 0; column < row.getLastCellNum(); position++) {
            String text = normalize(formatter.formatCellValue(cellAt(sheet, rowIndex, column)));
            String key;
            if (text.isEmpty() && type == TableType.SYSTEM) { column++; continue; }
            if ("tuần này".equals(text) || matchesPeriod(text, week)) key = "current";
            else if ("tuần trước".equals(text) || matchesPeriod(text, week.minusWeeks(1))) key = "previous";
            else if ("so sánh".equals(text) || (type != TableType.SYSTEM && ("chênh lệch".equals(text)
                    || (position == 2 && "tỷ lệ tăng giảm".equals(text))))) key = "difference";
            else if ("tỷ lệ tăng giảm".equals(text)) key = "rate";
            else throw missing("Header không hợp lệ hoặc sai kỳ trong bảng " + type.title + ": " + text);
            if (columns.put(key, column) != null) throw missing("Trùng header trong bảng " + type.title + ": " + text);
            if (columns.size() == 4) return new Headers(rowIndex, anchor.getColumnIndex(), columns);
            // Bảng trạm có bốn header liền nhau, hai header cuối có thể cùng tên Tỷ lệ tăng giảm.
            if (type != TableType.SYSTEM && position == 3) break;
            CellRangeAddress range = mergedRange(sheet, rowIndex, column);
            column = range == null ? column + 1 : range.getLastColumn() + 1;
        }
        throw missing("Bảng " + type.title + " thiếu header hai tuần, chênh lệch hoặc Tỷ lệ tăng giảm");
    }

    private Metric readCounts(Table table, String label) {
        int row = table.rows.get(label).getRowNum();
        long previous = count(cellAt(table.sheet, row, table.headers.previous));
        long current = count(cellAt(table.sheet, row, table.headers.current));
        return new Metric(previous, current, current - previous, BigDecimal.ZERO);
    }

    private Metric readComparison(Table table, String label) {
        Metric counts = readCounts(table, label);
        int row = table.rows.get(label).getRowNum();
        long difference = integer(cellAt(table.sheet, row, table.headers.difference));
        if (difference != counts.difference) throw missing("Cột chênh lệch của " + label + " không khớp số liệu hai tuần");
        // Bảng trạm cho phép tuần trước bằng 0 và công thức tỷ lệ trả về #DIV/0!.
        boolean skipRate = difference == 0 || (table.type != TableType.SYSTEM && counts.previous == 0);
        BigDecimal rate = skipRate ? BigDecimal.ZERO : number(cellAt(table.sheet, row, table.headers.rate), true);
        if (rate.signum() != 0 && rate.signum() != Long.signum(difference)) {
            throw missing("Tỷ lệ tăng giảm của " + label + " không cùng chiều với chênh lệch");
        }
        return new Metric(counts.previous, counts.current, difference, rate);
    }

    private boolean matchesPeriod(String text, LocalDate start) {
        Matcher matcher = PERIOD.matcher(text);
        if (!matcher.find()) return false;
        LocalDate end = start.plusDays(6);
        return Integer.parseInt(matcher.group(1)) == start.getDayOfMonth()
                && Integer.parseInt(matcher.group(2)) == start.getMonthValue()
                && (matcher.group(3) == null || Integer.parseInt(matcher.group(3)) == start.getYear())
                && Integer.parseInt(matcher.group(4)) == end.getDayOfMonth()
                && Integer.parseInt(matcher.group(5)) == end.getMonthValue()
                && (matcher.group(6) == null || Integer.parseInt(matcher.group(6)) == end.getYear());
    }

    private Cell cellAt(Sheet sheet, int row, int column) {
        CellRangeAddress range = mergedRange(sheet, row, column);
        if (range != null) { row = range.getFirstRow(); column = range.getFirstColumn(); }
        Row source = sheet.getRow(row);
        return source == null ? null : source.getCell(column);
    }

    private CellRangeAddress mergedRange(Sheet sheet, int row, int column) {
        for (CellRangeAddress range : sheet.getMergedRegions()) if (range.isInRange(row, column)) return range;
        return null;
    }

    private long count(Cell cell) {
        long value = integer(cell);
        if (value < 0) throw missing("Số lượng âm tại " + cell.getAddress());
        return value;
    }

    private long integer(Cell cell) {
        try { return number(cell, false).longValueExact(); }
        catch (ArithmeticException exception) { throw missing("Số lượng không phải số nguyên hợp lệ tại " + cell.getAddress()); }
    }

    private BigDecimal number(Cell cell, boolean percent) {
        if (cell == null) throw missing("Thiếu ô số liệu trong bảng so sánh sự cố");
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (!Double.isFinite(value)) throw missing("Số liệu không hợp lệ tại " + cell.getAddress());
            BigDecimal result = BigDecimal.valueOf(value);
            return percent && cell.getCellStyle().getDataFormatString().contains("%") ? result.movePointRight(2) : result;
        }
        if (type == CellType.STRING) {
            String text = cell.getStringCellValue().trim().replace('\u2212', '-').replace(',', '.');
            if (percent && text.endsWith("%")) text = text.substring(0, text.length() - 1).trim();
            try { return new BigDecimal(text); } catch (NumberFormatException ignored) { }
        }
        throw missing("Ô số liệu trống hoặc lỗi tại " + cell.getAddress());
    }

    private String normalize(String text) { return WordSectionExtractor.normalize(text); }
    private MissingIncidentDataException missing(String text) { return new MissingIncidentDataException(text); }
    private static String format(long value) { return NumberFormat.getIntegerInstance(new Locale("vi", "VN")).format(value); }

    private enum TableType {
        SYSTEM("Tổng lỗi phát sinh"), STATION("So sánh lỗi trạm"),
        CABLE("So sánh Sự cố đứt cáp, cháy cáp trục, cáp nhánh");
        final String title;
        TableType(String title) { this.title = title; }
    }

    private static class Headers {
        final int row, labelColumn, previous, current, difference, rate;
        Headers(int row, int labelColumn, Map<String, Integer> columns) {
            this.row = row; this.labelColumn = labelColumn;
            previous = columns.get("previous"); current = columns.get("current");
            difference = columns.get("difference"); rate = columns.get("rate");
        }
    }

    private static class Table {
        final Sheet sheet;
        final Headers headers;
        final Map<String, Row> rows;
        final TableType type;
        Table(Sheet sheet, Headers headers, Map<String, Row> rows, TableType type) {
            this.sheet = sheet; this.headers = headers; this.rows = rows; this.type = type;
        }
    }

    private static class Metric {
        final long previous, current, difference;
        final BigDecimal rate;
        Metric(long previous, long current, long difference, BigDecimal rate) {
            this.previous = previous; this.current = current; this.difference = difference; this.rate = rate;
        }
        String direction() { return difference > 0 ? "tăng " : "giảm "; }
        String percent() { return rate.abs().setScale(0, RoundingMode.HALF_UP).toPlainString(); }
        String stationChange(String unit) {
            if (difference == 0) return "không có biến động";
            if (previous == 0) return direction() + format(current) + unit + " (tuần trước không phát sinh)";
            return direction() + percent() + "% (" + (difference > 0 ? "+" : "") + format(difference) + unit + ")";
        }
    }

    public static class Result {
        private final Metric incidents, complaints;
        private Result(Metric incidents, Metric complaints) { this.incidents = incidents; this.complaints = complaints; }
        public long getIncidentCount() { return incidents.current; }
        public long getComplaintCount() { return complaints.current; }
        public String getIncidentChange() {
            return incidents.difference == 0 ? "không có biến động" : incidents.direction() + Math.abs(incidents.difference) + " SC";
        }
        public String getComplaintChange() {
            return complaints.difference == 0 ? "PAKH không có thay đổi" : "PAKH " + complaints.direction()
                    + complaints.percent() + "% (" + Math.abs(complaints.difference) + "PA)";
        }
    }

    public static class StationResult {
        private final Metric incidents, complaints, affected;
        private StationResult(Metric incidents, Metric complaints, Metric affected) {
            this.incidents = incidents; this.complaints = complaints; this.affected = affected;
        }
        public String getIncidentCount() { return format(incidents.current); }
        public String getComplaintCount() { return format(complaints.current); }
        public String getAffectedCount() { return format(affected.current); }
        public String getIncidentChange() { return incidents.stationChange("SC"); }
        public String getComplaintChange() { return complaints.stationChange("PA"); }
        public String getAffectedChange() { return affected.stationChange("TB"); }
    }

    public static class MissingIncidentDataException extends RuntimeException {
        public MissingIncidentDataException(String message) { super(message); }
    }
}
