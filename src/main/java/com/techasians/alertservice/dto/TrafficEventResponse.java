package com.techasians.alertservice.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO chi tiết cho 1 Traffic Event.
 * Dùng cho API getById và kết quả trả về sau create/update.
 * Bao gồm đầy đủ danh sách schedules và channels.
 */
public class TrafficEventResponse {

    private Integer id;
    private String name;
    // 1-Định kỳ, 2-Phát sinh
    private Integer eventType;
    // 1-Hằng tháng, 2-Hằng năm, 3-Hằng quý
    private Integer repeatType;
    // 1-Có quy luật, 2-Không có quy luật
    private Integer patternType;
    // 0-Ngừng áp dụng, 1-Đang áp dụng
    private Integer status;
    private List<TrafficEventScheduleResponse> schedules;
    private List<TrafficEventChannelResponse> channels;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;

    public Integer getId() {
        return id;
    }

    public TrafficEventResponse setId(Integer id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public TrafficEventResponse setName(String name) {
        this.name = name;
        return this;
    }

    public Integer getEventType() {
        return eventType;
    }

    public TrafficEventResponse setEventType(Integer eventType) {
        this.eventType = eventType;
        return this;
    }

    public Integer getRepeatType() {
        return repeatType;
    }

    public TrafficEventResponse setRepeatType(Integer repeatType) {
        this.repeatType = repeatType;
        return this;
    }

    public Integer getPatternType() {
        return patternType;
    }

    public TrafficEventResponse setPatternType(Integer patternType) {
        this.patternType = patternType;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public TrafficEventResponse setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public List<TrafficEventScheduleResponse> getSchedules() {
        return schedules;
    }

    public TrafficEventResponse setSchedules(List<TrafficEventScheduleResponse> schedules) {
        this.schedules = schedules;
        return this;
    }

    public List<TrafficEventChannelResponse> getChannels() {
        return channels;
    }

    public TrafficEventResponse setChannels(List<TrafficEventChannelResponse> channels) {
        this.channels = channels;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public TrafficEventResponse setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public TrafficEventResponse setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public TrafficEventResponse setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public TrafficEventResponse setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
        return this;
    }
}
