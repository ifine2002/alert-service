package com.techasians.alertservice.entity;

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
@Table(name = "alert_level_recipients", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "alert_level_id", "alert_group_id" })
})
public class AlertLevelRecipient extends BaseAuditEntity<AlertLevelRecipient> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_level_id", nullable = false)
    private AlertLevel alertLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_group_id", nullable = false)
    private AlertGroup alertGroup;

    public Integer getId() {
        return id;
    }

    public AlertLevelRecipient setId(Integer id) {
        this.id = id;
        return this;
    }

    public AlertLevel getAlertLevel() {
        return alertLevel;
    }

    public AlertLevelRecipient setAlertLevel(AlertLevel alertLevel) {
        this.alertLevel = alertLevel;
        return this;
    }

    public AlertGroup getAlertGroup() {
        return alertGroup;
    }

    public AlertLevelRecipient setAlertGroup(AlertGroup alertGroup) {
        this.alertGroup = alertGroup;
        return this;
    }
}
