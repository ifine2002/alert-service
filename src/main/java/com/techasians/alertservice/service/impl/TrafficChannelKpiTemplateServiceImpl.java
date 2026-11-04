package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.constant.KpiExcelConstants;
import com.techasians.alertservice.dto.FileTemplateResponse;
import com.techasians.alertservice.dto.TrafficChannelTemplateProjection;
import com.techasians.alertservice.repository.TrafficChannelRepository;
import com.techasians.alertservice.service.TrafficChannelKpiTemplateService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;

@Service
public class TrafficChannelKpiTemplateServiceImpl implements TrafficChannelKpiTemplateService {

    @Autowired
    private TrafficChannelRepository channelRepository;

    @Override
    public FileTemplateResponse buildImportTemplate() {
        List<TrafficChannelTemplateProjection> activeChannels = channelRepository.findActiveChannelsForTemplate(1);

        try (Workbook workbook = getTemplateWorkbook()) {
            Sheet sheet = workbook.getSheetAt(0);

            // Hide the channel ID column
            sheet.setColumnHidden(KpiExcelConstants.COL_INDEX_CHANNEL_ID, true);

            int rowIndex = 2; // Start after note row and header row
            for (TrafficChannelTemplateProjection channel : activeChannels) {
                Row row = sheet.createRow(rowIndex++);
                
                Cell nameCell = row.createCell(KpiExcelConstants.COL_INDEX_CHANNEL_NAME);
                nameCell.setCellValue(channel.getName());

                Cell idCell = row.createCell(KpiExcelConstants.COL_INDEX_CHANNEL_ID);
                idCell.setCellValue(channel.getId());
            }

            // Auto-size the first column
            sheet.autoSizeColumn(KpiExcelConstants.COL_INDEX_CHANNEL_NAME);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);

            String base64Data = Base64.getEncoder().encodeToString(outputStream.toByteArray());

            return new FileTemplateResponse("Template_Import_KPI.xlsx", 
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 
                    base64Data);

        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi tạo template tải xuống", e);
        }
    }

    private Workbook getTemplateWorkbook() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("KPI");
        Row noteRow = sheet.createRow(0);
        noteRow.setHeightInPoints(60);
        Cell noteCell = noteRow.createCell(0);
        noteCell.setCellValue("Lưu ý:\n"
                + "Giá trị KPI, Năng suất phải là số dương, ngăn cách số thập phân bằng dấu \".\"\n"
                + "Giá trị KPI phải nhỏ hơn hoặc bằng 100%");

        CellStyle noteStyle = workbook.createCellStyle();
        noteStyle.setWrapText(true);
        noteStyle.setAlignment(HorizontalAlignment.LEFT);
        noteStyle.setVerticalAlignment(VerticalAlignment.BOTTOM);

        Font noteFont = workbook.createFont();
        noteFont.setColor(IndexedColors.RED.getIndex());
        noteFont.setFontName("Times New Roman");
        noteFont.setFontHeightInPoints((short) 11);
        noteFont.setBold(true);
        noteStyle.setFont(noteFont);
        noteCell.setCellStyle(noteStyle);

        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, KpiExcelConstants.TEMPLATE_HEADERS.length - 1));

        Row header = sheet.createRow(1);
        
        XSSFCellStyle headerStyle = (XSSFCellStyle) workbook.createCellStyle();
        headerStyle.setFillForegroundColor(new XSSFColor(new byte[] {(byte) 255, (byte) 230, (byte) 153}, null));
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontName("Times New Roman");
        headerFont.setFontHeightInPoints((short) 11);
        headerStyle.setFont(headerFont);
        
        for (int i = 0; i < KpiExcelConstants.TEMPLATE_HEADERS.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(KpiExcelConstants.TEMPLATE_HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }
        return workbook;
    }
}
