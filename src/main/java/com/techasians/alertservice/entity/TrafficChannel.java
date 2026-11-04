package com.techasians.alertservice.entity;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "traffic_channels")
public class TrafficChannel extends BaseAuditEntity<TrafficChannel> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "parent_id")
    private Integer parentId;

    @Column(name = "name", nullable = false, length = 255)
    private String name = "";

    @Column(name = "status", nullable = false)
    private Integer status = 1;

    // 0 - T7/CN không áp dụng rule cuối tuần, 1 - T7/CN áp dụng rule cuối tuần
    @Column(name = "weekend_affected", nullable = false)
    private Integer weekendAffected = 0;

    public Integer getId() {
        return id;
    }

    public TrafficChannel setId(Integer id) {
        this.id = id;
        return this;
    }

    public Integer getParentId() {
        return parentId;
    }

    public TrafficChannel setParentId(Integer parentId) {
        this.parentId = parentId;
        return this;
    }

    public String getName() {
        return name;
    }

    public TrafficChannel setName(String name) {
        this.name = name;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public TrafficChannel setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public Integer getWeekendAffected() {
        return weekendAffected;
    }

    public TrafficChannel setWeekendAffected(Integer weekendAffected) {
        this.weekendAffected = weekendAffected;
        return this;
    }

    // Helper: kiểm tra channel có áp dụng rule cuối tuần không
    public boolean isWeekendAffected() {
        return Integer.valueOf(1).equals(weekendAffected);
    }

    // 0 - Thứ 2 không áp dụng rule đặc biệt, 1 - Thứ 2 áp dụng rule đặc biệt
    @Column(name = "monday_affected", nullable = false)
    private Integer mondayAffected = 0;

    public Integer getMondayAffected() {
        return mondayAffected;
    }

    public TrafficChannel setMondayAffected(Integer mondayAffected) {
        this.mondayAffected = mondayAffected;
        return this;
    }

    // Helper: kiểm tra channel có áp dụng rule Thứ 2 không
    public boolean isMondayAffected() {
        return Integer.valueOf(1).equals(mondayAffected);
    }
}
