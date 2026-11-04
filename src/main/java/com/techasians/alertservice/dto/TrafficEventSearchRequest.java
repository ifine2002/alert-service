package com.techasians.alertservice.dto;

/**
 * Request DTO cho tìm kiếm phân trang Traffic Event.
 * Tất cả filter đều là tùy chọn.
 */
public class TrafficEventSearchRequest {

    // Tìm kiếm theo tên (LIKE, không phân biệt hoa thường)
    private String name;

    // 1-Định kỳ, 2-Phát sinh
    private Integer eventType;

    // 0-Ngừng áp dụng, 1-Đang áp dụng
    private Integer status;

    private int page = 0;
    private int size = 20;

    public String getName() {
        return name;
    }

    public TrafficEventSearchRequest setName(String name) {
        this.name = name;
        return this;
    }

    public Integer getEventType() {
        return eventType;
    }

    public TrafficEventSearchRequest setEventType(Integer eventType) {
        this.eventType = eventType;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public TrafficEventSearchRequest setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public int getPage() {
        return page;
    }

    public TrafficEventSearchRequest setPage(int page) {
        this.page = page;
        return this;
    }

    public int getSize() {
        return size;
    }

    public TrafficEventSearchRequest setSize(int size) {
        this.size = size;
        return this;
    }
}
