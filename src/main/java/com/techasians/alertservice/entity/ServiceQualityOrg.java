package com.techasians.alertservice.entity;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "service_quality_orgs",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_service_quality_orgs_code",
            columnNames = {"code"}
        )
    }
)
public class ServiceQualityOrg implements Serializable {

    private static final Long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name = "";

    @Column(name = "code", nullable = false, length = 100)
    private String code = "";

    @Column(name = "status", nullable = false)
    private Integer status = 1;

    @Column(name = "type", nullable = false)
    private Integer type = 1;

    @Column(name = "description", nullable = false)
    private String description = "";

    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy = "";

    @Column(name = "last_modified_date")
    private LocalDateTime lastModifiedDate;

    @Column(name = "last_modified_by", nullable = false, length = 100)
    private String lastModifiedBy = "";

    // Auditing methods
    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdDate = now;
        this.lastModifiedDate = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.lastModifiedDate = LocalDateTime.now();
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public ServiceQualityOrg setId(Long id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public ServiceQualityOrg setName(String name) {
        this.name = name;
        return this;
    }

    public String getCode() {
        return code;
    }

    public ServiceQualityOrg setCode(String code) {
        this.code = code;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public ServiceQualityOrg setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public Integer getType() {
        return type;
    }

    public ServiceQualityOrg setType(Integer type) {
        this.type = type;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public ServiceQualityOrg setDescription(String description) {
        this.description = description;
        return this;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public ServiceQualityOrg setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
        return this;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public ServiceQualityOrg setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    public LocalDateTime getLastModifiedDate() {
        return lastModifiedDate;
    }

    public ServiceQualityOrg setLastModifiedDate(LocalDateTime lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
        return this;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public ServiceQualityOrg setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
        return this;
    }
}
