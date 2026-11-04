package com.techasians.alertservice.dto;

import java.time.LocalDateTime;

/**
 * Response DTO rút gọn cho danh sách phân trang.
 * Không bao gồm schedules để tránh N+1 query.
 */
public class TrafficEventListResponse {

    private Integer id;
    private String name;
    private Integer eventType;
    private Integer repeatType;
    private Integer patternType;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;

    public Integer getId() {
        return id;
    }

    public TrafficEventListResponse setId(Integer id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public TrafficEventListResponse setName(String name) {
        this.name = name;
        return this;
    }

    public Integer getEventType() {
        return eventType;
    }

    public TrafficEventListResponse setEventType(Integer eventType) {
        this.eventType = eventType;
        return this;
    }

    public Integer getRepeatType() {
        return repeatType;
    }

    public TrafficEventListResponse setRepeatType(Integer repeatType) {
        this.repeatType = repeatType;
        return this;
    }

    public Integer getPatternType() {
        return patternType;
    }

    public TrafficEventListResponse setPatternType(Integer patternType) {
        this.patternType = patternType;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public TrafficEventListResponse setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public TrafficEventListResponse setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public TrafficEventListResponse setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }
}
