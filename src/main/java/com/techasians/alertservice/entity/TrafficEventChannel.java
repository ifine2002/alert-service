package com.techasians.alertservice.entity;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Entity map bảng traffic_event_channels.
 * Lưu mapping giữa sự kiện và kênh lưu lượng, kèm tỷ lệ tác động.
 *
 * Quy tắc impact_rate:
 * - Sự kiện định kỳ (event_type=1): impact_rate = NULL
 * - Sự kiện phát sinh (event_type=2): impact_rate bắt buộc có giá trị
 */
@Entity
@Table(
    name = "traffic_event_channels",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_traffic_event_channel",
        columnNames = {"event_id", "channel_id"}
    )
)
public class TrafficEventChannel extends BaseAuditEntity<TrafficEventChannel> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id", nullable = false)
    private TrafficChannel channel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private TrafficEvent event;

    // Tỷ lệ tác động của sự kiện đến lưu lượng kênh (0–100)
    @Column(name = "impact_rate", precision = 5, scale = 2)
    private BigDecimal impactRate;

    public Integer getId() {
        return id;
    }

    public TrafficEventChannel setId(Integer id) {
        this.id = id;
        return this;
    }

    public TrafficChannel getChannel() {
        return channel;
    }

    public TrafficEventChannel setChannel(TrafficChannel channel) {
        this.channel = channel;
        return this;
    }

    public TrafficEvent getEvent() {
        return event;
    }

    public TrafficEventChannel setEvent(TrafficEvent event) {
        this.event = event;
        return this;
    }

    public BigDecimal getImpactRate() {
        return impactRate;
    }

    public TrafficEventChannel setImpactRate(BigDecimal impactRate) {
        this.impactRate = impactRate;
        return this;
    }
}
