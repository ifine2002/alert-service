package com.techasians.alertservice.entity;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;

@Entity
@Table(name = "alert_config_histories")
public class AlertConfigHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_config_id", nullable = false)
    private Integer alertConfigId;

    @Column(name = "action_type", nullable = false)
    private Integer actionType;

    @Column(name = "before_snapshot", columnDefinition = "JSON")
    private String beforeSnapshot;

    @Column(name = "after_snapshot", columnDefinition = "JSON")
    private String afterSnapshot;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 100, nullable = false)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.createdBy == null) {
            this.createdBy = "";
        }
    }

    public Long getId() {
        return id;
    }

    public AlertConfigHistory setId(Long id) {
        this.id = id;
        return this;
    }

    public Integer getAlertConfigId() {
        return alertConfigId;
    }

    public AlertConfigHistory setAlertConfigId(Integer alertConfigId) {
        this.alertConfigId = alertConfigId;
        return this;
    }

    public Integer getActionType() {
        return actionType;
    }

    public AlertConfigHistory setActionType(Integer actionType) {
        this.actionType = actionType;
        return this;
    }

    public String getBeforeSnapshot() {
        return beforeSnapshot;
    }

    public AlertConfigHistory setBeforeSnapshot(String beforeSnapshot) {
        this.beforeSnapshot = beforeSnapshot;
        return this;
    }

    public String getAfterSnapshot() {
        return afterSnapshot;
    }

    public AlertConfigHistory setAfterSnapshot(String afterSnapshot) {
        this.afterSnapshot = afterSnapshot;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public AlertConfigHistory setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public AlertConfigHistory setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }
}
