package com.techasians.alertservice.service.impl;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.techasians.alertservice.service.impl.WordSourceDocuments.ExcelSheet;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Đọc tổng TV360 và các nhóm tổng hợp, không lấy các bảng thiết bị chi tiết bên dưới. */
@Component
public class WordTv360FeedbackReader {
    private static final Logger log = LoggerFactory.getLogger(WordTv360FeedbackReader.class);
    private static final Pattern PERIOD = Pattern.compile("^(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?\\s*[-–]\\s*(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?$");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public Result read(ExcelSheet summarySource, ExcelSheet causesSource, LocalDate week, List<String> warnings) {
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        Table table = table(requiredSheet(summarySource), week, "Nội dung phản ánh", formatter);
        BigDecimal daily = number(cell(table.sheet, table.totalRow, table.daily), false);
        String change = part("So sánh tuần trước", warnings, () -> {
            Columns previous = columns(table.sheet, table.totalRow, week.minusWeeks(1), formatter);
            BigDecimal before = number(cell(table.sheet, table.totalRow, previous.daily), false);
            if (daily.compareTo(before) == 0) return "không đổi";
            if (before.signum() == 0) throw missing("TB ngày tuần trước bằng 0, không tính được phần trăm biến động");
            BigDecimal percentage = daily.subtract(before).abs().multiply(HUNDRED).divide(before, 8, RoundingMode.HALF_UP);
            int scale = percentage.setScale(0, RoundingMode.HALF_UP).signum() == 0 ? 2 : 0;
            return (daily.compareTo(before) > 0 ? "tăng " : "giảm ") + decimal(percentage, scale) + "%";
        });
        List<Service> top = part("PAKH tập trung", warnings, () -> top(table, formatter));
        String[] causes = part("Nguyên nhân lỗi", warnings, () -> {
            Table errors = table(requiredSheet(causesSource), week, "Nguyên nhân lỗi", formatter);
            return new String[] {cause(errors, "Hỗ trợ khách hàng", formatter), cause(errors, "Lỗi phía khách hàng", formatter)};
        });
        log.info("TV360: Sheet {}, tuần {} - {}: TB ngày={}, biến động={}, top={}, hỗ trợ KH={}, lỗi phía KH={}",
                summarySource.getName(), week, week.plusDays(6), daily, change, top,
                causes == null ? null : causes[0], causes == null ? null : causes[1]);
        return new Result(daily, change, top, causes);
    }

    private List<Service> top(Table table, DataFormatter formatter) {
        List<Service> services = new ArrayList<>();
        for (int index = table.totalRow + 1; index < table.endRow; index++) {
            if (!isGroup(table, index, formatter)) continue;
            String name = display(cell(table.sheet, index, table.label), formatter).replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
            if (name.isEmpty()) throw missing("Thiếu tên dịch vụ tại dòng " + (index + 1));
            BigDecimal share = number(cell(table.sheet, index, table.share), true);
            if (share.signum() > 0) services.add(new Service(name, share));
        }
        // List.sort giữ thứ tự nguồn khi tỷ trọng bằng nhau.
        services.sort(Comparator.comparing((Service service) -> service.share).reversed());
        if (services.isEmpty() && number(cell(table.sheet, table.totalRow, table.daily), false).signum() > 0) {
            throw missing("Không có nhóm dịch vụ có tỷ trọng ngày dương");
        }
        return new ArrayList<>(services.subList(0, Math.min(2, services.size())));
    }

    private String cause(Table table, String name, DataFormatter formatter) {
        Integer match = null;
        for (int index = table.totalRow + 1; index < table.endRow; index++) {
            if (!isGroup(table, index, formatter) || !normalize(name).equals(normalize(display(cell(table.sheet, index, table.label), formatter)))) continue;
            if (match != null) throw missing("Trùng nhóm " + name);
            match = index;
        }
        if (match == null) throw missing("Không tìm thấy nhóm " + name + " trong DỊCH VỤ TV360");
        return decimal(number(cell(table.sheet, match, table.share), true).multiply(HUNDRED), 1) + "%";
    }

    private boolean isGroup(Table table, int row, DataFormatter formatter) {
        return display(cell(table.sheet, row, table.order), formatter).trim().matches("[1-9]\\d*");
    }

    private Table table(Sheet sheet, LocalDate week, String label, DataFormatter formatter) {
        Cell total = null;
        for (Row row : sheet) for (Cell candidate : row) {
            if (!"dịch vụ tv360".equals(normalize(display(candidate, formatter)))) continue;
            if (total != null) throw missing("Trùng nhãn DỊCH VỤ TV360 ở sheet " + sheet.getSheetName());
            total = candidate;
        }
        if (total == null) throw missing("Thiếu dòng DỊCH VỤ TV360 ở sheet " + sheet.getSheetName());
        Columns columns = columns(sheet, total.getRowIndex(), week, formatter);
        int order = namedColumn(sheet, 0, total.getRowIndex() - 1, 0, columns.first - 1, "STT", formatter);
        int names = namedColumn(sheet, 0, total.getRowIndex() - 1, 0, columns.first - 1, label, formatter);
        int end = sheet.getLastRowNum() + 1;
        int lastOrder = 0;
        for (int row = total.getRowIndex() + 1; row < end; row++) {
            String value = display(cell(sheet, row, order), formatter).trim();
            if (value.isEmpty() || "-".equals(value)) continue;
            if (!value.matches("[1-9]\\d*")) { end = row; break; }
            int currentOrder;
            try { currentOrder = Integer.parseInt(value); }
            catch (NumberFormatException exception) { throw missing("STT không hợp lệ tại dòng " + (row + 1)); }
            if (currentOrder <= lastOrder) { end = row; break; }
            lastOrder = currentOrder;
        }
        return new Table(sheet, total.getRowIndex(), end, order, names, columns);
    }

    private Columns columns(Sheet sheet, int totalRow, LocalDate week, DataFormatter formatter) {
        Columns result = null;
        for (int index = 0; index < totalRow; index++) {
            Row row = sheet.getRow(index);
            if (row == null) continue;
            for (Cell candidate : row) {
                if (!matchesPeriod(display(candidate, formatter), week)) continue;
                if (result != null) throw missing("Trùng header tuần " + week + " ở sheet " + sheet.getSheetName());
                CellRangeAddress range = merged(sheet, index, candidate.getColumnIndex());
                if (range == null) throw missing("Header tuần chưa gộp nhóm cột ở " + candidate.getAddress());
                int first = range.getFirstColumn(), last = range.getLastColumn(), subheader = range.getLastRow() + 1;
                result = new Columns(first,
                        namedColumn(sheet, subheader, totalRow - 1, first, last, "TB ngày", formatter),
                        namedColumn(sheet, subheader, totalRow - 1, first, last, "Tỷ trọng ngày", formatter));
            }
        }
        if (result == null) throw missing("Thiếu header tuần " + week + " - " + week.plusDays(6) + " ở sheet " + sheet.getSheetName());
        return result;
    }

    private boolean matchesPeriod(String text, LocalDate week) {
        Matcher match = PERIOD.matcher(text.trim().replace('\u00a0', ' '));
        if (!match.matches()) return false;
        LocalDate end = week.plusDays(6);
        // Header rút gọn dd/MM-dd/MM/yyyy dùng năm ở cuối cho cả khoảng; hỗ trợ tuần vắt năm.
        int endYear = match.group(6) == null ? end.getYear() : Integer.parseInt(match.group(6));
        int firstMonth = Integer.parseInt(match.group(2)), lastMonth = Integer.parseInt(match.group(5));
        int startYear = match.group(3) == null ? endYear - (firstMonth > lastMonth ? 1 : 0) : Integer.parseInt(match.group(3));
        return Integer.parseInt(match.group(1)) == week.getDayOfMonth() && firstMonth == week.getMonthValue()
                && startYear == week.getYear() && Integer.parseInt(match.group(4)) == end.getDayOfMonth()
                && lastMonth == end.getMonthValue() && endYear == end.getYear();
    }

    private int namedColumn(Sheet sheet, int start, int end, int first, int last, String name, DataFormatter formatter) {
        Set<Integer> columns = new HashSet<>();
        for (int row = start; row <= end; row++) for (int column = first; column <= last; column++) {
            if (normalize(name).equals(normalize(display(cell(sheet, row, column), formatter)))) columns.add(column);
        }
        if (columns.size() != 1) throw missing((columns.isEmpty() ? "Thiếu" : "Trùng") + " header " + name + " ở sheet " + sheet.getSheetName());
        return columns.iterator().next();
    }

    private Sheet requiredSheet(ExcelSheet source) {
        if (source.getSheet() == null) throw missing(source.getMissingReason());
        return source.getSheet();
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

    // Dùng kết quả cache để không tính lại các công thức tham chiếu file ngoài; tương thích POI 4.1.2.
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

    private BigDecimal number(Cell cell, boolean share) {
        if (cell == null) throw missing("Thiếu ô số liệu");
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        BigDecimal value = null;
        if (type == CellType.NUMERIC && Double.isFinite(cell.getNumericCellValue())) value = BigDecimal.valueOf(cell.getNumericCellValue());
        if (type == CellType.STRING) {
            String text = cell.getStringCellValue().replace('\u00a0', ' ').trim();
            boolean percent = text.endsWith("%");
            if (percent) text = text.substring(0, text.length() - 1).trim();
            if (text.contains(",") && text.contains(".")) text = text.lastIndexOf(',') > text.lastIndexOf('.')
                    ? text.replace(".", "").replace(',', '.') : text.replace(",", "");
            else text = text.replace(',', '.');
            try { value = new BigDecimal(text); if (percent) value = share ? value.divide(HUNDRED) : null; }
            catch (NumberFormatException ignored) { }
        }
        if (value == null || value.signum() < 0 || share && value.compareTo(BigDecimal.ONE) > 0) {
            throw missing("Số liệu trống hoặc không hợp lệ tại " + cell.getSheet().getSheetName() + "!" + cell.getAddress());
        }
        return value;
    }

    private <T> T part(String label, List<String> warnings, Supplier<T> action) {
        try { return action.get(); }
        catch (MissingTv360DataException exception) {
            String warning = "TV360: " + label + ": " + exception.getMessage();
            log.warn(warning); warnings.add(warning); return null;
        }
    }

    private String normalize(String value) { return WordSectionExtractor.normalize(value); }
    private MissingTv360DataException missing(String reason) { return new MissingTv360DataException(reason); }
    private static String decimal(BigDecimal value, int scale) {
        return value.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static class Columns {
        final int first, daily, share;
        Columns(int first, int daily, int share) { this.first = first; this.daily = daily; this.share = share; }
    }

    private static class Table {
        final Sheet sheet;
        final int totalRow, endRow, order, label, daily, share;
        Table(Sheet sheet, int totalRow, int endRow, int order, int label, Columns columns) {
            this.sheet = sheet; this.totalRow = totalRow; this.endRow = endRow; this.order = order; this.label = label;
            this.daily = columns.daily; this.share = columns.share;
        }
    }

    public static class Service {
        private final String name;
        private final BigDecimal share;
        Service(String name, BigDecimal share) { this.name = name; this.share = share; }
        public String getName() { return name; }
        public String getShare() { return decimal(share.multiply(HUNDRED), 1) + "%"; }
        @Override public String toString() { return name + " " + getShare(); }
    }

    public static class Result {
        private final BigDecimal daily;
        private final String change;
        private final List<Service> top;
        private final String[] causes;
        Result(BigDecimal daily, String change, List<Service> top, String[] causes) {
            this.daily = daily; this.change = change; this.top = top; this.causes = causes;
        }
        public String getDaily() { return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(daily.setScale(0, RoundingMode.HALF_UP)); }
        public String getChange() { return change; }
        public List<Service> getTop() { return top == null ? Collections.emptyList() : Collections.unmodifiableList(top); }
        public boolean hasCauses() { return causes != null; }
        public String getSupportShare() { return causes == null ? null : causes[0]; }
        public String getCustomerShare() { return causes == null ? null : causes[1]; }
        public boolean isZero() { return daily.signum() == 0; }
    }

    public static class MissingTv360DataException extends RuntimeException {
        public MissingTv360DataException(String message) { super(message); }
    }
}
