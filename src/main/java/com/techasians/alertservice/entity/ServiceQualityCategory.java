package com.techasians.alertservice.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "service_quality_categories")
public class ServiceQualityCategory extends BaseAuditEntity<ServiceQualityCategory> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", nullable = false)
    private String name = "";

    @Column(name = "parent_id")
    private Integer parentId;

    @Column(name = "status", nullable = false)
    private Integer status = 1;

    public Integer getId() {
        return id;
    }

    public ServiceQualityCategory setId(Integer id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public ServiceQualityCategory setName(String name) {
        this.name = name;
        return this;
    }

    public Integer getParentId() {
        return parentId;
    }

    public ServiceQualityCategory setParentId(Integer parentId) {
        this.parentId = parentId;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public ServiceQualityCategory setStatus(Integer status) {
        this.status = status;
        return this;
    }
}
