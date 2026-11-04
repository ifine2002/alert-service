package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.constant.KpiExcelConstants;
import com.techasians.alertservice.dto.FileTemplateResponse;
import com.techasians.alertservice.dto.TrafficChannelKpiImportResult;
import com.techasians.alertservice.dto.TrafficChannelKpiImportRow;
import com.techasians.alertservice.entity.TrafficChannelKpi;
import com.techasians.alertservice.exception.ImportValidationException;
import com.techasians.alertservice.repository.TrafficChannelKpiRepository;
import com.techasians.alertservice.repository.TrafficChannelRepository;
import com.techasians.alertservice.service.TrafficChannelKpiImportService;
import com.techasians.alertservice.dto.TrafficChannelTemplateProjection;
import com.techasians.alertservice.util.ExcelPoiHelper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TrafficChannelKpiImportServiceImpl implements TrafficChannelKpiImportService {

    @Autowired
    private TrafficChannelKpiRepository kpiRepository;

    @Autowired
    private TrafficChannelRepository channelRepository;

    @Override
    @Transactional
    public TrafficChannelKpiImportResult importFile(MultipartFile file) {
        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);

            List<TrafficChannelKpiImportRow> rows = new ArrayList<>();
            boolean hasError = false;

            // Row 0 is the note row, row 1 is the header row
            Row headerRow = sheet.getRow(1);
            if (headerRow == null) {
                throw new IllegalArgumentException("File import không đúng định dạng mẫu. Vui lòng tải file mẫu mới nhất.");
            }

            int expectedCols = KpiExcelConstants.TEMPLATE_HEADERS.length;
            for (int i = 0; i < expectedCols; i++) {
                Cell cell = headerRow.getCell(i);
                String headerName = ExcelPoiHelper.readStringCell(cell);
                if (headerName == null || !headerName.trim().equals(KpiExcelConstants.TEMPLATE_HEADERS[i])) {
                    throw new IllegalArgumentException("Sai tên cột '" + KpiExcelConstants.TEMPLATE_HEADERS[i] + "' ở vị trí " + (i + 1) + ". Vui lòng tải file mẫu mới nhất.");
                }
            }
            for (int i = expectedCols; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                String headerName = ExcelPoiHelper.readStringCell(cell);
                if (headerName != null && !headerName.trim().isEmpty()) {
                    throw new IllegalArgumentException("File import thừa cột. Vui lòng tải file mẫu mới nhất.");
                }
            }

            Set<Integer> activeChannelIds = channelRepository.findActiveChannelsForTemplate(1)
                    .stream().map(TrafficChannelTemplateProjection::getId).collect(Collectors.toSet());

            // Start reading data from row 2 because row 0 is note and row 1 is header
            for (int i = 2; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (ExcelPoiHelper.isRowEmpty(row)) {
                    continue;
                }

                TrafficChannelKpiImportRow importRow = new TrafficChannelKpiImportRow();
                importRow.setRowIndex(i);

                Cell idCell = row.getCell(KpiExcelConstants.COL_INDEX_CHANNEL_ID);
                Integer channelId = ExcelPoiHelper.readIntegerCell(idCell);
                importRow.setChannelId(channelId);

                Cell kpiCell = row.getCell(KpiExcelConstants.COL_INDEX_KPI);
                Cell prodCell = row.getCell(KpiExcelConstants.COL_INDEX_PRODUCTIVITY);

                String kpiStr = ExcelPoiHelper.readStringCell(kpiCell);
                String prodStr = ExcelPoiHelper.readStringCell(prodCell);

                boolean kpiEmpty = (kpiStr == null || kpiStr.trim().isEmpty());
                boolean prodEmpty = (prodStr == null || prodStr.trim().isEmpty());

                if (channelId == null) {
                    importRow.setSkip(true);
                    rows.add(importRow);
                    continue;
                }

                if (kpiEmpty && prodEmpty) {
                    importRow.setSkip(true);
                    rows.add(importRow);
                    continue;
                }

                if (!activeChannelIds.contains(channelId)) {
                    importRow.setSkip(true);
                    rows.add(importRow);
                    continue;
                }

                if (kpiEmpty || prodEmpty) {
                    importRow.addError(KpiExcelConstants.ERROR_KPI_OR_PRODUCTIVITY_MISSING);
                    hasError = true;
                }

                BigDecimal kpiVal = null;
                BigDecimal prodVal = null;

                if (!kpiEmpty) {
                    kpiVal = ExcelPoiHelper.readDecimalCell(kpiCell);
                    if (kpiVal == null) {
                        importRow.addError("KPI: " + KpiExcelConstants.ERROR_INVALID_NUMBER_FORMAT);
                        hasError = true;
                    } else if (kpiVal.compareTo(BigDecimal.ZERO) < 0 || kpiVal.compareTo(new BigDecimal("100")) > 0) {
                        importRow.addError(KpiExcelConstants.ERROR_KPI_OUT_OF_RANGE);
                        hasError = true;
                    }
                }

                if (!prodEmpty) {
                    prodVal = ExcelPoiHelper.readDecimalCell(prodCell);
                    if (prodVal == null) {
                        importRow.addError("Năng suất: " + KpiExcelConstants.ERROR_INVALID_NUMBER_FORMAT);
                        hasError = true;
                    } else if (prodVal.compareTo(BigDecimal.ZERO) < 0) {
                        importRow.addError(KpiExcelConstants.ERROR_PRODUCTIVITY_NEGATIVE);
                        hasError = true;
                    }
                }

                if (kpiVal != null && prodVal != null) {
                    importRow.setKpiRate(ExcelPoiHelper.roundToTwoDecimalPlaces(kpiVal));
                    importRow.setProductivityValue(ExcelPoiHelper.roundToTwoDecimalPlaces(prodVal));
                }
                
                rows.add(importRow);
            }

            // Check duplicate channel Ids in file
            Set<Integer> seenIds = new HashSet<>();
            for (TrafficChannelKpiImportRow r : rows) {
                if (!r.isSkip() && !r.hasErrors()) {
                    if (!seenIds.add(r.getChannelId())) {
                        r.addError(KpiExcelConstants.ERROR_DUPLICATE_CHANNEL);
                        hasError = true;
                    }
                }
            }

            List<Integer> channelIds = rows.stream()
                    .filter(r -> !r.isSkip() && r.getChannelId() != null)
                    .map(TrafficChannelKpiImportRow::getChannelId)
                    .collect(Collectors.toList());

            // Need to check if channel is locked or not found? Wait, user rule: "Bỏ qua kênh bị khóa, bỏ qua kênh không tồn tại"
            // To do this, we could query active channels. But we already queries all active channels when exporting.
            // If we just upsert, we don't strictly need to check if they are locked unless we query the `traffic_channel` table.
            // Let's assume if it's not active, we skip. However, to keep it simple, we just upsert whatever comes in unless we query `TrafficChannel`.
            // Wait, the prompt says: "Nếu ID kênh trong file thuộc về một kênh không còn hoạt động hoặc không tồn tại -> Bỏ qua bản ghi đó, không báo lỗi."
            // So we need to filter by active channels.
            
            if (hasError) {
                byte[] errorFileBytes = buildErrorFile(workbook, rows);
                TrafficChannelKpiImportResult result = new TrafficChannelKpiImportResult();
                result.setErrorFile(errorFileBytes);
                return result;
            }

            if (channelIds.isEmpty()) {
                return new TrafficChannelKpiImportResult();
            }

            List<TrafficChannelKpi> existingKpis = kpiRepository.findByChannelIdInAndDeletedAtIsNull(channelIds);
            Map<Integer, TrafficChannelKpi> existingMap = existingKpis.stream()
                    .collect(Collectors.toMap(TrafficChannelKpi::getChannelId, kpi -> kpi, (k1, k2) -> k1));

            List<TrafficChannelKpi> toSave = new ArrayList<>();

            for (TrafficChannelKpiImportRow row : rows) {
                if (row.isSkip()) {
                    continue;
                }
                
                TrafficChannelKpi entity = existingMap.get(row.getChannelId());
                if (entity == null) {
                    entity = new TrafficChannelKpi();
                    entity.setChannelId(row.getChannelId());
                }
                entity.setKpiRate(row.getKpiRate());
                entity.setProductivityValue(row.getProductivityValue());
                toSave.add(entity);
            }

            if (!toSave.isEmpty()) {
                kpiRepository.saveAll(toSave);
            }

            return new TrafficChannelKpiImportResult();

        } catch (Exception e) {
            throw new RuntimeException("Lỗi xử lý file import: " + e.getMessage(), e);
        }
    }

    private byte[] buildErrorFile(Workbook workbook, List<TrafficChannelKpiImportRow> rows) throws Exception {
        Sheet sheet = workbook.getSheetAt(0);
        
        // Add Result Header
        Row header = sheet.getRow(0);
        if (header == null) header = sheet.createRow(0);
        Cell resultHeader = header.getCell(KpiExcelConstants.COL_INDEX_RESULT);
        if (resultHeader == null) resultHeader = header.createCell(KpiExcelConstants.COL_INDEX_RESULT);
        resultHeader.setCellValue(KpiExcelConstants.RESULT_HEADER_NAME);

        CellStyle errorStyle = workbook.createCellStyle();
        errorStyle.setWrapText(true);
        Font errorFont = workbook.createFont();
        errorFont.setColor(IndexedColors.RED.getIndex());
        errorStyle.setFont(errorFont);

        for (TrafficChannelKpiImportRow r : rows) {
            if (r.hasErrors()) {
                Row row = sheet.getRow(r.getRowIndex());
                Cell errorCell = row.getCell(KpiExcelConstants.COL_INDEX_RESULT);
                if (errorCell == null) errorCell = row.createCell(KpiExcelConstants.COL_INDEX_RESULT);
                
                String combinedError = r.getErrorMessages().stream()
                        .map(msg -> "- " + msg)
                        .collect(Collectors.joining("\n"));
                
                errorCell.setCellValue(combinedError);
                errorCell.setCellStyle(errorStyle);
            }
        }

        sheet.autoSizeColumn(KpiExcelConstants.COL_INDEX_RESULT);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        return outputStream.toByteArray();
    }
}
