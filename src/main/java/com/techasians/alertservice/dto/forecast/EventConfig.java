package com.techasians.alertservice.dto.forecast;

import java.util.ArrayList;
import java.util.List;

/**
 * Cấu hình đầy đủ của một sự kiện dùng trong EventCalendarBuilder.
 * Gồm thông tin event + danh sách schedules.
 */
public class EventConfig {

    private Integer id;
    private String name;

    // 1=Định kỳ, 2=Phát sinh
    private Integer eventType;

    // 1=Hằng tháng, 2=Hằng năm, 3=Hằng quý - chỉ có giá trị khi eventType=1
    private Integer repeatType;

    // 1=Có quy luật, 2=Không có quy luật - chỉ có giá trị khi eventType=1
    private Integer patternType;

    // Danh sách các lịch diễn ra
    private List<EventScheduleConfig> schedules;

    // Danh sách channel được mapping với event này.
    // Event không mapping channel thì không được coi là event của channel đó.
    private List<Integer> channelIds = new ArrayList<>();

    public EventConfig() {
    }

    public Integer getId() {
        return id;
    }

    public EventConfig setId(Integer id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public EventConfig setName(String name) {
        this.name = name;
        return this;
    }

    public Integer getEventType() {
        return eventType;
    }

    public EventConfig setEventType(Integer eventType) {
        this.eventType = eventType;
        return this;
    }

    public Integer getRepeatType() {
        return repeatType;
    }

    public EventConfig setRepeatType(Integer repeatType) {
        this.repeatType = repeatType;
        return this;
    }

    public Integer getPatternType() {
        return patternType;
    }

    public EventConfig setPatternType(Integer patternType) {
        this.patternType = patternType;
        return this;
    }

    public List<EventScheduleConfig> getSchedules() {
        return schedules;
    }

    public EventConfig setSchedules(List<EventScheduleConfig> schedules) {
        this.schedules = schedules;
        return this;
    }

    public List<Integer> getChannelIds() {
        return channelIds;
    }

    public EventConfig setChannelIds(List<Integer> channelIds) {
        this.channelIds = channelIds != null ? channelIds : new ArrayList<>();
        return this;
    }
}
