package com.techasians.alertservice.entity;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Mapping giữa dữ liệu lưu lượng lịch sử và sự kiện.
 * FK tham chiếu trực tiếp traffic_events (traffic_event_id).
 *
 * Lý do không link schedule_id: Ngày import là ngày thực tế xảy ra,
 * có thể không khớp với lịch dự kiến — không cần so khớp schedule.
 */
@Entity
@Table(
    name = "traffic_history_events",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_traffic_history_event",
        columnNames = {"traffic_history_id", "traffic_event_id"}
    )
)
public class TrafficHistoryEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "traffic_history_id", nullable = false)
    private TrafficHistory trafficHistory;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "traffic_event_id", nullable = false)
    private TrafficEvent trafficEvent;

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

    public TrafficHistoryEvent setId(Long id) {
        this.id = id;
        return this;
    }

    public TrafficHistory getTrafficHistory() {
        return trafficHistory;
    }

    public TrafficHistoryEvent setTrafficHistory(TrafficHistory trafficHistory) {
        this.trafficHistory = trafficHistory;
        return this;
    }

    public TrafficEvent getTrafficEvent() {
        return trafficEvent;
    }

    public TrafficHistoryEvent setTrafficEvent(TrafficEvent trafficEvent) {
        this.trafficEvent = trafficEvent;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public TrafficHistoryEvent setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public TrafficHistoryEvent setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }
}
