package com.techasians.alertservice.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.*;

@Entity
@Table(name = "alert_groups", uniqueConstraints = {
        @UniqueConstraint(name = "uk_code_alert_groups", columnNames = { "code" })
})
public class AlertGroup extends BaseAuditEntity<AlertGroup> implements Serializable {

    private static final Long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // Removed serviceQualityOrg and serviceQualityCategory

    @Column(name = "name", nullable = false)
    private String name = "";

    @Column(name = "code", nullable = false, length = 100)
    private String code = "";

    @Column(name = "description", nullable = false)
    private String description = "";

    @Column(name = "status", nullable = false)
    private Integer status = 1;

    // Soft delete: null = chua xoa, co gia tri = da xoa
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by", length = 100)
    private String deletedBy;

    // Getters and Setters

    public Integer getId() {
        return id;
    }

    public AlertGroup setId(Integer id) {
        this.id = id;
        return this;
    }



    public String getName() {
        return name;
    }

    public AlertGroup setName(String name) {
        this.name = name;
        return this;
    }

    public String getCode() {
        return code;
    }

    public AlertGroup setCode(String code) {
        this.code = code;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public AlertGroup setDescription(String description) {
        this.description = description;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public AlertGroup setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public AlertGroup setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
        return this;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    public AlertGroup setDeletedBy(String deletedBy) {
        this.deletedBy = deletedBy;
        return this;
    }
}
