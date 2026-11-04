package com.techasians.alertservice.service;

import com.techasians.alertservice.dto.ColumnMetadata;
import com.techasians.alertservice.dto.CreatedEventInfo;
import com.techasians.alertservice.dto.ImportResultDTO;
import com.techasians.alertservice.entity.*;
import com.techasians.alertservice.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.SortedSet;
import java.util.TreeSet;


/**
 * Service xử lý import dữ liệu lưu lượng lịch sử từ file Excel.
 *
 * Cấu trúc file Excel:
 * - Mỗi Sheet = 1 khu vực (HNI, HCM, DNG, TQ...)
 * - Hàng 1: Năm, Quý, Tháng, Ngày, Thứ, Sự kiện định kỳ, Sự kiện phát sinh |
 * [Tên Kênh Cha merged]...
 * - Hàng 2: (trống cho các cột chung) | [Tên Kênh Con merged] hoặc (trống nếu
 * không có kênh con)
 * - Hàng 3: (trống cho các cột chung) | [Tên Metric]...
 * - Hàng 4+: Dữ liệu, mỗi hàng = 1 ngày
 *
 * Logic xử lý duplicate:
 * - Trong cùng 1 sheet, nếu có 2 dòng cùng ngày (trùng trafficDate) thì coi là
 * DUPLICATE
 * - Khi có duplicate: rollback toàn bộ, trả về file Excel có cột "Kết quả" ghi
 * thông báo lỗi
 * - Cột "Kết quả" được merge ở row 0, row 1, row 2 (giống cấu trúc header)
 * - Cả 2 dòng trùng đều bị đánh dấu lỗi
 */
@Service
public class TrafficHistoryImportService {

    // Logger để đo thời gian từng bước - giúp tìm điểm bottleneck
    private static final Logger log = LoggerFactory.getLogger(TrafficHistoryImportService.class);

    // Các index cột cố định trong Excel (0-based)
    private static final int COL_YEAR = 0; // Năm
    private static final int COL_QUARTER = 1; // Quý
    private static final int COL_MONTH = 2; // Tháng
    private static final int COL_DAY = 3; // Ngày
    private static final int COL_WEEKDAY = 4; // Thứ
    private static final int COL_EVENT_PERIODIC = 5; // Sự kiện định kỳ
    private static final int COL_EVENT_INCIDENT = 6; // Sự kiện phát sinh
    private static final int COL_DATA_START = 7; // Cột bắt đầu dữ liệu metric

    // Số hàng header
    private static final int HEADER_ROWS = 4; // 4 hàng: Lưu ý | Kênh Cha | Kênh Con | Tên Metric


    // Tỷ lệ tác động mặc định cho sự kiện phát sinh được auto-create khi import
    @Value("${app.traffic.import.default-event-impact-rate:5}")
    private BigDecimal defaultEventImpactRate;

    private final TrafficRegionRepository trafficRegionRepository;
    private final TrafficRegionChannelRepository trafficRegionChannelRepository;
    private final TrafficChannelRepository trafficChannelRepository;
    private final TrafficChannelMetricRepository trafficChannelMetricRepository;
    private final TrafficHistoryRepository trafficHistoryRepository;
    private final TrafficHistoryMetricValueRepository trafficHistoryMetricValueRepository;
    private final TrafficEventRepository trafficEventRepository;
    private final TrafficEventScheduleRepository trafficEventScheduleRepository;
    private final TrafficEventChannelRepository trafficEventChannelRepository;
    private final TrafficHistoryEventRepository trafficHistoryEventRepository;
    // Repository dùng JDBC batch INSERT thực sự (bypass hạn chế của Hibernate
    // IDENTITY)
    private final TrafficHistoryBatchRepository trafficHistoryBatchRepository;

    public TrafficHistoryImportService(
            TrafficRegionRepository trafficRegionRepository,
            TrafficRegionChannelRepository trafficRegionChannelRepository,
            TrafficChannelRepository trafficChannelRepository,
            TrafficChannelMetricRepository trafficChannelMetricRepository,
            TrafficHistoryRepository trafficHistoryRepository,
            TrafficHistoryMetricValueRepository trafficHistoryMetricValueRepository,
            TrafficEventRepository trafficEventRepository,
            TrafficEventScheduleRepository trafficEventScheduleRepository,
            TrafficEventChannelRepository trafficEventChannelRepository,
            TrafficHistoryEventRepository trafficHistoryEventRepository,
            TrafficHistoryBatchRepository trafficHistoryBatchRepository) {
        this.trafficRegionRepository = trafficRegionRepository;
        this.trafficRegionChannelRepository = trafficRegionChannelRepository;
        this.trafficChannelRepository = trafficChannelRepository;
        this.trafficChannelMetricRepository = trafficChannelMetricRepository;
        this.trafficHistoryRepository = trafficHistoryRepository;
        this.trafficHistoryMetricValueRepository = trafficHistoryMetricValueRepository;
        this.trafficEventRepository = trafficEventRepository;
        this.trafficEventScheduleRepository = trafficEventScheduleRepository;
        this.trafficEventChannelRepository = trafficEventChannelRepository;
        this.trafficHistoryEventRepository = trafficHistoryEventRepository;
        this.trafficHistoryBatchRepository = trafficHistoryBatchRepository;
    }

    /**
     * Import dữ liệu từ file Excel.
     * Mỗi Sheet trong file tương ứng với một khu vực trong DB.
     * Tên Sheet phải khớp với tên khu vực (TrafficRegion.name) trong DB.
     *
     * Nếu phát hiện duplicate (cùng ngày trong 1 sheet):
     * - Không insert gì vào DB (rollback toàn bộ)
     * - Trả về ImportResultDTO chứa file Excel báo lỗi
     */
    @Transactional
    public ImportResultDTO importExcel(MultipartFile file) throws IOException {
        // ---- BẮT ĐẦU ĐO THỜI GIAN TỔNG ----
        long startTotal = System.currentTimeMillis();
        int totalRows = 0;
        int successRows = 0;
        int skippedRows = 0;

        // Đọc toàn bộ dữ liệu Region, Channel, Metric vào Map để tra cứu nhanh (tránh
        // query DB liên tục trong vòng lặp)
        // Key: tên (viết thường, trim) -> Value: Entity
        long t0 = System.currentTimeMillis();
        Map<String, TrafficRegion> regionMap = buildRegionMap();
        Map<String, TrafficChannel> channelMap = buildChannelMap();
        Map<String, TrafficEvent> eventMap = buildEventMap();
        // Fail-fast nếu không tìm thấy kênh mặc định — không cho phép import tiếp tục
        TrafficChannel defaultChannel = findDefaultChannel(channelMap);
        // Track schedule mới tạo trong phiên import (tránh tạo trùng cùng ngày)
        Map<String, TrafficEventSchedule> newScheduleMap = new HashMap<>();
        // Gom danh sách sự kiện được auto-create để trả về trong response
        List<CreatedEventInfo> createdEvents = new ArrayList<>();
        log.info("[TIMING] Bước 1 - Load lookup maps từ DB: {} ms", System.currentTimeMillis() - t0);

        // Đọc toàn bộ file Excel vào bộ nhớ để có thể dùng lại khi cần ghi lỗi
        byte[] fileBytes = file.getBytes();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            log.info("[TIMING] File Excel có {} sheet(s)", workbook.getNumberOfSheets());

            // -----------------------------------------------------------------------
            // BƯỚC KIỂM TRA DUPLICATE TRƯỚC - quét toàn bộ file trước khi insert
            // Nếu phát hiện bất kỳ duplicate nào -> dừng lại, ghi lỗi, trả file
            // -----------------------------------------------------------------------

            // validationErrors: sheetIndex -> (rowIndex -> danh sách message lỗi)
            // Gom TẤT CẢ lỗi (ngày/tháng/năm sai, thiếu, duplicate) vào đây
            // Dùng List<String> vì một dòng có thể có nhiều lỗi cùng lúc
            Map<Integer, Map<Integer, List<String>>> validationErrors = new LinkedHashMap<>();

            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                String sheetName = sheet.getSheetName().trim();

                TrafficRegion region = regionMap.get(sheetName.toLowerCase());
                if (region == null) {
                    continue;
                }

                // Tìm cột cuối của sheet để biết có bao nhiêu cột metric
                int lastDataCol = findLastColumnIndex(sheet);

                // Map để track duplicate: ngày (LocalDate) -> danh sách rowIndex có ngày đó
                Map<LocalDate, List<Integer>> dateToRows = new LinkedHashMap<>();

                // Map lưu lỗi của từng sheet: rowIndex -> danh sách message lỗi
                Map<Integer, List<String>> sheetErrors = new LinkedHashMap<>();

                for (int rowIndex = HEADER_ROWS; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row == null) {
                        continue;
                    }

                    // Đọc raw string của 3 ô ngày/tháng/năm để biết ô nào đang có giá trị
                    String yearRaw  = getCellStringValue(row.getCell(COL_YEAR));
                    String monthRaw = getCellStringValue(row.getCell(COL_MONTH));
                    String dayRaw   = getCellStringValue(row.getCell(COL_DAY));

                    boolean hasYear  = yearRaw  != null;
                    boolean hasMonth = monthRaw != null;
                    boolean hasDay   = dayRaw   != null;
                    boolean hasAnyDateField = hasYear || hasMonth || hasDay;

                    // Trường hợp: không nhập ngày/tháng/năm gì cả
                    if (!hasAnyDateField) {
                        // Nếu dòng có giá trị metric -> báo lỗi vì thiếu khóa ngày
                        if (hasMetricDataInRow(row, lastDataCol)) {
                            List<String> rowErrors = sheetErrors.computeIfAbsent(rowIndex, k -> new ArrayList<>());
                            rowErrors.add("- Ngày không hợp lệ: bắt buộc nhập ngày từ 1 đến 31.");
                            rowErrors.add("- Tháng không hợp lệ: bắt buộc nhập tháng từ 1 đến 12.");
                            rowErrors.add("- Năm không hợp lệ: bắt buộc nhập năm gồm 4 số và nhỏ hơn hoặc bằng 2100.");
                        }
                        // Nếu không có metric -> bỏ qua (dòng trống)
                        continue;
                    }

                    // Trường hợp: đã nhập ít nhất 1 trong 3 phần -> validate đủ cả 3
                    List<String> dateErrors = validateDateColumns(row);
                    if (!dateErrors.isEmpty()) {
                        // Dòng bị lỗi ngày/tháng/năm -> ghi lỗi, KHÔNG đưa vào duplicate check
                        sheetErrors.computeIfAbsent(rowIndex, k -> new ArrayList<>()).addAll(dateErrors);
                        continue;
                    }

                    // Ngày/tháng/năm hợp lệ -> đưa vào duplicate check
                    LocalDate trafficDate = readTrafficDate(row);
                    if (trafficDate != null) {
                        dateToRows.computeIfAbsent(trafficDate, d -> new ArrayList<>()).add(rowIndex);
                    }

                    // Validate độ dài tên sự kiện (định kỳ và phát sinh)
                    List<String> eventNameErrors = validateEventNames(row);
                    if (!eventNameErrors.isEmpty()) {
                        sheetErrors.computeIfAbsent(rowIndex, k -> new ArrayList<>()).addAll(eventNameErrors);
                    }
                }

                // Tìm những ngày xuất hiện >= 2 lần (duplicate) trong sheet này
                for (Map.Entry<LocalDate, List<Integer>> entry : dateToRows.entrySet()) {
                    List<Integer> rows = entry.getValue();
                    if (rows.size() >= 2) {
                        // Đánh dấu TẤT CẢ các dòng trùng (cả dòng đầu lẫn dòng sau)
                        String duplicateMessage = String.format(
                                "- Dữ liệu trùng lặp: ngày %s xuất hiện %d lần trong sheet này.",
                                entry.getKey(), rows.size());
                        for (int rowIndex : rows) {
                            sheetErrors.computeIfAbsent(rowIndex, k -> new ArrayList<>()).add(duplicateMessage);
                        }
                    }
                }

                if (!sheetErrors.isEmpty()) {
                    validationErrors.put(sheetIndex, sheetErrors);
                    log.warn("[VALIDATE] Sheet '{}': phát hiện {} dòng bị lỗi.",
                            sheetName, sheetErrors.size());
                }
            }

            // Nếu có bất kỳ lỗi nào -> tạo file báo lỗi và trả về NGAY (không insert DB)
            if (!validationErrors.isEmpty()) {
                log.warn("[VALIDATE] Phát hiện lỗi trong file. Rollback toàn bộ, trả về file lỗi.");
                byte[] errorFileBytes = buildErrorFile(fileBytes, validationErrors);
                return new ImportResultDTO(errorFileBytes);
            }

            // -----------------------------------------------------------------------
            // KHÔNG CÓ DUPLICATE -> Thực hiện import bình thường
            // -----------------------------------------------------------------------

            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                String sheetName = sheet.getSheetName().trim();

                // Tìm khu vực tương ứng trong DB theo tên Sheet
                TrafficRegion region = regionMap.get(sheetName.toLowerCase());
                if (region == null) {
                    // Không tìm thấy khu vực -> bỏ qua toàn bộ sheet này
                    continue;
                }

                // ---- ĐO THỜI GIAN TỪNG SHEET ----
                long startSheet = System.currentTimeMillis();
                log.info("[TIMING] === Bắt đầu xử lý sheet: '{}' ===", sheetName);

                // Đọc và phân tích 3 hàng Header để xây dựng cấu hình cột
                List<ColumnMetadata> columnConfigs = parseHeaderRows(sheet);

                // Xây dựng Map tra cứu nhanh: channelMetric theo (channelName|metricName)
                Map<String, TrafficChannelMetric> channelMetricMap = buildChannelMetricMap(columnConfigs, channelMap);

                // Xây dựng Map tra cứu nhanh: channelName -> TrafficRegionChannel (1 query /
                // sheet)
                Map<String, TrafficRegionChannel> regionChannelMap = buildRegionChannelMap(region.getId());

                // Bộ đệm (Cache) lưu trữ các TrafficHistory vừa tạo trong Sheet hiện tại
                // Giúp tránh lỗi Duplicate Key nếu trong Excel có nhiều dòng trùng cùng 1 ngày
                Map<String, TrafficHistory> historyCache = new HashMap<>();

                List<TrafficHistory> historyBatch = new ArrayList<>();
                List<TrafficHistoryMetricValue> metricValueBatch = new ArrayList<>();
                List<TrafficHistoryEvent> eventBatch = new ArrayList<>();

                // Theo dõi các kênh bị skip - chỉ log WARNING 1 lần mỗi kênh, tránh spam log
                Set<String> missingChannels = new HashSet<>();

                // Tiền xử lý: Thu thập tất cả ngày có trong Sheet vào SortedSet (tự sắp xếp tăng dần)
                long tScan = System.currentTimeMillis();
                SortedSet<LocalDate> allDates = new TreeSet<>();
                for (int rowIndex = HEADER_ROWS; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row == null)
                        continue;
                    LocalDate date = readTrafficDate(row);
                    if (date != null) {
                        allDates.add(date);
                    }
                }
                log.info("[TIMING] Bước 2 - Scan all dates ({} rows, {} unique dates): {} ms",
                        sheet.getLastRowNum() - HEADER_ROWS + 1, allDates.size(), System.currentTimeMillis() - tScan);

                // Smart Delete: xóa theo từng khoảng ngày liên tiếp, tránh xóa nhầm
                // ngày không có trong file (ví dụ file có ngày 1,2,3,7,10 thì chỉ xóa
                // [1→3], [7→7], [10→10] — không xóa ngày 4,5,6,8,9 đang có trong DB)
                if (!allDates.isEmpty() && !regionChannelMap.isEmpty()) {
                    List<Long> rcIdList = new ArrayList<>();
                    for (TrafficRegionChannel rc : regionChannelMap.values()) {
                        rcIdList.add(rc.getId());
                    }

                    // Tính danh sách các khoảng ngày liên tiếp từ allDates
                    List<LocalDate[]> dateRanges = buildDateRanges(allDates);
                    log.info("[TIMING] Bước 3 - Smart delete {} date range(s): {}",
                            dateRanges.size(), dateRanges.stream()
                                    .map(r -> r[0] + "→" + r[1])
                                    .collect(java.util.stream.Collectors.joining(", ")));

                    long tDelete = System.currentTimeMillis();
                    for (LocalDate[] range : dateRanges) {
                        trafficHistoryRepository.bulkDeleteMetricValues(rcIdList, range[0], range[1]);
                    }
                    log.info("[TIMING] Bước 3a - bulkDeleteMetricValues ({} ranges): {} ms",
                            dateRanges.size(), System.currentTimeMillis() - tDelete);

                    tDelete = System.currentTimeMillis();
                    for (LocalDate[] range : dateRanges) {
                        trafficHistoryRepository.bulkDeleteEvents(rcIdList, range[0], range[1]);
                    }
                    log.info("[TIMING] Bước 3b - bulkDeleteEvents ({} ranges): {} ms",
                            dateRanges.size(), System.currentTimeMillis() - tDelete);

                    tDelete = System.currentTimeMillis();
                    for (LocalDate[] range : dateRanges) {
                        trafficHistoryRepository.bulkDeleteHistories(rcIdList, range[0], range[1]);
                    }
                    log.info("[TIMING] Bước 3c - bulkDeleteHistories ({} ranges): {} ms",
                            dateRanges.size(), System.currentTimeMillis() - tDelete);
                }

                // Duyệt qua từng hàng dữ liệu để gom vào Batch (bắt đầu sau hàng header)
                long tBuildBatch = System.currentTimeMillis();
                for (int rowIndex = HEADER_ROWS; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row == null) {
                        continue;
                    }

                    // totalRows đếm số cặp (kênh × ngày) - nhất quán với successRows và skippedRows
                    // Không đếm ở đây, sẽ đếm bên trong vòng lặp kênh bên dưới

                    try {
                        // Đọc ngày từ các cột cố định
                        LocalDate trafficDate = readTrafficDate(row);
                        if (trafficDate == null) {
                            skippedRows++;
                            continue;
                        }

                        // Nhóm các cột lại theo kênh (channelName), xử lý mỗi kênh 1 lần
                        Set<String> processedChannels = new HashSet<>();
                        for (ColumnMetadata colMeta : columnConfigs) {
                            // normalizeKey: xóa khoảng trắng đầu cuối + giữa các từ + chữ thường
                            String channelKey = normalizeKey(colMeta.getChannelName());

                            if (processedChannels.contains(channelKey)) {
                                continue;
                            }
                            processedChannels.add(channelKey);

                            // Mỗi cặp (kênh × ngày) = 1 đơn vị xử lý
                            totalRows++;

                            // Tìm kênh trong DB
                            TrafficChannel channel = channelMap.get(channelKey);
                            if (channel == null) {
                                // Chỉ log 1 lần đầu cho mỗi kênh bị thiếu (tránh spam 1253 dòng)
                                if (missingChannels.add("channel:" + channelKey)) {
                                    log.warn("[SKIP] Kênh '{}' trong sheet '{}' KHÔNG TỒN TẠI trong DB (channelMap). " +
                                            "Kiểm tra lại tên kênh trong file Excel và bảng traffic_channels.",
                                            channelKey, sheetName);
                                }
                                skippedRows++;
                                continue;
                            }

                            // Tìm mapping region-channel từ cache (không query DB)
                            TrafficRegionChannel regionChannel = regionChannelMap.get(channelKey);
                            if (regionChannel == null) {
                                // Chỉ log 1 lần đầu cho mỗi kênh bị thiếu
                                if (missingChannels.add("region:" + channelKey)) {
                                    log.warn("[SKIP] Kênh '{}' trong sheet '{}' CHƯA ĐƯỢC MAP với khu vực này. " +
                                            "Kiểm tra bảng traffic_region_channels.",
                                            channelKey, sheetName);
                                }
                                skippedRows++;
                                continue;
                            }

                            // Xác định có sự kiện không
                            boolean hasEvent = hasEventInRow(row);

                            // Tạo TrafficHistory mới (gom vào batch, sinh ID hàng loạt lúc saveAll)
                            TrafficHistory history = new TrafficHistory()
                                    .setRegionChannel(regionChannel)
                                    .setTrafficDate(trafficDate)
                                    .setHasEvent(hasEvent ? 1 : 0)
                                    .setCreatedBy("import");
                            historyBatch.add(history);

                            // Gom metric values của kênh này vào batch (saveAll 1 lần cuối sheet)
                            for (ColumnMetadata innerCol : columnConfigs) {
                                if (!normalizeKey(innerCol.getChannelName()).equals(channelKey)) {
                                    continue;
                                }

                                BigDecimal value = readCellAsBigDecimal(row.getCell(innerCol.getColumnIndex()));
                                if (value == null) {
                                    continue;
                                }

                                String metricLookupKey = channelKey + "|"
                                        + normalizeKey(innerCol.getMetricName());
                                TrafficChannelMetric channelMetric = channelMetricMap.get(metricLookupKey);
                                if (channelMetric == null) {
                                    continue;
                                }

                                // Round to nearest integer if data_type == 1
                                if (channelMetric.getMetric() != null 
                                        && channelMetric.getMetric().getDataType() != null 
                                        && channelMetric.getMetric().getDataType() == 1) {
                                    value = value.setScale(0, java.math.RoundingMode.HALF_UP);
                                }

                                metricValueBatch.add(new TrafficHistoryMetricValue()
                                        .setTrafficHistory(history)
                                        .setChannelMetric(channelMetric)
                                        .setMetricValue(value)
                                        .setCreatedBy("import"));
                            }

                            // Gom events của kênh này vào batch
                            if (hasEvent) {
                                collectEvents(row, history, trafficDate,
                                        eventMap, newScheduleMap, defaultChannel,
                                        createdEvents, eventBatch);
                            }

                            successRows++;
                        }

                    } catch (Exception e) {
                        skippedRows++;
                    }
                }
                log.info("[TIMING] Bước 4 - Gom batch từ Excel: {} ms | histories={}, metricValues={}, events={}",
                        System.currentTimeMillis() - tBuildBatch,
                        historyBatch.size(), metricValueBatch.size(), eventBatch.size());

                // --- JDBC Batch INSERT thực sự (không dùng saveAll() vì IDENTITY chặn
                // batching) ---
                //
                // Cách hoạt động:
                // 1. batchInsertHistories: gửi 1 câu SQL INSERT gồm nhiều VALUES cho 500 rows
                // một lần.
                // Sau đó đọc GENERATED KEYS để biết ID của từng history -> gán lại vào entity.
                // 2. batchInsertMetricValues: tương tự, dùng history.getId() (đã có từ bước 1)
                // làm FK.
                // 3. batchInsertEvents: tương tự.
                long tSaveHistory = System.currentTimeMillis();
                if (!historyBatch.isEmpty()) {
                    trafficHistoryBatchRepository.batchInsertHistories(historyBatch);
                    // Sau bước này, mọi history trong historyBatch đã được gán ID bởi DB.
                }
                log.info("[TIMING] Bước 5a - batchInsert histories ({} records): {} ms",
                        historyBatch.size(), System.currentTimeMillis() - tSaveHistory);

                // metricValues và events dùng history.getId() làm FK -> chỉ chạy được sau bước
                // trên
                long tSaveMetric = System.currentTimeMillis();
                if (!metricValueBatch.isEmpty()) {
                    trafficHistoryBatchRepository.batchInsertMetricValues(metricValueBatch);
                }
                log.info("[TIMING] Bước 5b - batchInsert metricValues ({} records): {} ms",
                        metricValueBatch.size(), System.currentTimeMillis() - tSaveMetric);

                long tSaveEvent = System.currentTimeMillis();
                if (!eventBatch.isEmpty()) {
                    trafficHistoryBatchRepository.batchInsertEvents(eventBatch);
                }
                log.info("[TIMING] Bước 5c - batchInsert events ({} records): {} ms",
                        eventBatch.size(), System.currentTimeMillis() - tSaveEvent);

                // Log tổng kết kênh bị bỏ qua trong sheet này
                if (!missingChannels.isEmpty()) {
                    log.warn("[SUMMARY] Sheet '{}': có {} kênh bị bỏ qua -> {}",
                            sheetName, missingChannels.size(), missingChannels);
                } else {
                    log.info("[SUMMARY] Sheet '{}': tất cả kênh đều được xử lý thành công.", sheetName);
                }

                log.info("[TIMING] === Sheet '{}' hoàn tất: {} ms ===",
                        sheetName, System.currentTimeMillis() - startSheet);
            }
        }

        log.info("[TIMING] === TỔNG THỜI GIAN IMPORT: {} ms | total={}, success={}, skip={} ===",
                System.currentTimeMillis() - startTotal, totalRows, successRows, skippedRows);
        return new ImportResultDTO(totalRows, successRows, skippedRows, createdEvents);
    }

    // =========================================================================
    // PHẦN XỬ LÝ DUPLICATE: Ghi cột "Kết quả" vào file Excel báo lỗi
    // =========================================================================

    /**
     * Tạo file Excel báo lỗi từ file gốc.
     *
     * Với mỗi sheet có duplicate:
     * 1. Tìm cột cuối cùng (lastCol) của sheet đó
     * 2. Thêm header "Kết quả" ở cột lastCol, merge qua row 0, 1, 2
     * 3. Ghi message lỗi vào từng dòng bị duplicate
     *
     * @param originalFileBytes nội dung file gốc (để đọc lại với XSSFWorkbook mới)
     * @param duplicateErrors   sheetIndex -> (rowIndex -> errorMessage)
     * @return nội dung file Excel đã ghi lỗi, dạng byte[]
     */
    private byte[] buildErrorFile(
            byte[] originalFileBytes,
            Map<Integer, Map<Integer, List<String>>> validationErrors) throws IOException {

        // Mở lại workbook từ byte[] gốc để tránh xung đột với workbook đang dùng
        try (Workbook errorWorkbook = new XSSFWorkbook(
                new java.io.ByteArrayInputStream(originalFileBytes))) {

            // Tạo style cho header "Kết quả": nền trắng, chữ đen, bold, căn giữa
            CellStyle headerStyle = createResultHeaderStyle(errorWorkbook);

            // Tạo style cho ô lỗi: nền vàng, chữ đỏ, wrap text
            CellStyle errorCellStyle = createErrorCellStyle(errorWorkbook);

            for (Map.Entry<Integer, Map<Integer, List<String>>> entry : validationErrors.entrySet()) {
                int sheetIndex = entry.getKey();
                Map<Integer, List<String>> rowErrors = entry.getValue();

                Sheet sheet = errorWorkbook.getSheetAt(sheetIndex);

                // Tìm số cột cuối cùng trong sheet (dựa vào row 2 - hàng metric)
                int lastCol = findLastColumnIndex(sheet);

                // Thêm header "Kết quả" với merge cell ở row 0, 1, 2
                writeResultHeader(sheet, lastCol, headerStyle);

                // Ghi message lỗi vào từng dòng bị lỗi
                // Nếu có nhiều message, nối chúng bằng ký tự xuống dòng \n
                for (Map.Entry<Integer, List<String>> rowEntry : rowErrors.entrySet()) {
                    int rowIndex = rowEntry.getKey();
                    // Nối tất cả message của dòng đó thành 1 chuỗi, mỗi message 1 dòng
                    String errorMessage = String.join("\n", rowEntry.getValue());

                    Row row = sheet.getRow(rowIndex);
                    if (row == null) {
                        row = sheet.createRow(rowIndex);
                    }
                    Cell errorCell = row.createCell(lastCol);
                    errorCell.setCellValue(errorMessage);
                    errorCell.setCellStyle(errorCellStyle);
                }

                // Tự động căn chỉnh độ rộng cột "Kết quả" cho dễ đọc
                sheet.autoSizeColumn(lastCol);
            }

            // Serialize workbook thành byte[]
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            errorWorkbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * Tìm chỉ số cột cuối cùng trong sheet.
     * Dùng row 2 (hàng tên metric) vì đây là hàng có nhiều cột nhất.
     * Nếu không có dữ liệu thì fallback về COL_DATA_START.
     */
    private int findLastColumnIndex(Sheet sheet) {
        // Kiểm tra row 0, 1, 2 để lấy max lastCellNum
        int maxCol = COL_DATA_START;
        for (int i = 0; i < HEADER_ROWS; i++) {
            Row row = sheet.getRow(i);
            if (row != null && row.getLastCellNum() > maxCol) {
                maxCol = row.getLastCellNum();
            }
        }
        // row.getLastCellNum() trả về chỉ số sau cột cuối (1-indexed), nên đây chính là
        // lastCol
        return maxCol;
    }

    /**
     * Thêm header "Kết quả" vào cột lastCol của sheet, merge qua row 0, 1, 2.
     *
     * Trước: row0[...kênh...] | (chưa có)
     * row1[...kênh...] | (chưa có)
     * row2[...metric] | (chưa có)
     *
     * Sau: row0[...kênh...] | "Kết quả" (merged 3 ô)
     * row1[...kênh...] | (merged)
     * row2[...metric] | (merged)
     */
    private void writeResultHeader(Sheet sheet, int lastCol, CellStyle headerStyle) {
        // Tạo ô header ở row 0
        Row row0 = sheet.getRow(0);
        if (row0 == null) {
            row0 = sheet.createRow(0);
        }
        Cell headerCell = row0.createCell(lastCol);
        headerCell.setCellValue("Kết quả");
        headerCell.setCellStyle(headerStyle);

        // Tạo ô trống ở row 1, row 2 (cần tồn tại để merge hoạt động đúng)
        for (int rowIdx = 1; rowIdx < HEADER_ROWS; rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) {
                row = sheet.createRow(rowIdx);
            }
            Cell cell = row.createCell(lastCol);
            cell.setCellStyle(headerStyle);
        }

        // Merge 3 ô: row 0 đến row 2, cùng cột lastCol
        // CellRangeAddress(firstRow, lastRow, firstCol, lastCol)
        sheet.addMergedRegion(new CellRangeAddress(0, HEADER_ROWS - 1, lastCol, lastCol));
    }

    /**
     * Tạo style cho header "Kết quả": nền trắng, chữ đen, bold, căn giữa, có
     * border.
     */
    private CellStyle createResultHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();

        // Nền trắng
        style.setFillForegroundColor(IndexedColors.WHITE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Chữ đen, bold
        Font font = workbook.createFont();
        font.setColor(IndexedColors.BLACK.getIndex());
        font.setBold(true);
        style.setFont(font);

        // Căn giữa theo chiều ngang và chiều dọc
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        // Bật wrap text để text dài hiển thị đầy đủ
        style.setWrapText(true);

        // Border 4 phía: thin (mảnh)
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.BLACK.getIndex());
        style.setBottomBorderColor(IndexedColors.BLACK.getIndex());
        style.setLeftBorderColor(IndexedColors.BLACK.getIndex());
        style.setRightBorderColor(IndexedColors.BLACK.getIndex());

        return style;
    }

    /**
     * Tạo style cho ô dữ liệu bị lỗi: nền vàng, chữ đỏ, wrap text.
     */
    private CellStyle createErrorCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();

        // Nền vàng
        style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Chữ đỏ
        Font font = workbook.createFont();
        font.setColor(IndexedColors.RED.getIndex());
        style.setFont(font);

        // Wrap text để hiển thị đầy đủ message
        style.setWrapText(true);

        return style;
    }

    // =========================================================================
    // PHẦN ĐỌC DỮ LIỆU HEADER VÀ CỘT (giữ nguyên như cũ)
    // =========================================================================

    /**
     * Đọc các hàng Header của Sheet và xây dựng danh sách ColumnMetadata.
     *
     * Cấu trúc header (tính ngược từ HEADER_ROWS):
     * HEADER_ROWS - 3: Tên Kênh Cha (merged across nhiều cột)
     * HEADER_ROWS - 2: Tên Kênh Con (merged, hoặc trống nếu không có kênh con)
     * HEADER_ROWS - 1: Tên Metric (hàng ngay trước dữ liệu)
     *
     * Ví dụ khi HEADER_ROWS = 4:
     * Row index 1 = Kênh Cha
     * Row index 2 = Kênh Con
     * Row index 3 = Tên Metric
     * Row index 4+ = Dữ liệu
     */
    private List<ColumnMetadata> parseHeaderRows(Sheet sheet) {
        List<ColumnMetadata> result = new ArrayList<>();

        // Tính index của từng hàng header dựa vào HEADER_ROWS
        // → khi thay đổi HEADER_ROWS thì parseHeaderRows tự động cập nhật theo
        int rowIndexParentChannel = HEADER_ROWS - 3; // Kênh Cha
        int rowIndexChildChannel = HEADER_ROWS - 2; // Kênh Con
        int rowIndexMetric = HEADER_ROWS - 1; // Tên Metric

        Row rowParent = sheet.getRow(rowIndexParentChannel);
        Row rowMetric = sheet.getRow(rowIndexMetric);

        if (rowParent == null || rowMetric == null) {
            return result;
        }

        // Lấy tất cả merged regions để tra cứu nhanh
        List<CellRangeAddress> mergedRegions = sheet.getMergedRegions();

        int lastCol = rowMetric.getLastCellNum();
        for (int colIndex = COL_DATA_START; colIndex < lastCol; colIndex++) {
            // Đọc tên Kênh Cha (có thể là merged cell)
            String parentChannelName = getMergedCellValue(sheet, mergedRegions, rowIndexParentChannel, colIndex);

            // Đọc tên Kênh Con (có thể là merged cell, hoặc trống)
            String childChannelName = getMergedCellValue(sheet, mergedRegions, rowIndexChildChannel, colIndex);

            // Đọc tên Metric
            String metricName = getCellStringValue(rowMetric.getCell(colIndex));

            if (metricName == null || metricName.isEmpty()) {
                continue;
            }

            // Nếu không có kênh con thì channelName = kênh cha, parentChannelName = null
            String channelName;
            String parent;
            if (childChannelName == null || childChannelName.isEmpty()) {
                channelName = parentChannelName;
                parent = null;
            } else {
                channelName = childChannelName;
                parent = parentChannelName;
            }

            if (channelName == null || channelName.isEmpty()) {
                continue;
            }

            result.add(new ColumnMetadata(channelName, parent, metricName, colIndex));
        }

        return result;
    }

    /**
     * Lấy giá trị text của một ô, có xử lý trường hợp ô đó nằm trong merged region.
     * Nếu ô hiện tại trống nhưng nằm trong merged region thì trả về giá trị của ô
     * đầu tiên trong merged region.
     */
    private String getMergedCellValue(Sheet sheet, List<CellRangeAddress> mergedRegions, int rowIndex, int colIndex) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            return null;
        }

        Cell cell = row.getCell(colIndex);
        String directValue = getCellStringValue(cell);

        // Nếu ô có giá trị thì dùng luôn
        if (directValue != null && !directValue.isEmpty()) {
            return directValue;
        }

        // Nếu không có, tìm trong merged regions xem cột này thuộc merged region nào
        for (CellRangeAddress range : mergedRegions) {
            if (range.isInRange(rowIndex, colIndex)) {
                // Lấy giá trị từ ô đầu tiên (góc trên bên trái) của merged region
                Row firstRow = sheet.getRow(range.getFirstRow());
                if (firstRow != null) {
                    return getCellStringValue(firstRow.getCell(range.getFirstColumn()));
                }
            }
        }

        return null;
    }

    /**
     * Đọc giá trị text của một ô (Cell).
     * Trả về null nếu ô rỗng hoặc null.
     */
    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        switch (cell.getCellType()) {
            case STRING:
                String val = cell.getStringCellValue().trim();
                return val.isEmpty() ? null : val;
            case NUMERIC:
                // Chuyển số sang chuỗi (ví dụ: số nguyên 2023 -> "2023")
                double num = cell.getNumericCellValue();
                if (num == Math.floor(num)) {
                    return String.valueOf((long) num);
                }
                return String.valueOf(num);
            case FORMULA:
                try {
                    return cell.getStringCellValue().trim();
                } catch (Exception e) {
                    return String.valueOf((long) cell.getNumericCellValue());
                }
            default:
                return null;
        }
    }

    /**
     * Đọc giá trị số (BigDecimal) của một ô.
     * Trả về null nếu không đọc được hoặc ô trống/không phải số.
     */
    private BigDecimal readCellAsBigDecimal(Cell cell) {
        if (cell == null) {
            return null;
        }
        try {
            switch (cell.getCellType()) {
                case NUMERIC:
                    BigDecimal numericValue = BigDecimal.valueOf(cell.getNumericCellValue());
                    String formatString = cell.getCellStyle() != null ? cell.getCellStyle().getDataFormatString() : "";
                    if (formatString != null && formatString.contains("%")) {
                        return numericValue.multiply(new BigDecimal("100"));
                    }
                    return numericValue;
                case STRING:
                    String str = cell.getStringCellValue().trim().replace("%", "").replace(",", ".");
                    if (str.isEmpty()) {
                        return null;
                    }
                    return new BigDecimal(str);
                case FORMULA:
                    return BigDecimal.valueOf(cell.getNumericCellValue());
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Đọc ngày từ các cột Năm, Tháng, Ngày.
     * Tháng trong Excel có dạng "T01", "T02"... -> lấy số sau chữ T.
     */
    private LocalDate readTrafficDate(Row row) {
        try {
            String yearStr = getCellStringValue(row.getCell(COL_YEAR));
            String monthStr = getCellStringValue(row.getCell(COL_MONTH));
            String dayStr = getCellStringValue(row.getCell(COL_DAY));

            if (yearStr == null || monthStr == null || dayStr == null) {
                return null;
            }

            // Năm: "N2023" -> 2023
            int year = Integer.parseInt(yearStr.replaceAll("[^0-9]", ""));

            // Tháng: "T01" -> 1, hoặc "1" -> 1
            int month = Integer.parseInt(monthStr.replaceAll("[^0-9]", ""));

            // Ngày: "N01" -> 1, hoặc "1" -> 1
            int day = Integer.parseInt(dayStr.replaceAll("[^0-9]", ""));

            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Kiểm tra xem dòng có sự kiện nào không (cột Sự kiện định kỳ hoặc Sự kiện phát
     * sinh có giá trị).
     */
    private boolean hasEventInRow(Row row) {
        String periodicEvent = getCellStringValue(row.getCell(COL_EVENT_PERIODIC));
        String incidentEvent = getCellStringValue(row.getCell(COL_EVENT_INCIDENT));
        return (periodicEvent != null && !periodicEvent.isEmpty())
                || (incidentEvent != null && !incidentEvent.isEmpty());
    }

    /**
     * Gom sự kiện của một dòng vào danh sách eventBatch (không saveAll ngay).
     * 1 cell có thể chứa nhiều sự kiện, mỗi sự kiện trên 1 dòng (ngăn cách bởi \n).
     *
     * Nếu sự kiện không tồn tại trong DB → tự động tạo mới TrafficEvent,
     * TrafficEventSchedule (exact_date = trafficDate) và TrafficEventChannel (kênh mặc định).
     */
    private void collectEvents(Row row, TrafficHistory history, LocalDate trafficDate,
            Map<String, TrafficEvent> eventMap,
            Map<String, TrafficEventSchedule> newScheduleMap,
            TrafficChannel defaultChannel,
            List<CreatedEventInfo> createdEvents,
            List<TrafficHistoryEvent> eventBatch) {

        // Đọc sự kiện định kỳ (event_type = 1)
        String periodicEventName = getCellStringValue(row.getCell(COL_EVENT_PERIODIC));
        if (periodicEventName != null && !periodicEventName.isEmpty()) {
            addEventsFromText(periodicEventName, 1, history, trafficDate,
                    eventMap, newScheduleMap, defaultChannel, createdEvents, eventBatch);
        }

        // Đọc sự kiện phát sinh (event_type = 2)
        String incidentEventName = getCellStringValue(row.getCell(COL_EVENT_INCIDENT));
        if (incidentEventName != null && !incidentEventName.isEmpty()) {
            addEventsFromText(incidentEventName, 2, history, trafficDate,
                    eventMap, newScheduleMap, defaultChannel, createdEvents, eventBatch);
        }
    }

    /**
     * Tách text thành nhiều sự kiện theo ký tự xuống dòng, sau đó map từng sự kiện vào DB.
     *
     * Quy tắc:
     * - Tìm thấy event → link thẳng traffic_event_id (không tạo mới)
     * - Không tìm thấy event → auto-create TrafficEvent + TrafficEventSchedule
     *   + TrafficEventChannel (kênh mặc định) → cập nhật cache
     *
     * Xử lý format số thứ tự đầu dòng:
     * "1.Chu kỳ cước ngày 21" → "Chu kỳ cước ngày 21"
     */
    private void addEventsFromText(
            String cellText, int eventType, TrafficHistory history, LocalDate trafficDate,
            Map<String, TrafficEvent> eventMap,
            Map<String, TrafficEventSchedule> newScheduleMap,
            TrafficChannel defaultChannel,
            List<CreatedEventInfo> createdEvents,
            List<TrafficHistoryEvent> events) {

        // Tách theo ký tự xuống dòng (\n hoặc \r\n)
        String[] lines = cellText.split("\\r?\\n");

        for (String line : lines) {
            // Xóa số thứ tự đầu dòng dạng "1.", "2.", "10." nếu có
            String eventName = line.trim().replaceFirst("^\\d+\\.\\s*", "").trim();
            if (eventName.isEmpty()) {
                continue;
            }

            String eventKey = normalizeKey(eventName);
            TrafficEvent event = eventMap.get(eventKey);

            boolean isNewEventInThisSession = false;

            if (event == null) {
                // ── AUTO-CREATE: TrafficEvent ────────────────────────────────────────
                // Định kỳ: repeat_type=1 (hằng tháng), pattern_type=2 (không quy luật)
                // Phát sinh: repeat_type=null, pattern_type=null
                event = new TrafficEvent()
                        .setName(eventName)
                        .setEventType(eventType)
                        .setRepeatType(eventType == 1 ? 1 : null)
                        .setPatternType(eventType == 1 ? 2 : null)
                        .setStatus(1)
                        .setCreatedBy("import")
                        .setUpdatedBy("import");
                trafficEventRepository.save(event);   // save ngay để có ID

                // ── AUTO-CREATE: TrafficEventChannel (kênh mặc định) ──────────────
                // Phát sinh: impact_rate = defaultEventImpactRate
                // Định kỳ:  impact_rate = null
                BigDecimal impactRate = (eventType == 2) ? defaultEventImpactRate : null;
                trafficEventChannelRepository.save(new TrafficEventChannel()
                        .setEvent(event)
                        .setChannel(defaultChannel)
                        .setImpactRate(impactRate)
                        .setCreatedBy("import")
                        .setUpdatedBy("import"));

                // Cập nhật cache để các dòng sau không tạo trùng event
                eventMap.put(eventKey, event);
                // Ghi nhận event mới để trả về trong response
                createdEvents.add(new CreatedEventInfo(event.getId(), event.getName()));
                isNewEventInThisSession = true;
            } else {
                // Kiểm tra xem event này có phải được tạo ra trong phiên import này không
                for (CreatedEventInfo info : createdEvents) {
                    if (info.getId().equals(event.getId())) {
                        isNewEventInThisSession = true;
                        break;
                    }
                }
            }

            if (isNewEventInThisSession) {
                // ── Tìm hoặc tạo TrafficEventSchedule cho ngày này ────────────────────
                // Tránh tạo trùng nếu cùng event mới + cùng ngày xuất hiện nhiều lần trong file
                String scheduleKey = event.getId() + "_" + trafficDate;
                if (!newScheduleMap.containsKey(scheduleKey)) {
                    TrafficEventSchedule schedule = new TrafficEventSchedule()
                            .setEvent(event)
                            .setExactDate(trafficDate)
                            .setCreatedBy("import")
                            .setUpdatedBy("import");
                    trafficEventScheduleRepository.save(schedule);
                    newScheduleMap.put(scheduleKey, schedule);
                }
            }

            // ── Tạo TrafficHistoryEvent link với TrafficEvent ─────────────────────
            events.add(new TrafficHistoryEvent()
                    .setTrafficHistory(history)
                    .setTrafficEvent(event)
                    .setCreatedBy("import"));
        }
    }

    /**
     * Tìm kênh mặc định 'KÊnh MẶC ĐỊNH' trong channelMap.
     * Fail-fast nếu không tồn tại — không cho phép import chạy khi thiếu kênh mặc định.
     */
    private TrafficChannel findDefaultChannel(Map<String, TrafficChannel> channelMap) {
        TrafficChannel channel = channelMap.get(normalizeKey("KÊnh MẶC ĐỊNH"));
        if (channel == null) {
            throw new IllegalStateException(
                    "Kênh mặc định 'KÊnh MẶC ĐỊNH' không tồn tại trong DB. "
                    + "Vui lòng tạo kênh này trước khi import.");
        }
        return channel;
    }

    // =========================================================================
    // PHẦN BUILD LOOKUP MAPS (giữ nguyên như cũ)
    // =========================================================================

    /**
     * Xây dựng Map: tên khu vực (đã normalize) -> TrafficRegion
     */
    private Map<String, TrafficRegion> buildRegionMap() {
        Map<String, TrafficRegion> map = new HashMap<>();
        trafficRegionRepository.findByStatus(1).forEach(r -> map.put(normalizeKey(r.getName()), r));
        return map;
    }

    /**
     * Xây dựng Map: tên kênh (đã normalize) -> TrafficChannel
     */
    private Map<String, TrafficChannel> buildChannelMap() {
        Map<String, TrafficChannel> map = new HashMap<>();
        // normalizeKey: xóa khoảng trắng đầu cuối + giữa các từ + chữ thường
        trafficChannelRepository.findByStatus(1).forEach(c -> map.put(normalizeKey(c.getName()), c));
        return map;
    }

    /**
     * Xây dựng Map: tên sự kiện (lowercase) -> TrafficEvent
     * Chỉ lấy event đang áp dụng và chưa bị xóa mềm.
     */
    private Map<String, TrafficEvent> buildEventMap() {
        Map<String, TrafficEvent> map = new HashMap<>();
        // Khi build từ DB lên, cũng chuẩn hóa tên để Map Key giống với khi xử lý file Excel
        trafficEventRepository.findByStatusAndDeletedAtIsNull(1)
                .forEach(e -> map.put(normalizeKey(e.getName()), e));
        return map;
    }


    /**
     * Xây dựng Map: channelName (đã normalize) -> TrafficRegionChannel cho một khu
     * vực.
     * Thực hiện 1 query duy nhất để lấy tất cả active region-channel của region đó.
     */
    private Map<String, TrafficRegionChannel> buildRegionChannelMap(
            Long regionId) {

        Map<String, TrafficRegionChannel> map = new HashMap<>();
        List<TrafficRegionChannel> list = trafficRegionChannelRepository.findActiveByRegionId(regionId);
        for (TrafficRegionChannel trc : list) {
            // normalizeKey: xóa khoảng trắng đầu cuối + giữa các từ + chữ thường
            String channelName = normalizeKey(trc.getChannel().getName());
            map.put(channelName, trc);
        }
        return map;
    }

    /**
     * Xây dựng Map tra cứu nhanh: "channelName|metricName" -> TrafficChannelMetric
     * Dùng 1 query IN (list channelId) thay vì N query riêng lẻ.
     */
    private Map<String, TrafficChannelMetric> buildChannelMetricMap(
            List<ColumnMetadata> columnConfigs,
            Map<String, TrafficChannel> channelMap) {

        Map<String, TrafficChannelMetric> map = new HashMap<>();

        // Thu thập danh sách channelId cần tra cứu
        Set<Integer> channelIds = new HashSet<>();
        for (ColumnMetadata colMeta : columnConfigs) {
            // normalizeKey khi tra cứu từ Excel vào channelMap (đã normalize khi build)
            TrafficChannel channel = channelMap.get(normalizeKey(colMeta.getChannelName()));
            if (channel != null) {
                channelIds.add(channel.getId());
            }
        }

        if (channelIds.isEmpty()) {
            return map;
        }

        // 1 query duy nhất lấy toàn bộ channelMetric của tất cả kênh liên quan
        List<TrafficChannelMetric> allMetrics = trafficChannelMetricRepository
                .findActiveByChannelIds(new ArrayList<>(channelIds));

        for (TrafficChannelMetric cm : allMetrics) {
            // Key cũng dùng normalizeKey để nhất quán với channelKey trong vòng lặp
            String key = normalizeKey(cm.getChannel().getName())
                    + "|"
                    + normalizeKey(cm.getMetric().getName());
            map.put(key, cm);
        }

        return map;
    }

    /**
     * Nhóm danh sách ngày (đã sắp xếp tăng dần) thành các khoảng ngày liên tiếp.
     *
     * Ví dụ:
     *   Input:  [1, 2, 3, 4, 7, 10, 12, 13, 14]
     *   Output: [[1→4], [7→7], [10→10], [12→14]]
     *
     * Logic:
     * - Duyệt từng ngày trong SortedSet (đã tự sắp xếp tăng dần)
     * - Nếu ngày hiện tại = ngày trước + 1 ngày → mở rộng khoảng đang mở
     * - Nếu không liên tiếp → đóng khoảng cũ, bắt đầu khoảng mới
     * - Cuối vòng lặp: đừng quên thêm khoảng cuối cùng
     *
     * Mục đích: tránh xóa những ngày trong DB mà file import không đề cập đến.
     *
     * @param dates Tập ngày đã sắp xếp tăng dần (TreeSet đảm bảo điều này)
     * @return Danh sách các khoảng, mỗi khoảng là LocalDate[]{startDate, endDate}
     */
    private List<LocalDate[]> buildDateRanges(SortedSet<LocalDate> dates) {
        List<LocalDate[]> ranges = new ArrayList<>();
        LocalDate rangeStart = null;
        LocalDate rangeEnd = null;

        for (LocalDate date : dates) {
            if (rangeStart == null) {
                // Bắt đầu khoảng đầu tiên
                rangeStart = date;
                rangeEnd = date;
            } else if (date.equals(rangeEnd.plusDays(1))) {
                // Ngày liên tiếp → mở rộng khoảng đang mở
                rangeEnd = date;
            } else {
                // Không liên tiếp → đóng khoảng cũ, bắt đầu khoảng mới
                ranges.add(new LocalDate[]{rangeStart, rangeEnd});
                rangeStart = date;
                rangeEnd = date;
            }
        }

        // Thêm khoảng cuối cùng (vòng lặp kết thúc mà chưa đóng khoảng)
        if (rangeStart != null) {
            ranges.add(new LocalDate[]{rangeStart, rangeEnd});
        }

        return ranges;
    }

    /**
     * Chuẩn hóa tên kênh/metric để so sánh nhất quán giữa DB và Excel.
     *
     * Thực hiện 3 bước:
     * 1. trim() - xóa khoảng trắng đầu và cuối chuỗi
     * VD: " SMS " -> "SMS"
     * 2. replaceAll(\s+) - thay nhiều khoảng trắng liên tiếp giữa các từ thành 1
     * dấu cách
     * VD: "SMS Tin nhắn" -> "SMS Tin nhắn"
     * 3. toLowerCase() - chuyển toàn bộ về chữ thường để so sánh không phân biệt
     * hoa/thường
     * VD: "SMS Tin nhắn" -> "sms tin nhắn"
     *
     * Sai (trước): "SMS" vs "sms" -> không khớp
     * Đúng (sau): normalizeKey("SMS") = normalizeKey("sms") = "sms"
     *
     * Sai (trước): "SMS Tin nhắn" vs "SMS Tin nhắn" -> không khớp (2 space vs 1
     * space)
     * Đúng (sau): normalizeKey("SMS Tin nhắn") = normalizeKey("SMS Tin nhắn") =
     * "sms tin nhắn"
     */
    private String normalizeKey(String name) {
        if (name == null) {
            return "";
        }
        // Bước 1+2: trim đầu cuối rồi xóa khoảng trắng thừa giữa các từ
        // Bước 3: chuyển về chữ thường
        return name.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    /**
     * Đọc giá trị số nguyên từ một ô trong row.
     *
     * Ví dụ: "T01" -> 1, "N15" -> 15, "2024" -> 2024, "" -> null
     *
     * @param row         dòng đang xử lý
     * @param columnIndex vị trí cột cần đọc (0-based)
     * @return số nguyên đọc được, hoặc null nếu ô trống/không đọc được
     */
    private Integer parseIntegerFromCell(Row row, int columnIndex) {
        String value = getCellStringValue(row.getCell(columnIndex));
        if (value == null) {
            return null;
        }
        // Xóa tất cả ký tự không phải số, ví dụ: "T01" -> "01"
        String numberOnly = value.replaceAll("[^0-9]", "");
        if (numberOnly.isEmpty()) {
            return null;
        }
        return Integer.parseInt(numberOnly);
    }

    /**
     * Kiểm tra xem dòng có chứa bất kỳ giá trị metric nào không.
     * Quét từ cột COL_DATA_START đến lastDataCol (exclusive).
     *
     * Mục đích: phân biệt dòng trống hoàn toàn và dòng có metric nhưng thiếu ngày.
     *
     * @param row        dòng đang xử lý
     * @param lastDataCol vị trí cột cuối + 1 (giá trị trả về của getLastCellNum)
     * @return true nếu có ít nhất 1 ô metric có giá trị
     */
    private boolean hasMetricDataInRow(Row row, int lastDataCol) {
        for (int colIndex = COL_DATA_START; colIndex < lastDataCol; colIndex++) {
            Cell cell = row.getCell(colIndex);
            if (cell == null) {
                continue;
            }
            // Chỉ cần ô không trống là đủ
            if (cell.getCellType() != CellType.BLANK) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validate ngày/tháng/năm của một dòng Excel.
     *
     * Quy tắc:
     * - Ngày: phải có giá trị và từ 1 đến 31
     * - Tháng: phải có giá trị và từ 1 đến 12
     * - Năm: phải có giá trị và từ 1000 đến 2100 (4 số, <= 2100)
     *
     * Ví dụ sai: ngày 0, 32; tháng 0, 13; năm 999, 20245, 2101
     * Ví dụ đúng: ngày 1-31, tháng 1-12, năm 1000-2100
     *
     * @param row dòng cần validate
     * @return danh sách message lỗi (bắt đầu bằng dấu '-'), rỗng nếu hợp lệ
     */
    private List<String> validateDateColumns(Row row) {
        List<String> errors = new ArrayList<>();

        Integer day   = parseIntegerFromCell(row, COL_DAY);
        Integer month = parseIntegerFromCell(row, COL_MONTH);
        Integer year  = parseIntegerFromCell(row, COL_YEAR);

        // Validate ngày: phải từ 1 đến 31
        if (day == null || day < 1 || day > 31) {
            errors.add("- Ngày không hợp lệ: bắt buộc nhập ngày từ 1 đến 31.");
        }

        // Validate tháng: phải từ 1 đến 12
        if (month == null || month < 1 || month > 12) {
            errors.add("- Tháng không hợp lệ: bắt buộc nhập tháng từ 1 đến 12.");
        }

        // Validate năm: phải là 4 số (>= 1000) và <= 2100
        if (year == null || year < 1000 || year > 2100) {
            errors.add("- Năm không hợp lệ: bắt buộc nhập năm gồm 4 số và nhỏ hơn hoặc bằng 2100.");
        }

        return errors;
    }

    /**
     * Validate độ dài tên sự kiện trong 2 cột: Sự kiện định kỳ và Sự kiện phát sinh.
     *
     * Mỗi cell có thể chứa nhiều sự kiện cách nhau bằng ký tự xuống dòng (\n).
     * Với từng dòng (tên sự kiện đơn lẻ):
     * - Bỏ số thứ tự đầu dòng dạng "1.", "2." (giống logic trong addEventsFromText)
     * - Nếu tên sau khi làm sạch vượt quá 500 ký tự → báo lỗi
     *
     * Ví dụ sai:  tên sự kiện dài 501 ký tự
     * Ví dụ đúng: tên sự kiện dài 500 ký tự trở xuống
     *
     * @param row dòng đang validate
     * @return danh sách message lỗi (bắt đầu bằng '-'), rỗng nếu hợp lệ
     */
    private List<String> validateEventNames(Row row) {
        List<String> errors = new ArrayList<>();

        // Kiểm tra cả 2 cột sự kiện
        int[] eventColumns = {COL_EVENT_PERIODIC, COL_EVENT_INCIDENT};
        String[] columnLabels = {"Sự kiện định kỳ", "Sự kiện phát sinh"};

        for (int i = 0; i < eventColumns.length; i++) {
            String cellText = getCellStringValue(row.getCell(eventColumns[i]));
            if (cellText == null || cellText.isEmpty()) {
                continue;
            }

            // Tách theo ký tự xuống dòng (giống logic trong addEventsFromText)
            String[] lines = cellText.split("\\r?\\n");
            for (String line : lines) {
                // Bỏ số thứ tự đầu dòng dạng "1.", "2.", "10." nếu có
                String eventName = line.trim().replaceFirst("^\\d+\\.\\s*", "").trim();
                if (eventName.isEmpty()) {
                    continue;
                }

                if (eventName.length() > 500) {
                    errors.add(String.format(
                            "- Tên sự kiện ở cột '%s' vượt quá 500 ký tự (hiện tại: %d ký tự): \"%s...\".",
                            columnLabels[i],
                            eventName.length(),
                            eventName.substring(0, 50)));
                }
            }
        }

        return errors;
    }
}
