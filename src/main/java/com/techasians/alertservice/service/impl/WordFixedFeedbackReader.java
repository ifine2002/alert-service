package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.service.impl.WordSourceDocuments.ExcelSheet;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Đọc theo phạm vi bảng/nhãn/tuần; tính lại công thức nội bộ, cảnh báo khi phải dùng cache. */
@Component
public class WordFixedFeedbackReader {
    private static final Pattern PERIOD = Pattern.compile("^(?:tuần(?: từ)?\\s*)?(\\d{1,2})[./](\\d{1,2})(?:[./](\\d{4}))?"
            + "\\s*[-–—]\\s*(\\d{1,2})[./](\\d{1,2})(?:[./](\\d{4}))?$");

    public Result read(ExcelSheet notesSource, ExcelSheet kpiSource, LocalDate week, List<String> warnings) {
        Context context = new Context(warnings);
        Notes notes = part("Bảng tiếp nhận", warnings, () -> notes(requireSheet(notesSource), week, context));
        BigDecimal received = notes == null ? null : part("Tiếp nhận BO + KT", warnings,
                () -> rowValue(notes, notes.current, value -> transfer(value, "bo"), "HTDV + sự cố chuyển BO", true, context)
                        .add(rowValue(notes, notes.current, value -> transfer(value, "kt"), "HTDV + sự cố chuyển KT", true, context)));
        BigDecimal technical = notes == null ? null : part("SỰ CỐ chuyển KT", warnings,
                () -> rowValue(notes, notes.current, this::technicalLabel, "SỰ CỐ chuyển KT", true, context));
        BigDecimal technicalBefore = notes == null || notes.previous < 0 ? null : part("SỰ CỐ chuyển KT tuần trước", warnings,
                () -> rowValue(notes, notes.previous, this::technicalLabel, "SỰ CỐ chuyển KT", true, context));
        BigDecimal rate = notes == null ? null : part("TLPA/10K", warnings,
                () -> rowValue(notes, notes.current, this::rateLabel, "TLPA/10K", false, context));
        BigDecimal rateBefore = notes == null || notes.previous < 0 ? null : part("TLPA/10K tuần trước", warnings,
                () -> rowValue(notes, notes.previous, this::rateLabel, "TLPA/10K", false, context));
        if (technical != null && technicalBefore != null && technicalBefore.signum() == 0 && technical.signum() > 0) {
            warnings.add("FIXED: Không thể tính tỷ lệ tăng giảm sự cố vì số PA tuần trước bằng 0");
        }
        Service gpon = notes == null ? null : part("FTTH GPON", warnings,
                () -> serviceResult(notes, kpiSource, "ftth gpon", context));
        Service television = notes == null ? null : part("THS", warnings,
                () -> serviceResult(notes, kpiSource, "truyền hình số", context));
        return new Result(received, technical, technicalBefore, rate, rateBefore, gpon, television);
    }

    private Notes notes(Sheet sheet, LocalDate week, Context context) {
        List<Cell> headers = find(sheet, value -> normalize(value).equals("cùng kỳ tuần trước"), context);
        List<Cell> labels = find(sheet, this::notesLabel, context);
        List<Notes> matches = new ArrayList<>();
        for (Cell header : headers) {
            int end = nextHeaderRow(headers, header, sheet.getLastRowNum() + 1);
            Set<Integer> labelColumns = new LinkedHashSet<>();
            for (Cell label : labels) if (label.getRowIndex() > header.getRowIndex() && label.getRowIndex() < end
                    && label.getColumnIndex() < header.getColumnIndex()) labelColumns.add(label.getColumnIndex());
            // Nhãn diễn giải ở bên trái (ví dụ A7 = THS) không phải cột chỉ tiêu của bảng số liệu.
            // Cột chỉ tiêu là cột nhãn gần nhóm tuần nhất.
            int metricColumn = labelColumns.stream().mapToInt(Integer::intValue).max().orElse(-1);
            for (int labelColumn : labelColumns) {
                if (labelColumn != metricColumn) continue;
                Table table = new Table(sheet, header.getRowIndex(), end, labelColumn, header.getColumnIndex() - 1);
                List<Cell> data = table.find(this::notesLabel, context);
                int firstData = data.stream().mapToInt(Cell::getRowIndex).min().orElse(end);
                List<Cell> dates = new ArrayList<>();
                for (int row = header.getRowIndex(); row < firstData; row++) {
                    for (int col = labelColumn + 1; col < header.getColumnIndex(); col++) {
                        Cell candidate = cell(sheet, row, col);
                        if (isOwnCell(candidate, row, col) && matchesPeriod(display(candidate, context), week)) dates.add(candidate);
                    }
                }
                if (dates.size() != 1) continue;
                int previous = -1;
                CellRangeAddress group = merged(sheet, header.getRowIndex(), header.getColumnIndex());
                int last = group == null ? header.getColumnIndex() : group.getLastColumn();
                for (int row = header.getRowIndex(); row < firstData; row++) {
                    for (int col = header.getColumnIndex(); col <= last; col++) {
                        Cell candidate = cell(sheet, row, col);
                        if (isOwnCell(candidate, row, col) && matchesPeriod(display(candidate, context), week.minusWeeks(1))) {
                            if (previous >= 0) throw missing("Trùng nhóm tuần trước trong bảng tiếp nhận");
                            previous = col;
                        }
                    }
                }
                matches.add(new Notes(table, dates.get(0).getColumnIndex(), previous));
            }
        }
        if (matches.size() != 1) throw missing("Thiếu hoặc có nhiều bảng N.Xet tuan đúng kỳ " + week);
        if (matches.get(0).previous < 0) context.warnings.add("FIXED: Thiếu cột tuần trước đúng kỳ trong N.Xet tuan");
        return matches.get(0);
    }

    private boolean notesLabel(String value) {
        String label = service(value);
        return transfer(value, "bo") || transfer(value, "kt") || technicalLabel(value) || rateLabel(value)
                || label.equals("ftth gpon") || label.equals("ths") || label.equals("truyền hình số");
    }
    private boolean technicalLabel(String value) {
        return normalize(value).replace("tiếp nhận ", "").equals("sự cố chuyển kt");
    }
    private boolean rateLabel(String value) { return normalize(value).replace(" ", "").equals("tlpa/10k"); }
    private boolean transfer(String text, String destination) {
        return normalize(text).replace("tiếp nhận ", "").replaceAll("\\s*\\+\\s*", " + ")
                .equals("htdv + sự cố chuyển " + destination);
    }
    private BigDecimal rowValue(Notes notes, int column, Predicate<String> match, String label, boolean count, Context context) {
        Cell anchor = notes.table.anchor(match, label, context);
        return number(cell(notes.table.sheet, anchor.getRowIndex(), column), count, context);
    }

    private List<Table> tables(Sheet sheet, String label, Context context) {
        List<Cell> headers = find(sheet, value -> normalize(value).equals(label), context);
        List<Table> result = new ArrayList<>();
        for (Cell header : headers) {
            int lastColumn = sheet.getRow(header.getRowIndex()).getLastCellNum() - 1;
            for (Cell other : headers) if (other.getRowIndex() == header.getRowIndex()
                    && other.getColumnIndex() > header.getColumnIndex()) lastColumn = Math.min(lastColumn, other.getColumnIndex() - 1);
            result.add(new Table(sheet, header.getRowIndex(), nextHeaderRow(headers, header, sheet.getLastRowNum() + 1),
                    header.getColumnIndex(), lastColumn));
        }
        return result;
    }

    private int nextHeaderRow(List<Cell> headers, Cell header, int last) {
        for (Cell other : headers) if (other.getColumnIndex() == header.getColumnIndex()
                && other.getRowIndex() > header.getRowIndex()) last = Math.min(last, other.getRowIndex());
        return last;
    }

    private Service serviceResult(Notes notes, ExcelSheet kpiSource, String label, Context context) {
        Cell rateHeader = notes.table.anchor(this::rateLabel, "TLPA/10K", context);
        int end = notes.table.endRow;
        for (int row = rateHeader.getRowIndex() + 1; row < end; row++) {
            String value = display(cell(notes.table.sheet, row, notes.table.label), context);
            String name = service(value);
            if (!normalize(value).isEmpty() && !name.equals("ftth gpon") && !name.equals("ths") && !name.equals("truyền hình số")) {
                end = row;
                break;
            }
        }
        Table rates = new Table(notes.table.sheet, rateHeader.getRowIndex(), end, notes.table.label, notes.table.lastColumn);
        Cell source = rates.anchor(
                value -> service(value).equals(label) || (label.equals("truyền hình số") && service(value).equals("ths")),
                label.equals("truyền hình số") ? "THS" : "FTTH GPON", context);
        BigDecimal rate = number(cell(notes.table.sheet, source.getRowIndex(), notes.current), false, context);
        BigDecimal kpi = part("KPI " + label, context.warnings, () -> kpi(requireSheet(kpiSource), label, context));
        return new Service(rate, kpi);
    }

    private BigDecimal kpi(Sheet sheet, String label, Context context) {
        List<Table> matches = new ArrayList<>();
        for (Table table : tables(sheet, "chỉ tiêu", context)) {
            if (!table.find(value -> service(value).equals(label), context).isEmpty()
                    && !findInRow(table, value -> normalize(value).equals("kpi"), context).isEmpty()) matches.add(table);
        }
        if (matches.size() != 1) throw missing("Thiếu hoặc có nhiều bảng KPI cho " + label);
        Table table = matches.get(0);
        Cell header = unique(findInRow(table, value -> normalize(value).equals("kpi"), context), "KPI");
        Cell source = table.anchor(value -> service(value).equals(label), label, context);
        CellRangeAddress group = merged(sheet, header.getRowIndex(), header.getColumnIndex());
        int last = group == null ? header.getColumnIndex() : group.getLastColumn();
        BigDecimal result = null;
        for (int col = header.getColumnIndex(); col <= last; col++) {
            Cell candidate = cell(sheet, source.getRowIndex(), col);
            String text = normalize(display(candidate, context));
            if (text.equals("≤") || text.equals("<=")) continue;
            if (text.equals("≥") || text.equals(">=")) throw missing("KPI TLPA phải là ngưỡng tối đa: " + label);
            if (text.isEmpty()) continue;
            if (result != null) throw missing("Có nhiều giá trị KPI: " + label);
            result = number(candidate, false, context);
        }
        if (result == null) throw missing("Thiếu giá trị KPI: " + label);
        return result;
    }

    private List<Cell> findInRow(Table table, Predicate<String> match, Context context) {
        List<Cell> result = new ArrayList<>();
        for (Cell candidate : table.sheet.getRow(table.header)) if (candidate.getColumnIndex() > table.label
                && candidate.getColumnIndex() <= table.lastColumn && match.test(display(candidate, context))) result.add(candidate);
        return result;
    }

    private boolean matchesPeriod(String text, LocalDate week) {
        Matcher match = PERIOD.matcher(normalize(text));
        if (!match.matches()) return false;
        LocalDate end = week.plusDays(6);
        return Integer.parseInt(match.group(1)) == week.getDayOfMonth()
                && Integer.parseInt(match.group(2)) == week.getMonthValue()
                && (match.group(3) == null || Integer.parseInt(match.group(3)) == week.getYear())
                && Integer.parseInt(match.group(4)) == end.getDayOfMonth()
                && Integer.parseInt(match.group(5)) == end.getMonthValue()
                && (match.group(6) == null || Integer.parseInt(match.group(6)) == end.getYear());
    }

    private List<Cell> find(Sheet sheet, Predicate<String> match, Context context) {
        List<Cell> result = new ArrayList<>();
        for (Row row : sheet) for (Cell candidate : row) if (match.test(display(candidate, context))) result.add(candidate);
        return result;
    }

    private Cell unique(List<Cell> candidates, String label) {
        if (candidates.size() != 1) throw missing((candidates.isEmpty() ? "Thiếu nhãn " : "Trùng nhãn ") + label + " trong phạm vi bảng");
        return candidates.get(0);
    }

    private Sheet requireSheet(ExcelSheet source) {
        if (source.getSheet() == null) throw missing(source.getMissingReason());
        return source.getSheet();
    }
    private CellRangeAddress merged(Sheet sheet, int row, int column) {
        for (CellRangeAddress range : sheet.getMergedRegions()) if (range.isInRange(row, column)) return range;
        return null;
    }
    private Cell cell(Sheet sheet, int row, int column) {
        CellRangeAddress range = merged(sheet, row, column);
        if (range != null) { row = range.getFirstRow(); column = range.getFirstColumn(); }
        Row source = sheet.getRow(row);
        return source == null ? null : source.getCell(column);
    }
    private boolean isOwnCell(Cell cell, int row, int column) {
        return cell != null && cell.getRowIndex() == row && cell.getColumnIndex() == column;
    }

    private String display(Cell cell, Context context) {
        if (cell == null) return "";
        if (cell.getCellType() != CellType.FORMULA) return context.formatter.formatCellValue(cell);
        // Dò nhãn không cần tính hàng nghìn công thức số. Số được sử dụng luôn đi qua Context.value().
        CellValue value = cell.getCachedFormulaResultType() == CellType.STRING ? context.value(cell) : cached(cell);
        switch (value.getCellType()) {
            case STRING: return value.getStringValue();
            case NUMERIC: return context.formatter.formatRawCellContents(value.getNumberValue(),
                    cell.getCellStyle().getDataFormat(), cell.getCellStyle().getDataFormatString());
            case ERROR: return FormulaError.forInt(value.getErrorValue()).getString();
            default: return "";
        }
    }

    private BigDecimal number(Cell cell, boolean count, Context context) {
        if (cell == null) throw missing("Thiếu ô số liệu");
        CellValue value = context.value(cell);
        BigDecimal result = null;
        if (value.getCellType() == CellType.NUMERIC && Double.isFinite(value.getNumberValue())) result = BigDecimal.valueOf(value.getNumberValue());
        if (value.getCellType() == CellType.STRING) {
            String text = value.getStringValue().replace('\u00a0', ' ').trim();
            if (text.contains(",") && text.contains(".")) text = text.lastIndexOf(',') > text.lastIndexOf('.')
                    ? text.replace(".", "").replace(',', '.') : text.replace(",", "");
            else if (count && text.matches("\\d{1,3}(?:\\.\\d{3})+")) text = text.replace(".", "");
            else text = text.replace(',', '.');
            try { result = new BigDecimal(text); } catch (NumberFormatException ignored) { }
        }
        if (result == null || result.signum() < 0) throw missing("Số liệu trống hoặc lỗi tại " + cell.getAddress());
        if (count) {
            try { result.toBigIntegerExact(); }
            catch (ArithmeticException exception) { throw missing("Số PA/thuê bao không nguyên tại " + cell.getAddress()); }
        }
        return result;
    }

    private static CellValue cached(Cell cell) {
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        switch (type) {
            case STRING: return new CellValue(cell.getStringCellValue());
            case NUMERIC: return new CellValue(cell.getNumericCellValue());
            case BOOLEAN: return CellValue.valueOf(cell.getBooleanCellValue());
            case ERROR: return CellValue.getError(cell.getErrorCellValue());
            default: return CellValue.getError(FormulaError.NA.getCode());
        }
    }

    private <T> T part(String label, List<String> warnings, Supplier<T> action) {
        try { return action.get(); }
        catch (MissingFixedDataException exception) { warnings.add("FIXED: " + label + ": " + exception.getMessage()); return null; }
    }
    private String normalize(String text) { return WordSectionExtractor.normalize(text); }
    private String service(String text) { return normalize(text).replaceFirst("^dịch vụ\\s+", "").replaceAll("\\s*[-–—_]\\s*", " "); }
    private MissingFixedDataException missing(String reason) { return new MissingFixedDataException(reason); }
    private static String decimal(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ','); }
    private static String integer(BigDecimal value) { return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(value.setScale(0, RoundingMode.HALF_UP)); }
    private static String direction(BigDecimal value) { return value.signum() > 0 ? "tăng" : value.signum() < 0 ? "giảm" : "không đổi"; }

    private static class Context {
        final List<String> warnings;
        final DataFormatter formatter = new DataFormatter(Locale.ROOT);
        final Map<Workbook, FormulaEvaluator> evaluators = new IdentityHashMap<>();
        final Map<Cell, CellValue> values = new IdentityHashMap<>();
        final Set<Workbook> warned = Collections.newSetFromMap(new IdentityHashMap<>());
        Context(List<String> warnings) { this.warnings = warnings; }
        CellValue value(Cell cell) {
            if (cell.getCellType() != CellType.FORMULA) return cached(cell);
            if (!values.containsKey(cell)) {
                Workbook workbook = cell.getSheet().getWorkbook();
                try {
                    FormulaEvaluator evaluator = evaluators.computeIfAbsent(workbook, source -> source.getCreationHelper().createFormulaEvaluator());
                    CellValue value = evaluator.evaluate(cell); // Không sửa cache hay file nguồn.
                    values.put(cell, value == null ? CellValue.getError(FormulaError.NA.getCode()) : value);
                } catch (RuntimeException exception) {
                    if (warned.add(workbook)) warnings.add("FIXED: Không tính lại được công thức tại " + cell.getSheet().getSheetName()
                            + "!" + cell.getAddress() + "; dùng cache Excel đã lưu, chưa xác minh độ mới. Cần tính và lưu lại Excel cùng các file liên kết.");
                    values.put(cell, cached(cell));
                }
            }
            return values.get(cell);
        }
    }
    private class Table {
        final Sheet sheet;
        final int header, endRow, label, lastColumn;
        Table(Sheet sheet, int header, int endRow, int label, int lastColumn) {
            this.sheet = sheet; this.header = header; this.endRow = endRow; this.label = label; this.lastColumn = lastColumn;
        }
        List<Cell> find(Predicate<String> match, Context context) {
            List<Cell> result = new ArrayList<>();
            for (int row = header + 1; row < endRow; row++) {
                Cell candidate = cell(sheet, row, label);
                if (isOwnCell(candidate, row, label) && match.test(display(candidate, context))) result.add(candidate);
            }
            return result;
        }
        Cell anchor(Predicate<String> match, String name, Context context) { return unique(find(match, context), name); }
    }
    private static class Notes {
        final Table table;
        final int current, previous;
        Notes(Table table, int current, int previous) { this.table = table; this.current = current; this.previous = previous; }
    }
    public static class Service {
        private final BigDecimal rate, kpi;
        private Service(BigDecimal rate, BigDecimal kpi) { this.rate = rate; this.kpi = kpi; }
        public String getRate() { return decimal(rate); }
        public boolean hasKpi() { return kpi != null; }
        /** Null nghĩa là chưa có dữ liệu, không được kết luận Đạt/Không đạt. */
        public String getKpi() { return kpi == null ? null : decimal(kpi); }
        public String getAssessment() { return kpi == null ? null : rate.compareTo(kpi) <= 0 ? "Đạt" : "Không đạt"; }
    }
    public static class Result {
        private final BigDecimal received, technical, technicalBefore, rate, rateBefore;
        private final Service gpon, television;
        private Result(BigDecimal received, BigDecimal technical, BigDecimal technicalBefore, BigDecimal rate, BigDecimal rateBefore,
                       Service gpon, Service television) {
            this.received = received; this.technical = technical; this.technicalBefore = technicalBefore;
            this.rate = rate; this.rateBefore = rateBefore; this.gpon = gpon; this.television = television;
        }
        public boolean hasReceived() { return received != null; }
        public String getReceivedDaily() { return received == null ? null : integer(received.divide(BigDecimal.valueOf(7), 8, RoundingMode.HALF_UP)); }
        public String getRate() { return rate == null ? null : decimal(rate); }
        public Service getGpon() { return gpon; }
        public Service getTelevision() { return television; }
        public String getTechnicalDaily() { return technical == null ? null : integer(technical.divide(BigDecimal.valueOf(7), 8, RoundingMode.HALF_UP)); }
        public String getTechnicalChange() {
            if (technical == null || technicalBefore == null) return null;
            BigDecimal change = technical.subtract(technicalBefore);
            if (change.signum() == 0) return "không đổi";
            if (technicalBefore.signum() == 0) return null;
            return direction(change) + " " + change.abs().multiply(BigDecimal.valueOf(100)).divide(technicalBefore, 0, RoundingMode.HALF_UP).toPlainString() + "%";
        }
        /** TLPA/10K thuê bao có đơn vị PA theo mẫu nghiệp vụ, không phải phần trăm. */
        public String getRateChange() {
            if (rate == null || rateBefore == null) return null;
            BigDecimal change = rate.subtract(rateBefore);
            return change.signum() == 0 ? "không đổi" : direction(change) + " " + decimal(change.abs()) + "PA";
        }
    }
    public static class MissingFixedDataException extends RuntimeException {
        public MissingFixedDataException(String message) { super(message); }
    }
}
