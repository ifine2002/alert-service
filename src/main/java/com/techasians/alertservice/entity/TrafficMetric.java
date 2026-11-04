package com.techasians.alertservice.entity;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "traffic_metrics")
public class TrafficMetric extends BaseAuditEntity<TrafficMetric> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", nullable = false, length = 255)
    private String name = "";

    @Column(name = "data_type", nullable = false)
    private Integer dataType = 1;

    @Column(name = "unit", nullable = false)
    private Integer unit = 1;

    @Column(name = "status", nullable = false)
    private Integer status = 1;

    public Integer getId() {
        return id;
    }

    public TrafficMetric setId(Integer id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public TrafficMetric setName(String name) {
        this.name = name;
        return this;
    }

    public Integer getDataType() {
        return dataType;
    }

    public TrafficMetric setDataType(Integer dataType) {
        this.dataType = dataType;
        return this;
    }

    public Integer getUnit() {
        return unit;
    }

    public TrafficMetric setUnit(Integer unit) {
        this.unit = unit;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public TrafficMetric setStatus(Integer status) {
        this.status = status;
        return this;
    }
}
