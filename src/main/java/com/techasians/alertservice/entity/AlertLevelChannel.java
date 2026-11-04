package com.techasians.alertservice.entity;

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
@Table(name = "alert_level_channels", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "alert_level_id", "notification_channel_id" })
})
public class AlertLevelChannel extends BaseAuditEntity<AlertLevelChannel> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_level_id", nullable = false)
    private AlertLevel alertLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_channel_id", nullable = false)
    private NotificationChannel notificationChannel;

    @Column(name = "status", nullable = false)
    private Integer status = 1;

    public Integer getId() {
        return id;
    }

    public AlertLevelChannel setId(Integer id) {
        this.id = id;
        return this;
    }

    public AlertLevel getAlertLevel() {
        return alertLevel;
    }

    public AlertLevelChannel setAlertLevel(AlertLevel alertLevel) {
        this.alertLevel = alertLevel;
        return this;
    }

    public NotificationChannel getNotificationChannel() {
        return notificationChannel;
    }

    public AlertLevelChannel setNotificationChannel(NotificationChannel notificationChannel) {
        this.notificationChannel = notificationChannel;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public AlertLevelChannel setStatus(Integer status) {
        this.status = status;
        return this;
    }
}
