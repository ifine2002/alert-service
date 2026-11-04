package com.techasians.alertservice.entity;

import javax.persistence.*;

@Entity
@Table(name = "traffic_region_channels", uniqueConstraints = {
        @UniqueConstraint(name = "uk_region_channel", columnNames = {"region_id", "channel_id"})
})
public class TrafficRegionChannel extends BaseAuditEntity<TrafficRegionChannel> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_id", nullable = false)
    private TrafficRegion region;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id", nullable = false)
    private TrafficChannel channel;

    @Column(name = "status", nullable = false)
    private Integer status = 1;

    public Long getId() {
        return id;
    }

    public TrafficRegionChannel setId(Long id) {
        this.id = id;
        return this;
    }

    public TrafficRegion getRegion() {
        return region;
    }

    public TrafficRegionChannel setRegion(TrafficRegion region) {
        this.region = region;
        return this;
    }

    public TrafficChannel getChannel() {
        return channel;
    }

    public TrafficRegionChannel setChannel(TrafficChannel channel) {
        this.channel = channel;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public TrafficRegionChannel setStatus(Integer status) {
        this.status = status;
        return this;
    }
}
