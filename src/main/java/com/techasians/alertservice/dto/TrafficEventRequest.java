package com.techasians.alertservice.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Request DTO dùng chung cho Create và Update Traffic Event.
 * Validate business rule được thực hiện trong service layer
 * (vì phụ thuộc vào tổ hợp eventType/repeatType/patternType).
 */
public class TrafficEventRequest {

    @NotBlank(message = "Tên sự kiện không được để trống")
    private String name;

    // 1-Định kỳ, 2-Phát sinh
    @NotNull(message = "event_type không được để trống")
    private Integer eventType;

    // 1-Hằng tháng, 2-Hằng năm, 3-Hằng quý — chỉ bắt buộc khi eventType=1
    private Integer repeatType;

    // 1-Có quy luật, 2-Không có quy luật — chỉ bắt buộc khi eventType=1
    private Integer patternType;

    @NotEmpty(message = "Phải có ít nhất 1 lịch sự kiện")
    @Valid
    private List<TrafficEventScheduleRequest> schedules;

    @NotEmpty(message = "Phải có ít nhất 1 kênh")
    @Valid
    private List<TrafficEventChannelRequest> channels;

    private Integer status;

    public String getName() {
        return name;
    }

    public TrafficEventRequest setName(String name) {
        this.name = name;
        return this;
    }

    public Integer getEventType() {
        return eventType;
    }

    public TrafficEventRequest setEventType(Integer eventType) {
        this.eventType = eventType;
        return this;
    }

    public Integer getRepeatType() {
        return repeatType;
    }

    public TrafficEventRequest setRepeatType(Integer repeatType) {
        this.repeatType = repeatType;
        return this;
    }

    public Integer getPatternType() {
        return patternType;
    }

    public TrafficEventRequest setPatternType(Integer patternType) {
        this.patternType = patternType;
        return this;
    }

    public List<TrafficEventScheduleRequest> getSchedules() {
        return schedules;
    }

    public TrafficEventRequest setSchedules(List<TrafficEventScheduleRequest> schedules) {
        this.schedules = schedules;
        return this;
    }

    public List<TrafficEventChannelRequest> getChannels() {
        return channels;
    }

    public TrafficEventRequest setChannels(List<TrafficEventChannelRequest> channels) {
        this.channels = channels;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public TrafficEventRequest setStatus(Integer status) {
        this.status = status;
        return this;
    }
}
