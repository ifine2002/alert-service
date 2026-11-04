package com.techasians.alertservice.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "traffic_channel_kpis")
public class TrafficChannelKpi extends BaseAuditEntity<TrafficChannelKpi> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "channel_id", nullable = false)
    private Integer channelId;

    @Column(name = "kpi_rate", nullable = false, precision = 8, scale = 2)
    private BigDecimal kpiRate;

    @Column(name = "productivity_value", nullable = false, precision = 8, scale = 2)
    private BigDecimal productivityValue;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by", length = 100)
    private String deletedBy;

    public Integer getId() {
        return id;
    }

    public TrafficChannelKpi setId(Integer id) {
        this.id = id;
        return this;
    }

    public Integer getChannelId() {
        return channelId;
    }

    public TrafficChannelKpi setChannelId(Integer channelId) {
        this.channelId = channelId;
        return this;
    }

    public BigDecimal getKpiRate() {
        return kpiRate;
    }

    public TrafficChannelKpi setKpiRate(BigDecimal kpiRate) {
        this.kpiRate = kpiRate;
        return this;
    }

    public BigDecimal getProductivityValue() {
        return productivityValue;
    }

    public TrafficChannelKpi setProductivityValue(BigDecimal productivityValue) {
        this.productivityValue = productivityValue;
        return this;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public TrafficChannelKpi setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
        return this;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    public TrafficChannelKpi setDeletedBy(String deletedBy) {
        this.deletedBy = deletedBy;
        return this;
    }
}
