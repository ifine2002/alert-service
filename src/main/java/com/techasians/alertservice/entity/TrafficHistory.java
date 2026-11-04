package com.techasians.alertservice.entity;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "traffic_histories", uniqueConstraints = {
        @UniqueConstraint(name = "uk_region_channel_date", columnNames = {"region_channel_id", "traffic_date"})
})
public class TrafficHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "traffic_date")
    private LocalDate trafficDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_channel_id", nullable = false)
    private TrafficRegionChannel regionChannel;

    @Column(name = "has_event", nullable = false)
    private Integer hasEvent = 0;

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

    public TrafficHistory setId(Long id) {
        this.id = id;
        return this;
    }

    public LocalDate getTrafficDate() {
        return trafficDate;
    }

    public TrafficHistory setTrafficDate(LocalDate trafficDate) {
        this.trafficDate = trafficDate;
        return this;
    }

    public TrafficRegionChannel getRegionChannel() {
        return regionChannel;
    }

    public TrafficHistory setRegionChannel(TrafficRegionChannel regionChannel) {
        this.regionChannel = regionChannel;
        return this;
    }

    public Integer getHasEvent() {
        return hasEvent;
    }

    public TrafficHistory setHasEvent(Integer hasEvent) {
        this.hasEvent = hasEvent;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public TrafficHistory setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public TrafficHistory setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }
}
