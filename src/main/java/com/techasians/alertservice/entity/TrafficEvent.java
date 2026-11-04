package com.techasians.alertservice.entity;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "traffic_events")
public class TrafficEvent extends BaseAuditEntity<TrafficEvent> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", nullable = false, length = 255)
    private String name = "";

    // 1-Định kỳ, 2-Phát sinh
    @Column(name = "event_type", nullable = false)
    private Integer eventType;

    // 1-Hằng tháng, 2-Hằng năm, 3-Hằng quý — chỉ có giá trị khi eventType=1
    @Column(name = "repeat_type")
    private Integer repeatType;

    // 1-Có quy luật, 2-Không có quy luật — chỉ có giá trị khi eventType=1
    @Column(name = "pattern_type")
    private Integer patternType;

    // 0-Ngừng áp dụng, 1-Đang áp dụng
    @Column(name = "status", nullable = false)
    private Integer status = 1;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by", length = 100)
    private String deletedBy;

    public Integer getId() {
        return id;
    }

    public TrafficEvent setId(Integer id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public TrafficEvent setName(String name) {
        this.name = name;
        return this;
    }

    public Integer getEventType() {
        return eventType;
    }

    public TrafficEvent setEventType(Integer eventType) {
        this.eventType = eventType;
        return this;
    }

    public Integer getRepeatType() {
        return repeatType;
    }

    public TrafficEvent setRepeatType(Integer repeatType) {
        this.repeatType = repeatType;
        return this;
    }

    public Integer getPatternType() {
        return patternType;
    }

    public TrafficEvent setPatternType(Integer patternType) {
        this.patternType = patternType;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public TrafficEvent setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public TrafficEvent setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
        return this;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    public TrafficEvent setDeletedBy(String deletedBy) {
        this.deletedBy = deletedBy;
        return this;
    }
}
