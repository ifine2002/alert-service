package com.techasians.alertservice.service.impl;

import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.techasians.alertservice.service.impl.WordSourceDocuments.ExcelSheet;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tổng hợp tám tuần từ Excel 1102: số sự cố, tổng PA, và thêm tổng ảnh hưởng cho phần trạm. */
@Component
public class WordIncidentChartReader {
    private static final Logger log = LoggerFactory.getLogger(WordIncidentChartReader.class);
    private static final Pattern MONTH_PATTERN = Pattern.compile("^\\s*Tháng\\s+(\\d{1,2})\\.(\\d{4})\\s*$");
    private static final String YEAR = "Năm", MONTH = "Tháng", DAY = "Ngày", OWNER = "Chủ dịch vụ";
    private static final String COMPLAINTS = "Số lượng phản ảnh", STATION_PA = "Tổng PA", AFFECTED = "Tổng ảnh hưởng";
    private static final String CAUSE = "Nguyên nhân cấp 1", PROVINCE = "Tỉnh";

    /** [8][2]: số sự cố VTT và tổng PAKH; tuần cũ nhất đứng trước. */
    public Double[][] read(ExcelSheet current, ExcelSheet previous, LocalDate week, List<String> warnings) {
        return read(current, previous, week, warnings, IncidentType.SYSTEM);
    }

    /** [8][3]: số dòng sự cố trạm, tổng PA và tổng TB ảnh hưởng. */
    public Double[][] readStation(ExcelSheet current, ExcelSheet previous, LocalDate week, List<String> warnings) {
        return read(current, previous, week, warnings, IncidentType.STATION);
    }

    public Double[][] readCable(ExcelSheet current, ExcelSheet previous, LocalDate week, List<String> warnings) {
        return read(current, previous, week, warnings, IncidentType.CABLE);
    }

    /** Chỉ đếm các dòng đứt/cháy cáp trong tuần báo cáo, gom mã tỉnh và lấy tối đa ba tỉnh. */
    public String readTopCableProvinces(ExcelSheet current, ExcelSheet previous, LocalDate week, List<String> warnings) {
        Map<String, Long> counts = new HashMap<>();
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        LocalDate end = week.plusDays(6);
        log.info("CABLE_TOP_PROVINCES: Đọc top tỉnh trong tuần {} - {}, lọc Nguyên nhân cấp 1: Cháy cáp, Đứt cáp",
                week, end);
        for (int year = week.getYear(); year <= end.getYear(); year++) {
            ExcelSheet source = year == end.getYear() ? current : previous;
            Sheet sheet = source == null ? null : source.getSheet();
            if (sheet == null) {
                String reason = source == null ? "Thiếu nguồn năm " + year : source.getMissingReason();
                log.warn("CABLE_TOP_PROVINCES: {} cho tuần {} - {}", reason, week, end);
                throw missing(reason);
            }
            log.info("CABLE_TOP_PROVINCES: Đọc sheet {}, năm dữ liệu {}", sheet.getSheetName(), year);
            Columns columns;
            try {
                columns = stationColumns(sheet, formatter, Arrays.asList(YEAR, MONTH, DAY, CAUSE, PROVINCE));
            } catch (MissingChartDataException exception) {
                log.warn("CABLE_TOP_PROVINCES: Không đọc được header sheet {}: {}",
                        sheet.getSheetName(), exception.getMessage());
                throw exception;
            }
            int cableRows = 0, invalidDates = 0, outsideWeek = 0, weeklyRows = 0, missingProvinces = 0;
            for (int index = columns.dataStart; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null || !isCable(row, columns, formatter)) continue;
                cableRows++;
                LocalDate date = readDate(sheet, row, columns, formatter, false, IncidentType.CABLE, warnings);
                if (date == null) { invalidDates++; continue; }
                if (date.getYear() != year || date.isBefore(week) || date.isAfter(end)) { outsideWeek++; continue; }
                weeklyRows++;
                String province = WordSectionExtractor.normalize(formatter.formatCellValue(columns.cell(row, PROVINCE)))
                        .toUpperCase(Locale.ROOT);
                if (province.isEmpty()) {
                    missingProvinces++;
                    log.warn("CABLE_TOP_PROVINCES: Sheet {} dòng {} ngày {} thiếu mã tỉnh — bỏ qua khi xếp hạng",
                            sheet.getSheetName(), index + 1, date);
                    warnings.add("STATION_INCIDENT: " + sheet.getSheetName() + " dòng " + (index + 1) + " thiếu mã tỉnh");
                } else {
                    counts.merge(province, 1L, Long::sum);
                    log.debug("CABLE_TOP_PROVINCES: Sheet {} dòng {} ngày {} tỉnh {}",
                            sheet.getSheetName(), index + 1, date, province);
                }
            }
            log.info("CABLE_TOP_PROVINCES: Sheet {}: {} dòng đứt/cháy cáp, {} ngày không hợp lệ, "
                            + "{} dòng ngoài tuần hoặc sai năm sheet, {} sự cố trong tuần, {} dòng thiếu mã tỉnh",
                    sheet.getSheetName(), cableRows, invalidDates, outsideWeek, weeklyRows, missingProvinces);
        }
        String result = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(3).map(entry -> entry.getKey() + " – " + entry.getValue() + "SC")
                .collect(java.util.stream.Collectors.joining("; "));
        log.info("CABLE_TOP_PROVINCES: Số sự cố theo tỉnh trong tuần {} - {}: {}", week, end, new TreeMap<>(counts));
        log.info("CABLE_TOP_PROVINCES: Top 3 tuần {} - {}: {}", week, end,
                result.isEmpty() ? "Không có tỉnh hợp lệ" : result);
        return result;
    }

    private boolean isCable(Row row, Columns columns, DataFormatter formatter) {
        String cause = WordSectionExtractor.normalize(formatter.formatCellValue(columns.cell(row, CAUSE)));
        return "cháy cáp".equals(cause) || "đứt cáp".equals(cause);
    }

    private Double[][] read(ExcelSheet current, ExcelSheet previous, LocalDate week, List<String> warnings, IncidentType type) {
        Double[][] values = new Double[8][type.seriesCount];
        for (Double[] row : values) Arrays.fill(row, 0.0);
        LocalDate start = week.minusWeeks(7), end = week.plusDays(6);
        int reportYear = end.getYear();
        List<Integer> years = new ArrayList<>(Collections.singletonList(reportYear));
        if (start.getYear() < reportYear) years.add(reportYear - 1);
        for (int year : years) {
            ExcelSheet source = year == reportYear ? current : previous;
            Sheet sheet = source == null ? null : source.getSheet();
            if (sheet == null) {
                String reason = source == null ? "Thiếu nguồn năm " + year : source.getMissingReason();
                if (year == reportYear) throw missing(reason);
                warnings.add(type.prefix + reason + "; dữ liệu năm " + year + " chưa đầy đủ");
                continue;
            }
            log.info("{}Đọc dữ liệu từ {} đến {} trong sheet {}", type.prefix, start, end, sheet.getSheetName());
            try { readSheet(sheet, year, year < reportYear, type, start, end, values, warnings); }
            catch (MissingChartDataException exception) {
                if (year == reportYear) throw exception;
                warnings.add(type.prefix + exception.getMessage() + "; dữ liệu năm " + year + " chưa đầy đủ");
            }
        }
        for (int i = 0; i < 8; i++) log.info("{}Tuần {} ({} - {}): {}", type.prefix,
                i + 1, start.plusWeeks(i), start.plusWeeks(i).plusDays(6), Arrays.toString(values[i]));
        return values;
    }

    private void readSheet(Sheet sheet, int year, boolean previousYear, IncidentType type, LocalDate start,
                           LocalDate end, Double[][] values, List<String> warnings) {
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        Columns columns = type == IncidentType.SYSTEM ? systemColumns(sheet, formatter, previousYear)
                : stationColumns(sheet, formatter, type == IncidentType.CABLE
                        ? Arrays.asList(YEAR, MONTH, DAY, STATION_PA, AFFECTED, CAUSE)
                        : Arrays.asList(YEAR, MONTH, DAY, STATION_PA, AFFECTED));
        boolean textMonth = type == IncidentType.SYSTEM && previousYear;
        for (int index = columns.dataStart; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (row == null) continue;
            if (type == IncidentType.CABLE && !isCable(row, columns, formatter)) continue;
            if (type == IncidentType.SYSTEM) {
                if (!"VTT".equalsIgnoreCase(formatter.formatCellValue(columns.cell(row, OWNER)).trim())) continue;
            } else if (Arrays.asList(YEAR, MONTH, DAY).stream().allMatch(
                    header -> formatter.formatCellValue(columns.cell(row, header)).trim().isEmpty())) continue;

            LocalDate date = readDate(sheet, row, columns, formatter, textMonth, type, warnings);
            if (date == null || date.isBefore(start) || date.isAfter(end)) continue;
            if (date.getYear() != year) continue;
            int weekIndex = (int) (ChronoUnit.DAYS.between(start, date) / 7);
            values[weekIndex][0]++;
            String paColumn = type != IncidentType.SYSTEM ? STATION_PA : COMPLAINTS;
            values[weekIndex][1] += numeric(columns.cell(row, paColumn), sheet, index, paColumn, type, warnings);
            if (type != IncidentType.SYSTEM) {
                values[weekIndex][2] += numeric(columns.cell(row, AFFECTED), sheet, index, AFFECTED, type, warnings);
            }
        }
    }

    private Columns systemColumns(Sheet sheet, DataFormatter formatter, boolean previousYear) {
        List<String> names = Arrays.asList(YEAR, MONTH, DAY, OWNER, COMPLAINTS);
        for (int index = 0; index <= sheet.getLastRowNum(); index++) {
            Map<String, Integer> columns = new HashMap<>();
            collectColumns(sheet.getRow(index), formatter, names, columns, false, sheet.getSheetName());
            if (!columns.containsKey(OWNER)) continue;
            List<String> required = previousYear ? Arrays.asList(MONTH, DAY, OWNER) : Arrays.asList(YEAR, MONTH, DAY, OWNER);
            if (!columns.keySet().containsAll(required)) throw missing("Sheet " + sheet.getSheetName()
                    + " thiếu một hoặc nhiều cột bắt buộc: " + String.join(", ", required));
            return new Columns(columns, index + 1);
        }
        throw missing("Sheet " + sheet.getSheetName() + " không có header chứa cột '" + OWNER + "'");
    }

    private Columns stationColumns(Sheet sheet, DataFormatter formatter, List<String> names) {
        Map<String, Integer> columns = new HashMap<>();
        // Ngày ở hàng đầu, các cột tổng ở hàng con bên dưới; vị trí cột có thể lệch giữa hai năm.
        for (int index = 0; index <= sheet.getLastRowNum(); index++) {
            collectColumns(sheet.getRow(index), formatter, names, columns, true, sheet.getSheetName());
            if (columns.size() == names.size()) return new Columns(columns, index + 1);
        }
        throw missing("Sheet " + sheet.getSheetName() + " thiếu header bắt buộc: " + String.join(", ", names));
    }

    private void collectColumns(Row row, DataFormatter formatter, List<String> names, Map<String, Integer> columns,
                                boolean rejectDuplicates, String sheetName) {
        if (row == null) return;
        for (Cell cell : row) {
            String text = WordSectionExtractor.normalize(formatter.formatCellValue(cell));
            for (String name : names) {
                if (!WordSectionExtractor.normalize(name).equals(text)) continue;
                if (rejectDuplicates && columns.containsKey(name)) throw missing("Sheet " + sheetName + " trùng header " + name);
                columns.putIfAbsent(name, cell.getColumnIndex());
            }
        }
    }

    private LocalDate readDate(Sheet sheet, Row row, Columns columns, DataFormatter formatter,
                               boolean textMonth, IncidentType type, List<String> warnings) {
        try {
            int year, month;
            if (textMonth) {
                String text = formatter.formatCellValue(columns.cell(row, MONTH)).trim();
                Matcher matcher = MONTH_PATTERN.matcher(text);
                if (!matcher.matches()) {
                    warnings.add(type.prefix + "Dòng " + (row.getRowNum() + 1) + " của " + sheet.getSheetName()
                            + " có cột Tháng không hợp lệ: '" + text + "' — bỏ qua");
                    return null;
                }
                month = Integer.parseInt(matcher.group(1)); year = Integer.parseInt(matcher.group(2));
            } else {
                year = datePart(columns.cell(row, YEAR), formatter, type);
                month = datePart(columns.cell(row, MONTH), formatter, type);
            }
            return LocalDate.of(year, month, datePart(columns.cell(row, DAY), formatter, type));
        } catch (DateTimeException | IllegalArgumentException exception) {
            warnings.add(type.prefix + "Dòng " + (row.getRowNum() + 1) + " của " + sheet.getSheetName()
                    + " có ngày không hợp lệ — bỏ qua");
            return null;
        }
    }

    private int datePart(Cell cell, DataFormatter formatter, IncidentType type) {
        if (cell == null) throw new IllegalArgumentException("Ngày trống");
        CellType cellType = cachedType(cell);
        if (cellType == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (type == IncidentType.SYSTEM) return (int) value;
            if (Double.isFinite(value) && value == Math.rint(value) && value >= 1 && value <= 9999) return (int) value;
        } else if (cellType == CellType.STRING) return Integer.parseInt(cell.getStringCellValue().trim());
        else if (type == IncidentType.SYSTEM) return Integer.parseInt(formatter.formatCellValue(cell).trim());
        throw new IllegalArgumentException("Ngày không hợp lệ");
    }

    private double numeric(Cell cell, Sheet sheet, int row, String header, IncidentType type, List<String> warnings) {
        if (cell != null) {
            CellType cellType = cachedType(cell);
            if (cellType == CellType.NUMERIC) {
                double value = cell.getNumericCellValue();
                if (Double.isFinite(value) && value >= 0) return value;
            } else if (cellType == CellType.STRING) {
                // Excel có thể lưu số nhập/dán vào ô dưới dạng text, kể cả kết quả công thức.
                String text = cell.getStringCellValue().replace('\u00a0', ' ').trim();
                try {
                    double value = Double.parseDouble(text);
                    if (Double.isFinite(value) && value >= 0) return value;
                } catch (NumberFormatException ignored) {
                    // Giữ cảnh báo cho ô trống hoặc text không phải số; không suy diễn thành số 0 hợp lệ.
                }
            }
        }
        warnings.add(type.prefix + sheet.getSheetName() + " dòng " + (row + 1) + " có '" + header
                + "' trống hoặc không hợp lệ — không cộng chỉ số này");
        return 0;
    }

    private CellType cachedType(Cell cell) {
        return cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
    }

    private MissingChartDataException missing(String text) { return new MissingChartDataException(text); }

    private enum IncidentType {
        SYSTEM("SYSTEM_INCIDENT_CHART: ", 2), STATION("STATION_INCIDENT_CHART: ", 3),
        CABLE("CABLE_INCIDENT_CHART: ", 3);
        final String prefix;
        final int seriesCount;
        IncidentType(String prefix, int seriesCount) { this.prefix = prefix; this.seriesCount = seriesCount; }
    }

    private static class Columns {
        final Map<String, Integer> columns;
        final int dataStart;
        Columns(Map<String, Integer> columns, int dataStart) { this.columns = columns; this.dataStart = dataStart; }
        Cell cell(Row row, String name) {
            Integer index = columns.get(name);
            return index == null ? null : row.getCell(index);
        }
    }

    public static class MissingChartDataException extends RuntimeException {
        public MissingChartDataException(String message) { super(message); }
    }
}
