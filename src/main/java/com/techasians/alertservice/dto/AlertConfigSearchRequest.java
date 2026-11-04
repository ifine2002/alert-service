package com.techasians.alertservice.dto;

/**
 * DTO nhận các tham số tìm kiếm và phân trang từ Frontend.
 * Dùng cho API GET danh sách Cấu hình cảnh báo.
 */
public class AlertConfigSearchRequest {

    // Lọc theo ID loại chỉ số (category). Không bắt buộc.
    private Integer categoryId;

    // Tìm kiếm theo tên chỉ số (tìm kiếm gần đúng). Không bắt buộc.
    private String metricName;

    // Tìm kiếm theo người tạo. Không bắt buộc.
    private String createdBy;

    // Số trang hiện tại (bắt đầu từ 0). Mặc định là trang 0.
    private int page = 0;

    // Số bản ghi trên mỗi trang. Mặc định là 10.
    private int size = 10;

    private String sort;

    public Integer getCategoryId() {
        return categoryId;
    }

    public AlertConfigSearchRequest setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
        return this;
    }

    public String getMetricName() {
        return metricName;
    }

    public AlertConfigSearchRequest setMetricName(String metricName) {
        this.metricName = metricName;
        return this;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public AlertConfigSearchRequest setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    public int getPage() {
        return page;
    }

    public AlertConfigSearchRequest setPage(int page) {
        this.page = page;
        return this;
    }

    public int getSize() {
        return size;
    }

    public AlertConfigSearchRequest setSize(int size) {
        this.size = size;
        return this;
    }

    public String getSort() {
        return sort;
    }

    public AlertConfigSearchRequest setSort(String sort) {
        this.sort = sort;
        return this;
    }
}
