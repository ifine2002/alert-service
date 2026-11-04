package com.techasians.alertservice.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả trả về sau khi import file Excel.
 *
 * Nếu hasDuplicates = true thì errorFile chứa nội dung file Excel
 * đã được ghi chú lỗi ở cột "Kết quả" - controller sẽ trả về file này
 * thay vì JSON thông thường.
 */
public class ImportResultDTO {

    // Tổng số dòng dữ liệu đã đọc từ file
    private int totalRows;

    // Số dòng import thành công
    private int successRows;

    // Số dòng bị bỏ qua (do không map được khu vực/kênh)
    private int skippedRows;

    // Thông điệp tổng kết
    private String message;

    // Có bản ghi trùng trong file không?
    private boolean hasDuplicates;

    // Nội dung file Excel báo lỗi (chỉ có giá trị khi hasDuplicates = true)
    private byte[] errorFile;

    // Danh sách sự kiện được tự động tạo mới trong quá trình import
    // Rỗng nếu không có event nào được auto-create
    private List<CreatedEventInfo> createdEvents = new ArrayList<>();

    public ImportResultDTO(int totalRows, int successRows, int skippedRows) {
        this(totalRows, successRows, skippedRows, new ArrayList<>());
    }

    /**
     * Constructor thành công kèm danh sách sự kiện được tự động tạo mới.
     */
    public ImportResultDTO(int totalRows, int successRows, int skippedRows,
                           List<CreatedEventInfo> createdEvents) {
        this.totalRows = totalRows;
        this.successRows = successRows;
        this.skippedRows = skippedRows;
        this.hasDuplicates = false;
        this.errorFile = null;
        this.createdEvents = (createdEvents != null) ? createdEvents : new ArrayList<>();
        this.message = String.format("Import hoàn tất: %d/%d dòng thành công, %d dòng bỏ qua.",
                successRows, totalRows, skippedRows);
        if (!this.createdEvents.isEmpty()) {
            this.message += String.format(" Đã tự động tạo %d sự kiện mới.",
                    this.createdEvents.size());
        }
    }

    // Constructor dùng khi có duplicate - không import, chỉ trả file lỗi
    public ImportResultDTO(byte[] errorFile) {
        this.totalRows = 0;
        this.successRows = 0;
        this.skippedRows = 0;
        this.hasDuplicates = true;
        this.errorFile = errorFile;
        this.message = "Import thất bại: File chứa bản ghi trùng lặp. Vui lòng tải file lỗi và kiểm tra cột 'Kết quả'.";
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getSuccessRows() {
        return successRows;
    }

    public int getSkippedRows() {
        return skippedRows;
    }

    public String getMessage() {
        return message;
    }

    public boolean isHasDuplicates() {
        return hasDuplicates;
    }

    public byte[] getErrorFile() {
        return errorFile;
    }

    public List<CreatedEventInfo> getCreatedEvents() {
        return createdEvents;
    }
}
