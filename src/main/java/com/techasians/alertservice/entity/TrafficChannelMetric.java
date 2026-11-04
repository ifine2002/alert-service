package com.techasians.alertservice.entity;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "traffic_channel_metrics", uniqueConstraints = {
        @UniqueConstraint(name = "uk_channel_metric", columnNames = {"channel_id", "metric_id"})
})
public class TrafficChannelMetric extends BaseAuditEntity<TrafficChannelMetric> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id", nullable = false)
    private TrafficChannel channel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "metric_id", nullable = false)
    private TrafficMetric metric;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "status", nullable = false)
    private Integer status = 1;

    public Long getId() {
        return id;
    }

    public TrafficChannelMetric setId(Long id) {
        this.id = id;
        return this;
    }

    public TrafficChannel getChannel() {
        return channel;
    }

    public TrafficChannelMetric setChannel(TrafficChannel channel) {
        this.channel = channel;
        return this;
    }

    public TrafficMetric getMetric() {
        return metric;
    }

    public TrafficChannelMetric setMetric(TrafficMetric metric) {
        this.metric = metric;
        return this;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public TrafficChannelMetric setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public TrafficChannelMetric setStatus(Integer status) {
        this.status = status;
        return this;
    }
}
