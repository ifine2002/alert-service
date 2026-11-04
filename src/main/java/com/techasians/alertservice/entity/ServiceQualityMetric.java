package com.techasians.alertservice.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name = "service_quality_metrics")
public class ServiceQualityMetric extends BaseAuditEntity<ServiceQualityMetric> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "category_id")
    private Integer categoryId;

    @Column(name = "name", nullable = false)
    private String name = "";

    @Column(name = "short_name", nullable = false)
    private String shortName = "";

    @Column(name = "status", nullable = false)
    private Integer status = 1;

    @Column(name = "type", nullable = false)
    private Integer type = 1;

    public Integer getId() {
        return id;
    }

    public ServiceQualityMetric setId(Integer id) {
        this.id = id;
        return this;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public ServiceQualityMetric setCategory(Integer categoryId) {
        this.categoryId = categoryId;
        return this;
    }

    public String getName() {
        return name;
    }

    public ServiceQualityMetric setName(String name) {
        this.name = name;
        return this;
    }

    public String getShortName() {
        return shortName;
    }

    public ServiceQualityMetric setShortName(String shortName) {
        this.shortName = shortName;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public ServiceQualityMetric setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public Integer getType() {
        return type;
    }

    public ServiceQualityMetric setType(Integer type) {
        this.type = type;
        return this;
    }
}
