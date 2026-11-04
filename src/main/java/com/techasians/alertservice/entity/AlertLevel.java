package com.techasians.alertservice.entity;

import java.math.BigDecimal;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Entity
@Table(name = "alert_levels", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "alert_config_id", "level_number" })
})
public class AlertLevel extends BaseAuditEntity<AlertLevel> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_config_id", nullable = false)
    private AlertConfig alertConfig;

    @Column(name = "level_number", nullable = false)
    private Integer levelNumber = 1;

    @Column(name = "condition_operator", nullable = false)
    private Integer conditionOperator = 1;

    @Column(name = "threshold_value", nullable = false, precision = 18, scale = 4)
    private BigDecimal thresholdValue = BigDecimal.ZERO;

    @Column(name = "threshold_value_to", nullable = false, precision = 18, scale = 4)
    private BigDecimal thresholdValueTo = BigDecimal.ZERO;

    @Column(name = "status", nullable = false)
    private Integer status = 1;

    public Integer getId() {
        return id;
    }

    public AlertLevel setId(Integer id) {
        this.id = id;
        return this;
    }

    public AlertConfig getAlertConfig() {
        return alertConfig;
    }

    public AlertLevel setAlertConfig(AlertConfig alertConfig) {
        this.alertConfig = alertConfig;
        return this;
    }

    public Integer getLevelNumber() {
        return levelNumber;
    }

    public AlertLevel setLevelNumber(Integer levelNumber) {
        this.levelNumber = levelNumber;
        return this;
    }

    public Integer getConditionOperator() {
        return conditionOperator;
    }

    public AlertLevel setConditionOperator(Integer conditionOperator) {
        this.conditionOperator = conditionOperator;
        return this;
    }

    public BigDecimal getThresholdValue() {
        return thresholdValue;
    }

    public AlertLevel setThresholdValue(BigDecimal thresholdValue) {
        this.thresholdValue = thresholdValue;
        return this;
    }

    public BigDecimal getThresholdValueTo() {
        return thresholdValueTo;
    }

    public AlertLevel setThresholdValueTo(BigDecimal thresholdValueTo) {
        this.thresholdValueTo = thresholdValueTo;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public AlertLevel setStatus(Integer status) {
        this.status = status;
        return this;
    }
}
