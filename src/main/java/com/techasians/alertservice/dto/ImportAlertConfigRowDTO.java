package com.techasians.alertservice.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO nội bộ — lưu dữ liệu thô của từng dòng sau khi đọc file Excel import.
 *
 * Ví dụ 1 dòng Excel:
 *   Chỉ số vận hành CC | CCR | Mức 1 | < | 95 |   | Y | Y | VTT; VTT_SME
 *
 * Sau khi parse, sẽ được lưu vào đối tượng này để validate và lưu DB.
 */
public class ImportAlertConfigRowDTO {

    // Số thứ tự dòng trong file Excel (bắt đầu từ 2, vì dòng 1 là header)
    // Dùng để hiển thị thông báo lỗi: "Dòng 3: ..."
    private int rowNumber;

    // Cột A: Tên loại chỉ số (ServiceQualityCategory.name)
    private String categoryName;

    // Cột B: Tên chỉ số (ServiceQualityMetric.name)
    private String metricName;

    // Cột C: Mức độ cảnh báo — đọc từ "Mức 1", "Mức 2", "Mức 3"
    // Sau khi parse: 1, 2, hoặc 3
    private Integer levelNumber;

    // Cột D: Phép so sánh — đọc từ "<", ">", ">=", "<=", "between"
    // Sau khi parse theo DB schema: 1=<, 2=>, 3=>=, 4=<=, 5=between
    private Integer conditionOperator;

    // Cột E: Giá trị ngưỡng V1/Min — bắt buộc với mọi phép so sánh
    private BigDecimal thresholdValue;

    // Cột F: Giá trị ngưỡng V2/Max — chỉ bắt buộc với phép "between"
    private BigDecimal thresholdValueTo;

    // Lưu chuỗi gốc để validate định dạng số thập phân
    private String v1Raw;
    
    private String v2Raw;

    // Cột G: Nhận SMS — đọc từ "Y" hoặc "N"
    private Boolean receiveSms;

    // Cột H: Nhận Email — đọc từ "Y" hoặc "N"
    private Boolean receiveEmail;

    // Cột I: Danh sách mã đầu mối nhận (AlertGroup.code), phân cách bằng ";"
    // Ví dụ: ["VTT", "VTT_SME"]
    private List<String> groupCodes;

    // Kết quả validate dòng này: null = chưa validate, "" = hợp lệ, "lỗi..." = có lỗi
    private String errorMessage;

    // Key đã resolve được sau bước validate: categoryId_metricName (dùng để tra metricsByKey trong saveImportGroup)
    // Khi người dùng nhập tên cha, key này sử dụng id category con để tìm đúng metric
    private String resolvedMetricKey;

    // ====================== Getters và Setters ======================

    public int getRowNumber() {
        return rowNumber;
    }

    public ImportAlertConfigRowDTO setRowNumber(int rowNumber) {
        this.rowNumber = rowNumber;
        return this;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public ImportAlertConfigRowDTO setCategoryName(String categoryName) {
        this.categoryName = categoryName;
        return this;
    }

    public String getMetricName() {
        return metricName;
    }

    public ImportAlertConfigRowDTO setMetricName(String metricName) {
        this.metricName = metricName;
        return this;
    }

    public Integer getLevelNumber() {
        return levelNumber;
    }

    public ImportAlertConfigRowDTO setLevelNumber(Integer levelNumber) {
        this.levelNumber = levelNumber;
        return this;
    }

    public Integer getConditionOperator() {
        return conditionOperator;
    }

    public ImportAlertConfigRowDTO setConditionOperator(Integer conditionOperator) {
        this.conditionOperator = conditionOperator;
        return this;
    }

    public BigDecimal getThresholdValue() {
        return thresholdValue;
    }

    public ImportAlertConfigRowDTO setThresholdValue(BigDecimal thresholdValue) {
        this.thresholdValue = thresholdValue;
        return this;
    }

    public BigDecimal getThresholdValueTo() {
        return thresholdValueTo;
    }

    public ImportAlertConfigRowDTO setThresholdValueTo(BigDecimal thresholdValueTo) {
        this.thresholdValueTo = thresholdValueTo;
        return this;
    }

    public String getV1Raw() {
        return v1Raw;
    }

    public ImportAlertConfigRowDTO setV1Raw(String v1Raw) {
        this.v1Raw = v1Raw;
        return this;
    }

    public String getV2Raw() {
        return v2Raw;
    }

    public ImportAlertConfigRowDTO setV2Raw(String v2Raw) {
        this.v2Raw = v2Raw;
        return this;
    }

    public Boolean getReceiveSms() {
        return receiveSms;
    }

    public ImportAlertConfigRowDTO setReceiveSms(Boolean receiveSms) {
        this.receiveSms = receiveSms;
        return this;
    }

    public Boolean getReceiveEmail() {
        return receiveEmail;
    }

    public ImportAlertConfigRowDTO setReceiveEmail(Boolean receiveEmail) {
        this.receiveEmail = receiveEmail;
        return this;
    }

    public List<String> getGroupCodes() {
        return groupCodes;
    }

    public ImportAlertConfigRowDTO setGroupCodes(List<String> groupCodes) {
        this.groupCodes = groupCodes;
        return this;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public ImportAlertConfigRowDTO setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    public String getResolvedMetricKey() {
        return resolvedMetricKey;
    }

    public ImportAlertConfigRowDTO setResolvedMetricKey(String resolvedMetricKey) {
        this.resolvedMetricKey = resolvedMetricKey;
        return this;
    }

    // Kiểm tra nhanh: dòng này có lỗi không?
    public boolean hasError() {
        return errorMessage != null && !errorMessage.isEmpty();
    }
}
