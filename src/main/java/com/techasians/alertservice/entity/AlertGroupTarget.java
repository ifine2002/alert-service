package com.techasians.alertservice.entity;

import com.techasians.alertservice.enums.TargetType;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "alert_group_targets", uniqueConstraints = {
        @UniqueConstraint(name = "uk_alert_group_target", columnNames = { "alert_group_id", "target_id", "target_type" })
})
public class AlertGroupTarget extends BaseAuditEntity<AlertGroupTarget> implements Serializable {

    private static final Long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_group_id", nullable = false)
    private AlertGroup alertGroup;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "target_type", nullable = false, length = 50)
    private TargetType targetType;

    public Integer getId() {
        return id;
    }

    public AlertGroupTarget setId(Integer id) {
        this.id = id;
        return this;
    }

    public AlertGroup getAlertGroup() {
        return alertGroup;
    }

    public AlertGroupTarget setAlertGroup(AlertGroup alertGroup) {
        this.alertGroup = alertGroup;
        return this;
    }

    public Long getTargetId() {
        return targetId;
    }

    public AlertGroupTarget setTargetId(Long targetId) {
        this.targetId = targetId;
        return this;
    }

    public TargetType getTargetType() {
        return targetType;
    }

    public AlertGroupTarget setTargetType(TargetType targetType) {
        this.targetType = targetType;
        return this;
    }
}
