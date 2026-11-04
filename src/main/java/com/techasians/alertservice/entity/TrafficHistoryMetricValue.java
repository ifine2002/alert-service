package com.techasians.alertservice.entity;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "traffic_history_metric_values", uniqueConstraints = {
        @UniqueConstraint(name = "uk_history_channel_metric", columnNames = {"traffic_history_id", "channel_metric_id"})
})
public class TrafficHistoryMetricValue implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "traffic_history_id", nullable = false)
    private TrafficHistory trafficHistory;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_metric_id", nullable = false)
    private TrafficChannelMetric channelMetric;

    @Column(name = "metric_value", precision = 20, scale = 4)
    private BigDecimal metricValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy = "";

    @PrePersist
    protected void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.createdBy == null || this.createdBy.isEmpty()) {
            this.createdBy = "system";
        }
    }

    public Long getId() {
        return id;
    }

    public TrafficHistoryMetricValue setId(Long id) {
        this.id = id;
        return this;
    }

    public TrafficHistory getTrafficHistory() {
        return trafficHistory;
    }

    public TrafficHistoryMetricValue setTrafficHistory(TrafficHistory trafficHistory) {
        this.trafficHistory = trafficHistory;
        return this;
    }

    public TrafficChannelMetric getChannelMetric() {
        return channelMetric;
    }

    public TrafficHistoryMetricValue setChannelMetric(TrafficChannelMetric channelMetric) {
        this.channelMetric = channelMetric;
        return this;
    }

    public BigDecimal getMetricValue() {
        return metricValue;
    }

    public TrafficHistoryMetricValue setMetricValue(BigDecimal metricValue) {
        this.metricValue = metricValue;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public TrafficHistoryMetricValue setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public TrafficHistoryMetricValue setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }
}
