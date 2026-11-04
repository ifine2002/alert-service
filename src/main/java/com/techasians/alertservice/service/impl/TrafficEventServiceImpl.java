package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.dto.*;
import com.techasians.alertservice.entity.*;
import com.techasians.alertservice.repository.*;
import com.techasians.alertservice.service.TrafficEventService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TrafficEventServiceImpl implements TrafficEventService {

    private static final BigDecimal MAX_IMPACT_RATE = BigDecimal.valueOf(100);

    private final TrafficEventRepository eventRepository;
    private final TrafficEventScheduleRepository scheduleRepository;
    private final TrafficEventChannelRepository channelRepository;
    private final TrafficChannelRepository trafficChannelRepository;

    public TrafficEventServiceImpl(
            TrafficEventRepository eventRepository,
            TrafficEventScheduleRepository scheduleRepository,
            TrafficEventChannelRepository channelRepository,
            TrafficChannelRepository trafficChannelRepository) {
        this.eventRepository = eventRepository;
        this.scheduleRepository = scheduleRepository;
        this.channelRepository = channelRepository;
        this.trafficChannelRepository = trafficChannelRepository;
    }

    // =========================================================================
    // CREATE
    // =========================================================================

    @Override
    @Transactional
    public TrafficEventResponse create(TrafficEventRequest request, String createdBy) {
        // Validate nghiệp vụ — truyền null để biết đây là create (không loại trừ id
        // nào)
        Map<Integer, TrafficChannel> channelMap = new HashMap<>();
        validateRequest(request, channelMap, null);

        // Tạo và lưu entity TrafficEvent
        TrafficEvent event = new TrafficEvent()
                .setName(request.getName().trim())
                .setEventType(request.getEventType())
                .setRepeatType(request.getRepeatType())
                .setPatternType(request.getPatternType())
                .setStatus(request.getStatus())
                .setCreatedBy(createdBy)
                .setUpdatedBy(createdBy);
        eventRepository.save(event);

        // Lưu schedules
        List<TrafficEventSchedule> schedules = buildScheduleEntities(request, event, createdBy);
        scheduleRepository.saveAll(schedules);

        // Lưu channel mappings
        List<TrafficEventChannel> channels = buildChannelEntities(request, event, channelMap, createdBy);
        channelRepository.saveAll(channels);

        return toDetailResponse(event, schedules, channels);
    }

    // =========================================================================
    // UPDATE — Diff-based sync
    // =========================================================================

    @Override
    @Transactional
    public TrafficEventResponse update(Integer id, TrafficEventRequest request, String updatedBy) {
        // Kiểm tra event tồn tại và chưa bị xóa mềm
        TrafficEvent event = eventRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Sự kiện không tồn tại hoặc đã bị xóa"));

        // Validate nghiệp vụ — truyền id để loại trừ khi check tên trùng
        Map<Integer, TrafficChannel> channelMap = new HashMap<>();
        validateRequest(request, channelMap, id);

        // Cập nhật các field của event
        event.setName(request.getName().trim())
                .setEventType(request.getEventType())
                .setRepeatType(request.getRepeatType())
                .setPatternType(request.getPatternType())
                .setStatus(request.getStatus())
                .setUpdatedBy(updatedBy);
        eventRepository.save(event);

        // Diff-sync schedules
        List<TrafficEventSchedule> updatedSchedules = syncSchedules(event, request, updatedBy);

        // Diff-sync channels
        List<TrafficEventChannel> updatedChannels = syncChannels(event, request, channelMap, updatedBy);

        return toDetailResponse(event, updatedSchedules, updatedChannels);
    }

    // =========================================================================
    // DELETE — Soft delete
    // =========================================================================

    @Override
    @Transactional
    public void delete(Integer id, String deletedBy) {
        TrafficEvent event = eventRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Sự kiện không tồn tại hoặc đã bị xóa"));

        // Chỉ set deleted_at và deleted_by — schedules và channels giữ nguyên
        event.setDeletedAt(LocalDateTime.now())
                .setDeletedBy(deletedBy);
        eventRepository.save(event);
    }

    // =========================================================================
    // GET BY ID
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public TrafficEventResponse getById(Integer id) {
        TrafficEvent event = eventRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Sự kiện không tồn tại hoặc đã bị xóa"));

        List<TrafficEventSchedule> schedules = scheduleRepository.findByEventId(id);
        List<TrafficEventChannel> channels = channelRepository.findByEventId(id);

        return toDetailResponse(event, schedules, channels);
    }

    // =========================================================================
    // SEARCH
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public PageResult<TrafficEventResponse> search(TrafficEventSearchRequest request) {
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
        // Trim tên để tránh khoảng trắng thừa ảnh hưởng đến LIKE
        String nameTrim = (request.getName() != null && !request.getName().isBlank())
                ? request.getName().trim()
                : null;

        Page<TrafficEvent> pageData = eventRepository.search(nameTrim, request.getEventType(), request.getStatus(),
                pageable);
        List<TrafficEvent> events = pageData.getContent();

        if (events.isEmpty()) {
            return PageResult.of(Collections.emptyList(), pageData.getTotalElements(), request.getPage(),
                    request.getSize());
        }

        List<Integer> eventIds = events.stream().map(TrafficEvent::getId).collect(Collectors.toList());

        List<TrafficEventSchedule> allSchedules = scheduleRepository.findByEventIdIn(eventIds);
        Map<Integer, List<TrafficEventSchedule>> scheduleMap = allSchedules.stream()
                .collect(Collectors.groupingBy(s -> s.getEvent().getId()));

        List<TrafficEventChannel> allChannels = channelRepository.findByEventIdIn(eventIds);
        Map<Integer, List<TrafficEventChannel>> channelMap = allChannels.stream()
                .collect(Collectors.groupingBy(c -> c.getEvent().getId()));

        List<TrafficEventResponse> responses = events.stream().map(event -> {
            List<TrafficEventSchedule> schedules = scheduleMap.getOrDefault(event.getId(), Collections.emptyList());
            List<TrafficEventChannel> channels = channelMap.getOrDefault(event.getId(), Collections.emptyList());
            return toDetailResponse(event, schedules, channels);
        }).collect(Collectors.toList());

        return PageResult.of(responses, pageData.getTotalElements(), request.getPage(), request.getSize());
    }


    // =========================================================================
    // IMPORT EXCEL
    // =========================================================================

    private static final String PERIODIC_SHEET_NAME = "Sự kiện định kỳ";
    private static final String INCIDENT_SHEET_NAME = "Sự kiện phát sinh";
    private static final String RESULT_HEADER = "Kết quả";
    private static final int HEADER_ROW_INDEX = 0;
    private static final int DATA_ROW_START_INDEX = 1;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("d/M/yyyy");

    private static final int PERIODIC_COL_SCHEDULE = 0;
    private static final int PERIODIC_COL_NAME = 1;
    private static final int PERIODIC_COL_REPEAT_TYPE = 2;
    private static final int PERIODIC_COL_PATTERN_TYPE = 3;
    private static final int PERIODIC_COL_CHANNELS = 4;
    private static final int PERIODIC_COL_RESULT = 5;

    private static final int INCIDENT_COL_SCHEDULE = 0;
    private static final int INCIDENT_COL_NAME = 1; 
    private static final int INCIDENT_COL_CHANNELS = 2;
    private static final int INCIDENT_COL_IMPACT_RATES = 3;
    private static final int INCIDENT_COL_RESULT = 4;

    @Override
    @Transactional
    public TrafficEventImportResult importFile(MultipartFile file, String importedBy) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File import không được để trống");
        }

        try (InputStream inputStream = file.getInputStream(); Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet periodicSheet = workbook.getSheet(PERIODIC_SHEET_NAME);
            Sheet incidentSheet = workbook.getSheet(INCIDENT_SHEET_NAME);
            if (periodicSheet == null || incidentSheet == null) {
                throw new IllegalArgumentException("File import không đúng template. Vui lòng tải template mới nhất");
            }

            validateImportHeader(periodicSheet, new String[] {
                    "Lịch diễn ra", "Sự kiện định kỳ", "Chu kỳ lặp", "Quy luật", "Kênh ảnh hưởng"
            });
            validateImportHeader(incidentSheet, new String[] {
                    "Lịch diễn ra", "Sự kiện phát sinh", "Kênh ảnh hưởng", "% Dự báo tăng/giảm lưu lượng"
            });

            Map<String, TrafficEvent> eventByName = buildExistingEventMap();
            Map<String, TrafficChannel> channelByName = buildActiveChannelMap();

            List<TrafficEventImportRow> rows = new ArrayList<>();
            readImportSheet(periodicSheet, 1, rows, eventByName, channelByName);
            readImportSheet(incidentSheet, 2, rows, eventByName, channelByName);


            int errorRows = 0;
            for (TrafficEventImportRow row : rows) {
                if (row.hasErrors()) {
                    errorRows++;
                }
            }

            TrafficEventImportResult result = new TrafficEventImportResult();
            if (errorRows > 0) {
                result.setErrorFile(buildImportErrorFile(workbook, rows));
                return result;
            }

            int createdRows = 0;
            int updatedRows = 0;
            Map<String, TrafficEvent> createdEventByName = new HashMap<>();
            for (TrafficEventImportRow row : rows) {
                String eventNameKey = normalizeText(row.getEventName());
                if (row.getExistingEvent() == null && createdEventByName.containsKey(eventNameKey)) {
                    row.setExistingEvent(createdEventByName.get(eventNameKey));
                    saveImportedSchedule(row, importedBy);
                    updatedRows++;
                } else if (row.getExistingEvent() == null) {
                    TrafficEvent createdEvent = saveImportedEvent(row, importedBy);
                    createdEventByName.put(eventNameKey, createdEvent);
                    createdRows++;
                } else {
                    saveImportedSchedule(row, importedBy);
                    updatedRows++;
                }
            }

            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi xử lý file import sự kiện: " + e.getMessage(), e);
        }
    }

    private void validateImportHeader(Sheet sheet, String[] expectedHeaders) {
        Row headerRow = sheet.getRow(HEADER_ROW_INDEX);
        if (headerRow == null) {
            throw new IllegalArgumentException("Sheet '" + sheet.getSheetName() + "' thiếu dòng tiêu đề");
        }

        int actualColumnCount = countHeaderColumns(headerRow);
        if (actualColumnCount != expectedHeaders.length) {
            throw new IllegalArgumentException("Sheet '" + sheet.getSheetName()
                    + "' phải có đúng " + expectedHeaders.length
                    + " cột dữ liệu, hiện tại có " + actualColumnCount + " cột");
        }

        for (int i = 0; i < expectedHeaders.length; i++) {
            String actualHeader = readCellAsString(headerRow.getCell(i));
            if (actualHeader == null || !actualHeader.trim().equals(expectedHeaders[i])) {
                throw new IllegalArgumentException("Sheet '" + sheet.getSheetName()
                        + "' sai cột '" + expectedHeaders[i] + "' ở vị trí " + (i + 1));
            }
        }
    }

    private int countHeaderColumns(Row headerRow) {
        int count = 0;
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            String value = readCellAsString(headerRow.getCell(i));
            if (value != null && !value.trim().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private Map<String, TrafficEvent> buildExistingEventMap() {
        Map<String, TrafficEvent> result = new HashMap<>();
        List<TrafficEvent> events = eventRepository.findByDeletedAtIsNull();
        for (TrafficEvent event : events) {
            result.put(normalizeText(event.getName()), event);
        }
        return result;
    }

    private Map<String, TrafficChannel> buildActiveChannelMap() {
        Map<String, TrafficChannel> result = new HashMap<>();
        List<TrafficChannel> channels = trafficChannelRepository.findByStatus(1);
        for (TrafficChannel channel : channels) {
            result.put(normalizeText(channel.getName()), channel);
        }
        return result;
    }

    private void readImportSheet(Sheet sheet, int sheetEventType, List<TrafficEventImportRow> rows,
            Map<String, TrafficEvent> eventByName, Map<String, TrafficChannel> channelByName) {
        for (int rowIndex = DATA_ROW_START_INDEX; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row excelRow = sheet.getRow(rowIndex);
            if (isExcelRowEmpty(excelRow)) {
                continue;
            }

            TrafficEventImportRow importRow = new TrafficEventImportRow();
            importRow.setRowIndex(rowIndex);
            importRow.setSheetEventType(sheetEventType);
            importRow.setResultColumnIndex(sheetEventType == 1 ? PERIODIC_COL_RESULT : INCIDENT_COL_RESULT);

            if (sheetEventType == 1) {
                readPeriodicImportRow(excelRow, importRow, eventByName, channelByName);
            } else {
                readIncidentImportRow(excelRow, importRow, eventByName, channelByName);
            }
            rows.add(importRow);
        }
    }

    private void readPeriodicImportRow(Row excelRow, TrafficEventImportRow importRow,
            Map<String, TrafficEvent> eventByName, Map<String, TrafficChannel> channelByName) {
        String scheduleText = readCellAsString(excelRow.getCell(PERIODIC_COL_SCHEDULE));
        String eventName = readCellAsString(excelRow.getCell(PERIODIC_COL_NAME));
        String repeatText = readCellAsString(excelRow.getCell(PERIODIC_COL_REPEAT_TYPE));
        String patternText = readCellAsString(excelRow.getCell(PERIODIC_COL_PATTERN_TYPE));
        String channelText = readCellAsString(excelRow.getCell(PERIODIC_COL_CHANNELS));

        importRow.setEventName(trimToNull(eventName));
        if (importRow.getEventName() == null) {
            importRow.addError("Tên sự kiện định kỳ không được để trống");
            return;
        }
        if (importRow.getEventName().length() > 500) {
            importRow.addError("Tên sự kiện định kỳ không được vượt quá 500 ký tự");
            return;
        }

        importRow.setExistingEvent(eventByName.get(normalizeText(importRow.getEventName())));
        if (importRow.getExistingEvent() != null) {
            validateExistingEventSheet(importRow, 1);
            importRow.setScheduleRequest(parseScheduleForExistingEvent(scheduleText, importRow.getExistingEvent(), importRow.getErrors()));
            return;
        }

        Integer repeatType = parseRepeatType(repeatText);
        Integer patternType = parsePatternType(patternText);
        if (repeatType == null) {
            importRow.addError("Chu kỳ lặp không hợp lệ, chỉ chấp nhận Hằng tháng, Hằng năm hoặc Hằng quý");
        }
        if (patternType == null) {
            importRow.addError("Quy luật không hợp lệ, chỉ chấp nhận Có quy luật hoặc Không có quy luật");
        }

        List<TrafficEventChannelRequest> channels = parsePeriodicChannels(channelText, channelByName, importRow.getErrors());
        List<TrafficEventScheduleRequest> schedules = new ArrayList<>();
        if (repeatType != null && patternType != null) {
            TrafficEventScheduleRequest schedule = parseScheduleByTypes(scheduleText, 1, repeatType, patternType, importRow.getErrors());
            if (schedule != null) {
                importRow.setScheduleRequest(schedule);
                schedules.add(schedule);
            }
        }

        importRow.setRequest(new TrafficEventRequest()
                .setName(importRow.getEventName())
                .setEventType(1)
                .setRepeatType(repeatType)
                .setPatternType(patternType)
                .setStatus(1)
                .setSchedules(schedules)
                .setChannels(channels));
        validateCreateImportRow(importRow);
    }

    private void readIncidentImportRow(Row excelRow, TrafficEventImportRow importRow,
            Map<String, TrafficEvent> eventByName, Map<String, TrafficChannel> channelByName) {
        String scheduleText = readCellAsString(excelRow.getCell(INCIDENT_COL_SCHEDULE));
        String eventName = readCellAsString(excelRow.getCell(INCIDENT_COL_NAME));
        String channelText = readCellAsString(excelRow.getCell(INCIDENT_COL_CHANNELS));
        String impactRateText = readImpactRateCellAsString(excelRow.getCell(INCIDENT_COL_IMPACT_RATES));

        importRow.setEventName(trimToNull(eventName));
        if (importRow.getEventName() == null) {
            importRow.addError("Tên sự kiện phát sinh không được để trống");
            return;
        }
        if (importRow.getEventName().length() > 500) {
            importRow.addError("Tên sự kiện phát sinh không được vượt quá 500 ký tự");
            return;
        }

        importRow.setExistingEvent(eventByName.get(normalizeText(importRow.getEventName())));
        if (importRow.getExistingEvent() != null) {
            validateExistingEventSheet(importRow, 2);
            importRow.setScheduleRequest(parseScheduleForExistingEvent(scheduleText, importRow.getExistingEvent(), importRow.getErrors()));
            return;
        }

        List<TrafficEventChannelRequest> channels = parseIncidentChannels(channelText, impactRateText, channelByName, importRow.getErrors());
        List<TrafficEventScheduleRequest> schedules = new ArrayList<>();
        TrafficEventScheduleRequest schedule = parseExactDateSchedule(scheduleText, importRow.getErrors(),
                "Lịch diễn ra của sự kiện phát sinh phải nhập theo định dạng dd/MM/yyyy");
        if (schedule != null) {
            importRow.setScheduleRequest(schedule);
            schedules.add(schedule);
        }

        importRow.setRequest(new TrafficEventRequest()
                .setName(importRow.getEventName())
                .setEventType(2)
                .setStatus(1)
                .setSchedules(schedules)
                .setChannels(channels));
        validateCreateImportRow(importRow);
    }

    private void validateExistingEventSheet(TrafficEventImportRow row, int expectedEventType) {
        if (row.getExistingEvent().getEventType() == null || row.getExistingEvent().getEventType() != expectedEventType) {
            if (expectedEventType == 1) {
                row.addError("Sự kiện đã tồn tại là sự kiện phát sinh, không được nhập ở sheet Sự kiện định kỳ");
            } else {
                row.addError("Sự kiện đã tồn tại là sự kiện định kỳ, không được nhập ở sheet Sự kiện phát sinh");
            }
        }
    }

    private void validateCreateImportRow(TrafficEventImportRow row) {
        if (row.getRequest() == null || row.hasErrors()) {
            return;
        }
        try {
            validateRequest(row.getRequest(), new HashMap<>(), null);
        } catch (IllegalArgumentException e) {
            row.addError(e.getMessage());
        }
    }

    private TrafficEventScheduleRequest parseScheduleByTypes(String scheduleText, int eventType, Integer repeatType,
            Integer patternType, List<String> errors) {
        if (eventType == 2 || (eventType == 1 && patternType != null && patternType == 2)) {
            return parseExactDateSchedule(scheduleText, errors,
                    "Lịch diễn ra phải nhập theo định dạng dd/MM/yyyy với sự kiện không có quy luật hoặc phát sinh");
        }
        if (repeatType != null && repeatType == 1) {
            Integer day = parseDayOfMonth(scheduleText);
            if (day == null) {
                errors.add("Lịch diễn ra của sự kiện hằng tháng có quy luật chỉ được nhập ngày trong tháng, ví dụ 16");
                return null;
            }
            return new TrafficEventScheduleRequest().setDayOfMonth(day);
        }
        if (repeatType != null && (repeatType == 2 || repeatType == 3)) {
            int[] dayMonth = parseDayAndMonth(scheduleText);
            if (dayMonth == null) {
                errors.add("Lịch diễn ra của sự kiện hằng năm/hằng quý có quy luật phải nhập ngày/tháng, ví dụ 02/09");
                return null;
            }
            return new TrafficEventScheduleRequest().setDayOfMonth(dayMonth[0]).setMonthOfYear(dayMonth[1]);
        }
        errors.add("Không xác định được kiểu lịch diễn ra");
        return null;
    }

    private TrafficEventScheduleRequest parseScheduleForExistingEvent(String scheduleText, TrafficEvent event,
            List<String> errors) {
        Integer eventType = event.getEventType();
        Integer repeatType = event.getRepeatType();
        Integer patternType = event.getPatternType();
        return parseScheduleByTypes(scheduleText, eventType, repeatType, patternType, errors);
    }

    private TrafficEventScheduleRequest parseExactDateSchedule(String scheduleText, List<String> errors,
            String errorMessage) {
        String value = trimToNull(scheduleText);
        if (value == null) {
            errors.add("Lịch diễn ra không được để trống");
            return null;
        }
        if (!value.matches("\\d{1,2}/\\d{1,2}/\\d{4}")) {
            errors.add(errorMessage);
            return null;
        }
        try {
            LocalDate exactDate = LocalDate.parse(value, DATE_FORMATTER);
            return new TrafficEventScheduleRequest().setExactDate(exactDate);
        } catch (DateTimeParseException e) {
            errors.add(errorMessage);
            return null;
        }
    }

    private Integer parseDayOfMonth(String text) {
        String value = trimToNull(text);
        if (value == null || !value.matches("\\d{1,2}")) {
            return null;
        }
        int day = Integer.parseInt(value);
        if (day < 1 || day > 31) {
            return null;
        }
        return day;
    }

    private int[] parseDayAndMonth(String text) {
        String value = trimToNull(text);
        if (value == null || !value.matches("\\d{1,2}/\\d{1,2}")) {
            return null;
        }
        String[] parts = value.split("/");
        int day = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        if (day < 1 || day > 31 || month < 1 || month > 12) {
            return null;
        }
        return new int[] { day, month };
    }

    private List<TrafficEventChannelRequest> parsePeriodicChannels(String channelText,
            Map<String, TrafficChannel> channelByName, List<String> errors) {
        List<String> channelNames = splitSemicolonText(channelText);
        List<TrafficEventChannelRequest> result = new ArrayList<>();
        if (channelNames.isEmpty()) {
            errors.add("Kênh ảnh hưởng không được để trống");
            return result;
        }
        Set<String> existingChannelNames = new HashSet<>();
        for (String channelName : channelNames) {
            String channelNameKey = normalizeText(channelName);
            if (!existingChannelNames.add(channelNameKey)) {
                errors.add("Kênh ảnh hưởng bị trùng trong cùng một dòng: " + channelName);
                continue;
            }

            TrafficChannel channel = channelByName.get(channelNameKey);
            if (channel == null) {
                errors.add("Kênh không tồn tại hoặc không hoạt động: " + channelName);
            } else {
                result.add(new TrafficEventChannelRequest().setChannelId(channel.getId()));
            }
        }
        return result;
    }

    private List<TrafficEventChannelRequest> parseIncidentChannels(String channelText, String impactRateText,
            Map<String, TrafficChannel> channelByName, List<String> errors) {
        List<String> channelNames = splitSemicolonText(channelText);
        List<String> impactRateTexts = splitSemicolonText(impactRateText);
        List<TrafficEventChannelRequest> result = new ArrayList<>();
        if (channelNames.isEmpty()) {
            errors.add("Kênh ảnh hưởng không được để trống");
            return result;
        }
        if (impactRateTexts.size() != channelNames.size()) {
            errors.add("Số lượng % dự báo tăng/giảm lưu lượng phải bằng số lượng kênh ảnh hưởng");
        }
        Set<String> existingChannelNames = new HashSet<>();
        for (int i = 0; i < channelNames.size(); i++) {
            String channelName = channelNames.get(i);
            String channelNameKey = normalizeText(channelName);
            BigDecimal impactRate = null;
            if (i < impactRateTexts.size()) {
                impactRate = parseImpactRate(impactRateTexts.get(i), errors);
            }
            if (!existingChannelNames.add(channelNameKey)) {
                errors.add("Kênh ảnh hưởng bị trùng trong cùng một dòng: " + channelName);
                continue;
            }

            TrafficChannel channel = channelByName.get(channelNameKey);
            if (channel == null) {
                errors.add("Kênh không tồn tại hoặc không hoạt động: " + channelName);
            } else {
                result.add(new TrafficEventChannelRequest().setChannelId(channel.getId()).setImpactRate(impactRate));
            }
        }
        return result;
    }

    private BigDecimal parseImpactRate(String text, List<String> errors) {
        String value = trimToNull(text);
        if (value == null) {
            errors.add("% dự báo tăng/giảm lưu lượng không được để trống");
            return null;
        }
        value = value.replace("%", "").replace("+", "").trim().replace(",", ".");
        try {
            BigDecimal impactRate = new BigDecimal(value);
            if (impactRate.abs().compareTo(MAX_IMPACT_RATE) > 0) {
                errors.add("% dự báo tăng/giảm lưu lượng phải nằm trong khoảng -100 đến 100: " + text);
                return null;
            }
            return impactRate;
        } catch (NumberFormatException e) {
            errors.add("% dự báo tăng/giảm lưu lượng không hợp lệ: " + text);
            return null;
        }
    }

    private Integer parseRepeatType(String text) {
        String value = normalizeText(text);
        if (value.equals("hang thang") || value.equals("hằng tháng") || value.equals("hàng tháng")) {
            return 1;
        }
        if (value.equals("hang nam") || value.equals("hằng năm") || value.equals("hàng năm")) {
            return 2;
        }
        if (value.equals("hang quy") || value.equals("hằng quý") || value.equals("hàng quý")) {
            return 3;
        }
        return null;
    }

    private Integer parsePatternType(String text) {
        String value = normalizeText(text);
        if (value.equals("co quy luat") || value.equals("có quy luật")) {
            return 1;
        }
        if (value.equals("khong co quy luat") || value.equals("không có quy luật")) {
            return 2;
        }
        return null;
    }

    private TrafficEvent saveImportedEvent(TrafficEventImportRow row, String importedBy) {
        Map<Integer, TrafficChannel> channelMap = new HashMap<>();
        validateRequest(row.getRequest(), channelMap, null);

        TrafficEvent event = new TrafficEvent()
                .setName(row.getRequest().getName().trim())
                .setEventType(row.getRequest().getEventType())
                .setRepeatType(row.getRequest().getRepeatType())
                .setPatternType(row.getRequest().getPatternType())
                .setStatus(row.getRequest().getStatus())
                .setCreatedBy(importedBy)
                .setUpdatedBy(importedBy);
        eventRepository.save(event);
        scheduleRepository.saveAll(buildScheduleEntities(row.getRequest(), event, importedBy));
        channelRepository.saveAll(buildChannelEntities(row.getRequest(), event, channelMap, importedBy));
        return event;
    }

    private void saveImportedSchedule(TrafficEventImportRow row, String importedBy) {
        if (row.getExistingEvent() == null || row.getScheduleRequest() == null) {
            return;
        }
        List<TrafficEventSchedule> existingSchedules = scheduleRepository.findByEventId(row.getExistingEvent().getId());
        boolean useExactDate = row.getExistingEvent().getEventType() == 2
                || (row.getExistingEvent().getPatternType() != null && row.getExistingEvent().getPatternType() == 2);
        if (useExactDate) {
            LocalDate exactDate = row.getScheduleRequest().getExactDate();
            for (TrafficEventSchedule schedule : existingSchedules) {
                if (Objects.equals(schedule.getExactDate(), exactDate)) {
                    return;
                }
            }
            TrafficEventSchedule newSchedule = new TrafficEventSchedule()
                    .setEvent(row.getExistingEvent())
                    .setExactDate(exactDate)
                    .setCreatedBy(importedBy)
                    .setUpdatedBy(importedBy);
            scheduleRepository.save(newSchedule);
            return;
        }

        if (row.getExistingEvent().getRepeatType() != null && row.getExistingEvent().getRepeatType() == 1) {
            syncMonthlyRuleSchedule(row.getExistingEvent(), existingSchedules, row.getScheduleRequest(), importedBy);
        } else {
            syncYearlyRuleSchedule(row.getExistingEvent(), existingSchedules, row.getScheduleRequest(), importedBy);
        }
    }

    private byte[] buildImportErrorFile(Workbook workbook, List<TrafficEventImportRow> rows) throws Exception {
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        CellStyle errorStyle = workbook.createCellStyle();
        errorStyle.setWrapText(true);
        Font errorFont = workbook.createFont();
        errorFont.setColor(IndexedColors.RED.getIndex());
        errorStyle.setFont(errorFont);

        addResultHeader(workbook.getSheet(PERIODIC_SHEET_NAME), PERIODIC_COL_RESULT, headerStyle);
        addResultHeader(workbook.getSheet(INCIDENT_SHEET_NAME), INCIDENT_COL_RESULT, headerStyle);

        for (TrafficEventImportRow importRow : rows) {
            if (!importRow.hasErrors()) {
                continue;
            }
            Sheet sheet = importRow.getSheetEventType() == 1
                    ? workbook.getSheet(PERIODIC_SHEET_NAME)
                    : workbook.getSheet(INCIDENT_SHEET_NAME);
            Row row = sheet.getRow(importRow.getRowIndex());
            Cell cell = row.getCell(importRow.getResultColumnIndex());
            if (cell == null) {
                cell = row.createCell(importRow.getResultColumnIndex());
            }
            cell.setCellValue(importRow.buildResultMessage());
            cell.setCellStyle(errorStyle);
        }

        workbook.getSheet(PERIODIC_SHEET_NAME).autoSizeColumn(PERIODIC_COL_RESULT);
        workbook.getSheet(INCIDENT_SHEET_NAME).autoSizeColumn(INCIDENT_COL_RESULT);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        return outputStream.toByteArray();
    }

    private void addResultHeader(Sheet sheet, int resultColumnIndex, CellStyle headerStyle) {
        Row headerRow = sheet.getRow(HEADER_ROW_INDEX);
        Cell cell = headerRow.getCell(resultColumnIndex);
        if (cell == null) {
            cell = headerRow.createCell(resultColumnIndex);
        }
        cell.setCellValue(RESULT_HEADER);
        cell.setCellStyle(headerStyle);
    }

    private String readCellAsString(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue();
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                LocalDate date = cell.getLocalDateTimeCellValue().toLocalDate();
                return date.format(DateTimeFormatter.ofPattern("d/M/yyyy"));
            }
            double value = cell.getNumericCellValue();
            if (value == Math.floor(value)) {
                return String.valueOf((long) value);
            }
            return String.valueOf(value);
        }
        return cell.toString();
    }

    private String readImpactRateCellAsString(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && isPercentFormattedCell(cell)) {
            double percentValue = cell.getNumericCellValue() * 100;
            if (percentValue == Math.floor(percentValue)) {
                return String.valueOf((long) percentValue);
            }
            return BigDecimal.valueOf(percentValue).stripTrailingZeros().toPlainString();
        }
        return readCellAsString(cell);
    }

    private boolean isPercentFormattedCell(Cell cell) {
        String format = cell.getCellStyle().getDataFormatString();
        return format != null && format.contains("%");
    }

    private boolean isExcelRowEmpty(Row row) {
        if (row == null) {
            return true;
        }
        for (int index = row.getFirstCellNum(); index < row.getLastCellNum(); index++) {
            Cell cell = row.getCell(index);
            String value = readCellAsString(cell);
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private List<String> splitSemicolonText(String text) {
        List<String> result = new ArrayList<>();
        String value = trimToNull(text);
        if (value == null) {
            return result;
        }
        String[] parts = value.split(";");
        for (String part : parts) {
            String trimmed = trimToNull(part);
            if (trimmed != null) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().toLowerCase(Locale.ROOT);
    }


    // =========================================================================
    // VALIDATE — Dùng chung cho create và update
    // =========================================================================

    /**
     * Validate toàn bộ nghiệp vụ của request.
     *
     * @param request   dữ liệu cần validate
     * @param excludeId id cần loại trừ khi check tên trùng (null khi create)
     */
    private void validateRequest(TrafficEventRequest request, Map<Integer, TrafficChannel> channelMap,
            Integer excludeId) {
        // 1. Validate status
        validateStatus(request.getStatus());

        // 2. Validate eventType
        if (request.getEventType() == null
                || (request.getEventType() != 1 && request.getEventType() != 2)) {
            throw new IllegalArgumentException("event_type không hợp lệ, chỉ chấp nhận 1 (Định kỳ) hoặc 2 (Phát sinh)");
        }

        boolean isPeriodic = request.getEventType() == 1;
        boolean isIncident = request.getEventType() == 2;

        // 2. Validate repeat_type và pattern_type theo event_type
        if (isPeriodic) {
            // Sự kiện định kỳ bắt buộc có repeat_type
            if (request.getRepeatType() == null) {
                throw new IllegalArgumentException("Sự kiện định kỳ phải có repeat_type");
            }
            if (request.getRepeatType() != 1 && request.getRepeatType() != 2 && request.getRepeatType() != 3) {
                throw new IllegalArgumentException(
                        "repeat_type không hợp lệ, chỉ chấp nhận 1 (Hằng tháng), 2 (Hằng năm) hoặc 3 (Hằng quý)");
            }
            // Sự kiện định kỳ bắt buộc có pattern_type
            if (request.getPatternType() == null) {
                throw new IllegalArgumentException("Sự kiện định kỳ phải có pattern_type");
            }
            if (request.getPatternType() != 1 && request.getPatternType() != 2) {
                throw new IllegalArgumentException(
                        "pattern_type không hợp lệ, chỉ chấp nhận 1 (Có quy luật) hoặc 2 (Không có quy luật)");
            }
        }

        // 4. Validate schedules theo loại
        validateSchedules(request);

        // 5. Validate channels
        validateChannels(request.getChannels(), channelMap, isIncident);

        // 6. Kiểm tra tên trùng trong DB
        boolean nameExists;
        if (excludeId == null) {
            // Create: kiểm tra tên tồn tại ở bất kỳ bản ghi nào chưa xóa
            nameExists = eventRepository.findByNameAndDeletedAtIsNull(request.getName().trim()).isPresent();
        } else {
            // Update: loại trừ bản ghi đang sửa
            nameExists = eventRepository.findByNameAndDeletedAtIsNullAndIdNot(
                    request.getName().trim(), excludeId).isPresent();
        }
        if (nameExists) {
            throw new IllegalArgumentException("Tên sự kiện '" + request.getName() + "' đã tồn tại");
        }
    }

    private void validateStatus(Integer status) {
        if (status == null || (status != 1 && status != 0)) {
            throw new IllegalArgumentException("Trạng thái sự kiện không hợp lệ");
        }
    }

    /**
     * Validate danh sách schedules theo loại sự kiện.
     * Kiểm tra field bắt buộc và ràng buộc giá trị.
     */
    private void validateSchedules(TrafficEventRequest request) {
        Integer eventType = request.getEventType();
        Integer repeatType = request.getRepeatType();
        Integer patternType = request.getPatternType();

        // Xác định loại schedule dựa theo tổ hợp type
        boolean useExactDate = (eventType == 2) // Phát sinh
                || (eventType == 1 && patternType != null && patternType == 2); // Định kỳ không quy luật

        boolean useMonthlyRule = (eventType == 1)
                && (repeatType != null && repeatType == 1)
                && (patternType != null && patternType == 1); // Định kỳ có quy luật / Hằng tháng

        boolean useYearlyRule = (eventType == 1)
                && (repeatType != null && repeatType == 2)
                && (patternType != null && patternType == 1); // Định kỳ có quy luật / Hằng năm

        boolean useQuarterlyRule = (eventType == 1)
                && (repeatType != null && repeatType == 3)
                && (patternType != null && patternType == 1); // Định kỳ có quy luật / Hằng quý

        // Kiểm tra trùng lặp exactDate trong list (chỉ với loại dùng exact_date)
        if (useExactDate) {
            Set<java.time.LocalDate> seenDates = new HashSet<>();
            for (TrafficEventScheduleRequest s : request.getSchedules()) {
                if (s.getExactDate() == null) {
                    throw new IllegalArgumentException("exact_date không được để trống với loại sự kiện này");
                }
                if (!seenDates.add(s.getExactDate())) {
                    throw new IllegalArgumentException(
                            "Có ngày bị trùng lặp trong danh sách lịch: " + s.getExactDate());
                }
            }
        }

        if (useMonthlyRule) {
            if (request.getSchedules().size() != 1) {
                throw new IllegalArgumentException("Định kỳ có quy luật hằng tháng chỉ có đúng 1 lịch");
            }
            TrafficEventScheduleRequest s = request.getSchedules().get(0);
            if (s.getDayOfMonth() == null || s.getDayOfMonth() < 1 || s.getDayOfMonth() > 31) {
                throw new IllegalArgumentException("day_of_month phải từ 1 đến 31");
            }
        }

        if (useYearlyRule || useQuarterlyRule) {
            if (request.getSchedules().size() != 1) {
                throw new IllegalArgumentException("Định kỳ có quy luật hằng năm/hằng quý chỉ có đúng 1 lịch");
            }
            TrafficEventScheduleRequest s = request.getSchedules().get(0);
            if (s.getDayOfMonth() == null || s.getDayOfMonth() < 1 || s.getDayOfMonth() > 31) {
                throw new IllegalArgumentException("day_of_month phải từ 1 đến 31");
            }
            if (s.getMonthOfYear() == null || s.getMonthOfYear() < 1 || s.getMonthOfYear() > 12) {
                throw new IllegalArgumentException("month_of_year phải từ 1 đến 12");
            }
        }
    }

    /**
     * Validate danh sách channels.
     * Kiểm tra trùng lặp channel_id, channel tồn tại trong DB, impact_rate theo
     * loại.
     */
    private void validateChannels(List<TrafficEventChannelRequest> channelRequests,
            Map<Integer, TrafficChannel> channelMap, boolean isIncident) {
        // Kiểm tra trùng channel_id trong cùng request
        Set<Integer> seenChannelIds = new HashSet<>();
        for (TrafficEventChannelRequest c : channelRequests) {
            if (!seenChannelIds.add(c.getChannelId())) {
                throw new IllegalArgumentException("channel_id " + c.getChannelId() + " bị trùng lặp trong request");
            }
        }

        // Kiểm tra tất cả channel_id tồn tại trong DB — 1 query IN duy nhất
        List<Integer> channelIds = channelRequests.stream()
                .map(TrafficEventChannelRequest::getChannelId)
                .collect(Collectors.toList());
        List<TrafficChannel> existingChannels = trafficChannelRepository.findAllById(channelIds);

        for (TrafficChannel channel : existingChannels) {
            channelMap.put(channel.getId(), channel);
        }

        for (TrafficEventChannelRequest c : channelRequests) {
            if (!channelMap.containsKey(c.getChannelId())) {
                throw new IllegalArgumentException("Kênh không tồn tại: channel_id = " + c.getChannelId());
            }
            validateImpactRate(c, isIncident);
        }
    }

    private void validateImpactRate(TrafficEventChannelRequest request, boolean isIncident) {
        BigDecimal impactRate = request.getImpactRate();
        Integer channelId = request.getChannelId();

        if (isIncident && impactRate == null) {
            throw new IllegalArgumentException(
                    "impactRate bắt buộc với channel_id = " + channelId);
        }

        if (impactRate == null) {
            return;
        }

        if (impactRate.abs().compareTo(MAX_IMPACT_RATE) > 0) {
            throw new IllegalArgumentException(
                    "impactRate phải nằm trong khoảng -100 đến 100 với channel_id = " + channelId);
        }
    }

    // =========================================================================
    // DIFF-SYNC SCHEDULES
    // =========================================================================

    /**
     * So sánh schedules hiện tại trong DB với request, thực hiện
     * insert/update/delete
     * chỉ những bản ghi thực sự thay đổi.
     *
     * @return danh sách schedules sau khi sync
     */
    private List<TrafficEventSchedule> syncSchedules(
            TrafficEvent event,
            TrafficEventRequest request,
            String updatedBy) {

        List<TrafficEventSchedule> existing = scheduleRepository.findByEventId(event.getId());

        boolean useExactDate = (event.getEventType() == 2)
                || (event.getPatternType() != null && event.getPatternType() == 2);
        boolean useYearlyRule = (event.getRepeatType() != null && event.getRepeatType() == 2)
                && (event.getPatternType() != null && event.getPatternType() == 1);
        boolean useMonthlyRule = (event.getRepeatType() != null && event.getRepeatType() == 1)
                && (event.getPatternType() != null && event.getPatternType() == 1);
        boolean useQuarterlyRule = (event.getRepeatType() != null && event.getRepeatType() == 3)
                && (event.getPatternType() != null && event.getPatternType() == 1);

        if (useExactDate) {
            return syncExactDateSchedules(event, existing, request.getSchedules(), updatedBy);
        } else if (useYearlyRule || useQuarterlyRule) {
            return syncYearlyRuleSchedule(event, existing, request.getSchedules().get(0), updatedBy);
        } else {
            // useMonthlyRule
            return syncMonthlyRuleSchedule(event, existing, request.getSchedules().get(0), updatedBy);
        }
    }

    /**
     * Sync schedules theo exact_date (phát sinh hoặc định kỳ không quy luật).
     * So sánh tập hợp exact_date để add/remove.
     *
     * Lưu ý: loại lịch này chỉ được dùng exact_date.
     * Vì vậy phải xóa day_of_month và month_of_year cũ nếu bản ghi được giữ lại.
     */
    private List<TrafficEventSchedule> syncExactDateSchedules(
            TrafficEvent event,
            List<TrafficEventSchedule> existing,
            List<TrafficEventScheduleRequest> requestSchedules,
            String updatedBy) {

        // Map existing: exact_date -> schedule
        // Chỉ map những bản ghi đã là exact-date thật sự.
        // Bản ghi rule cũ thường có exact_date = null, nên không nên tái sử dụng theo key null.
        Map<java.time.LocalDate, TrafficEventSchedule> existingMap = new HashMap<>();
        for (TrafficEventSchedule schedule : existing) {
            if (schedule.getExactDate() != null) {
                existingMap.put(schedule.getExactDate(), schedule);
            }
        }

        // Tập exact_date trong request
        Set<java.time.LocalDate> requestDates = requestSchedules.stream()
                .map(TrafficEventScheduleRequest::getExactDate)
                .collect(Collectors.toSet());

        // toDelete: lịch cũ không còn trong request.
        // Với bản ghi rule cũ, exact_date = null nên chắc chắn bị xóa khi đổi sang không quy luật.
        List<TrafficEventSchedule> toDelete = existing.stream()
                .filter(schedule -> !requestDates.contains(schedule.getExactDate()))
                .collect(Collectors.toList());

        // Các bản ghi exact_date đã tồn tại thì giữ lại,
        // nhưng phải clear field rule cũ để tránh dữ liệu bị lai.
        List<TrafficEventSchedule> toUpdate = new ArrayList<>();
        for (TrafficEventScheduleRequest requestSchedule : requestSchedules) {
            TrafficEventSchedule existingSchedule = existingMap.get(requestSchedule.getExactDate());
            if (existingSchedule != null) {
                boolean changed = existingSchedule.getDayOfMonth() != null
                        || existingSchedule.getMonthOfYear() != null;
                if (changed) {
                    existingSchedule.setDayOfMonth(null)
                            .setMonthOfYear(null)
                            .setUpdatedBy(updatedBy);
                    toUpdate.add(existingSchedule);
                }
            }
        }

        // toInsert: exact_date trong request nhưng không có trong existing
        List<TrafficEventSchedule> toInsert = requestSchedules.stream()
                .filter(requestSchedule -> !existingMap.containsKey(requestSchedule.getExactDate()))
                .map(requestSchedule -> new TrafficEventSchedule()
                        .setEvent(event)
                        .setExactDate(requestSchedule.getExactDate())
                        .setDayOfMonth(null)
                        .setMonthOfYear(null)
                        .setCreatedBy(updatedBy)
                        .setUpdatedBy(updatedBy))
                .collect(Collectors.toList());

        scheduleRepository.deleteAll(toDelete);
        scheduleRepository.saveAll(toUpdate);
        scheduleRepository.saveAll(toInsert);

        // Kết quả: giữ lại existing còn lại + những cái vừa insert
        List<TrafficEventSchedule> result = new ArrayList<>();
        for (TrafficEventSchedule schedule : existing) {
            if (!toDelete.contains(schedule)) {
                result.add(schedule);
            }
        }
        result.addAll(toInsert);
        return result;
    }

    /**
     * Sync schedule có quy luật hằng năm hoặc hằng quý (chỉ có đúng 1 bản ghi).
     * Nếu day_of_month hoặc month_of_year thay đổi → UPDATE bản ghi đó.
     *
     * Lưu ý: loại lịch này chỉ được dùng day_of_month + month_of_year.
     * Vì vậy phải xóa exact_date cũ nếu đổi từ loại không quy luật/phát sinh sang có quy luật.
     */
    private List<TrafficEventSchedule> syncYearlyRuleSchedule(
            TrafficEvent event,
            List<TrafficEventSchedule> existing,
            TrafficEventScheduleRequest requestSchedule,
            String updatedBy) {

        if (existing.isEmpty()) {
            // Không có → insert mới
            TrafficEventSchedule newSchedule = new TrafficEventSchedule()
                    .setEvent(event)
                    .setExactDate(null)
                    .setDayOfMonth(requestSchedule.getDayOfMonth())
                    .setMonthOfYear(requestSchedule.getMonthOfYear())
                    .setCreatedBy(updatedBy)
                    .setUpdatedBy(updatedBy);
            scheduleRepository.save(newSchedule);
            return Collections.singletonList(newSchedule);
        }

        // Có rồi → dùng bản ghi đầu tiên làm rule chính, xóa các bản ghi dư.
        TrafficEventSchedule schedule = existing.get(0);
        boolean changed = schedule.getExactDate() != null
                || !Objects.equals(schedule.getDayOfMonth(), requestSchedule.getDayOfMonth())
                || !Objects.equals(schedule.getMonthOfYear(), requestSchedule.getMonthOfYear());

        if (changed) {
            schedule.setExactDate(null)
                    .setDayOfMonth(requestSchedule.getDayOfMonth())
                    .setMonthOfYear(requestSchedule.getMonthOfYear())
                    .setUpdatedBy(updatedBy);
            scheduleRepository.save(schedule);
        }

        if (existing.size() > 1) {
            scheduleRepository.deleteAll(existing.subList(1, existing.size()));
        }

        return Collections.singletonList(schedule);
    }

    /**
     * Sync schedule có quy luật hằng tháng (chỉ có đúng 1 bản ghi).
     * Nếu day_of_month thay đổi → UPDATE bản ghi đó.
     *
     * Lưu ý: loại lịch này chỉ được dùng day_of_month.
     * Vì vậy phải xóa exact_date và month_of_year cũ nếu đổi loại lịch.
     */
    private List<TrafficEventSchedule> syncMonthlyRuleSchedule(
            TrafficEvent event,
            List<TrafficEventSchedule> existing,
            TrafficEventScheduleRequest requestSchedule,
            String updatedBy) {

        if (existing.isEmpty()) {
            TrafficEventSchedule newSchedule = new TrafficEventSchedule()
                    .setEvent(event)
                    .setExactDate(null)
                    .setDayOfMonth(requestSchedule.getDayOfMonth())
                    .setMonthOfYear(null)
                    .setCreatedBy(updatedBy)
                    .setUpdatedBy(updatedBy);
            scheduleRepository.save(newSchedule);
            return Collections.singletonList(newSchedule);
        }

        TrafficEventSchedule schedule = existing.get(0);
        boolean changed = schedule.getExactDate() != null
                || !Objects.equals(schedule.getDayOfMonth(), requestSchedule.getDayOfMonth())
                || schedule.getMonthOfYear() != null;

        if (changed) {
            schedule.setExactDate(null)
                    .setDayOfMonth(requestSchedule.getDayOfMonth())
                    .setMonthOfYear(null)
                    .setUpdatedBy(updatedBy);
            scheduleRepository.save(schedule);
        }

        if (existing.size() > 1) {
            scheduleRepository.deleteAll(existing.subList(1, existing.size()));
        }

        return Collections.singletonList(schedule);
    }

    // =========================================================================
    // DIFF-SYNC CHANNELS
    // =========================================================================

    /**
     * So sánh channels hiện tại trong DB với request, thực hiện
     * insert/update/delete
     * chỉ những bản ghi thực sự thay đổi (so sánh theo channel_id và impact_rate).
     *
     * @return danh sách channels sau khi sync
     */
    private List<TrafficEventChannel> syncChannels(
            TrafficEvent event,
            TrafficEventRequest request,
            Map<Integer, TrafficChannel> channelMap,
            String updatedBy) {

        List<TrafficEventChannel> existing = channelRepository.findByEventId(event.getId());

        // Map existing: channelId -> TrafficEventChannel
        Map<Integer, TrafficEventChannel> existingMap = new HashMap<>();
        for (TrafficEventChannel c : existing) {
            existingMap.put(c.getChannel().getId(), c);
        }

        // Map request: channelId -> request item
        Map<Integer, TrafficEventChannelRequest> requestMap = new HashMap<>();
        for (TrafficEventChannelRequest r : request.getChannels()) {
            requestMap.put(r.getChannelId(), r);
        }

        // toDelete: channelId có trong existing nhưng không có trong request
        List<TrafficEventChannel> toDelete = existing.stream()
                .filter(c -> !requestMap.containsKey(c.getChannel().getId()))
                .collect(Collectors.toList());

        // toInsert: channelId trong request nhưng không có trong existing
        List<TrafficEventChannelRequest> toInsertRequests = request.getChannels().stream()
                .filter(r -> !existingMap.containsKey(r.getChannelId()))
                .collect(Collectors.toList());

        boolean isIncident = event.getEventType() != null && event.getEventType() == 2;

        // toUpdate: chỉ sự kiện phát sinh mới cập nhật khi impact_rate thay đổi
        List<TrafficEventChannel> toUpdate = new ArrayList<>();
        if (isIncident) {
            for (TrafficEventChannel existingChannel : existing) {
                int channelId = existingChannel.getChannel().getId();
                if (requestMap.containsKey(channelId)) {
                    BigDecimal newRate = requestMap.get(channelId).getImpactRate();
                    if (!Objects.equals(existingChannel.getImpactRate(), newRate)) {
                        existingChannel.setImpactRate(newRate)
                                .setUpdatedBy(updatedBy);
                        toUpdate.add(existingChannel);
                    }
                }
            }
        }

        // Build toInsert entities sử dụng channelMap đã được query từ lúc validate
        List<TrafficEventChannel> toInsert = toInsertRequests.stream()
                .map(r -> new TrafficEventChannel()
                        .setEvent(event)
                        .setChannel(channelMap.get(r.getChannelId()))
                        .setImpactRate(isIncident ? r.getImpactRate() : null)
                        .setCreatedBy(updatedBy)
                        .setUpdatedBy(updatedBy))
                .collect(Collectors.toList());

        // Thực hiện thay đổi
        channelRepository.deleteAll(toDelete);
        channelRepository.saveAll(toUpdate);
        channelRepository.saveAll(toInsert);

        // Kết quả tổng hợp
        List<TrafficEventChannel> result = new ArrayList<>();
        for (TrafficEventChannel c : existing) {
            if (!toDelete.contains(c)) {
                result.add(c);
            }
        }
        result.addAll(toInsert);
        return result;
    }

    // =========================================================================
    // HELPER — Build entities và Map response
    // =========================================================================

    /**
     * Tạo danh sách entity TrafficEventSchedule từ request, theo loại sự kiện.
     */
    private List<TrafficEventSchedule> buildScheduleEntities(
            TrafficEventRequest request,
            TrafficEvent event,
            String createdBy) {

        List<TrafficEventSchedule> result = new ArrayList<>();
        for (TrafficEventScheduleRequest r : request.getSchedules()) {
            TrafficEventSchedule s = new TrafficEventSchedule()
                    .setEvent(event)
                    .setExactDate(r.getExactDate())
                    .setDayOfMonth(r.getDayOfMonth())
                    .setMonthOfYear(r.getMonthOfYear())
                    .setCreatedBy(createdBy)
                    .setUpdatedBy(createdBy);
            result.add(s);
        }
        return result;
    }

    /**
     * Tạo danh sách entity TrafficEventChannel từ request.
     * Sử dụng channelMap đã được truy vấn từ bước validate.
     */
    private List<TrafficEventChannel> buildChannelEntities(
            TrafficEventRequest request,
            TrafficEvent event,
            Map<Integer, TrafficChannel> channelMap,
            String createdBy) {

        boolean isIncident = request.getEventType() != null && request.getEventType() == 2;

        List<TrafficEventChannel> result = new ArrayList<>();
        for (TrafficEventChannelRequest r : request.getChannels()) {
            TrafficEventChannel c = new TrafficEventChannel()
                    .setEvent(event)
                    .setChannel(channelMap.get(r.getChannelId()))
                    .setImpactRate(isIncident ? r.getImpactRate() : null)
                    .setCreatedBy(createdBy)
                    .setUpdatedBy(createdBy);
            result.add(c);
        }
        return result;
    }

    /**
     * Chuyển entity sang Response DTO chi tiết (dùng cho getById, create, update).
     */
    private TrafficEventResponse toDetailResponse(
            TrafficEvent event,
            List<TrafficEventSchedule> schedules,
            List<TrafficEventChannel> channels) {

        List<TrafficEventScheduleResponse> scheduleResponses = schedules.stream()
                .map(s -> new TrafficEventScheduleResponse()
                        .setId(s.getId())
                        .setExactDate(s.getExactDate())
                        .setDayOfMonth(s.getDayOfMonth())
                        .setMonthOfYear(s.getMonthOfYear()))
                .collect(Collectors.toList());

        List<TrafficEventChannelResponse> channelResponses = channels.stream()
                .map(c -> new TrafficEventChannelResponse()
                        .setId(c.getId())
                        .setChannelId(c.getChannel().getId())
                        .setChannelName(c.getChannel().getName())
                        .setImpactRate(c.getImpactRate()))
                .collect(Collectors.toList());

        return new TrafficEventResponse()
                .setId(event.getId())
                .setName(event.getName())
                .setEventType(event.getEventType())
                .setRepeatType(event.getRepeatType())
                .setPatternType(event.getPatternType())
                .setStatus(event.getStatus())
                .setSchedules(scheduleResponses)
                .setChannels(channelResponses)
                .setCreatedAt(event.getCreatedAt())
                .setCreatedBy(event.getCreatedBy())
                .setUpdatedAt(event.getUpdatedAt())
                .setUpdatedBy(event.getUpdatedBy());
    }

    /**
     * Chuyển entity sang Response DTO rút gọn (dùng cho danh sách phân trang).
     */
    private TrafficEventListResponse toListResponse(TrafficEvent event) {
        return new TrafficEventListResponse()
                .setId(event.getId())
                .setName(event.getName())
                .setEventType(event.getEventType())
                .setRepeatType(event.getRepeatType())
                .setPatternType(event.getPatternType())
                .setStatus(event.getStatus())
                .setCreatedAt(event.getCreatedAt())
                .setCreatedBy(event.getCreatedBy());
    }
}
