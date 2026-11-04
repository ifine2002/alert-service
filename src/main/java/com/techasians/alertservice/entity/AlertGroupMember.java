package com.techasians.alertservice.entity;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "alert_group_members", catalog = "cos_dashboard", uniqueConstraints = {
        @UniqueConstraint(name = "uk_group_user", columnNames = { "alert_group_id", "alert_user_id" })
})
public class AlertGroupMember implements Serializable {

    private static final Long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * FK toi alert_groups
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alert_group_id", nullable = false)
    private AlertGroup alertGroup;

    /**
     * FK toi alert_users
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alert_user_id", nullable = false)
    private AlertUser alertUser;

    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy = "";

    // Getters and Setters

    @PrePersist
    public void prePersist() {
        this.createdDate = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public AlertGroupMember setId(Long id) {
        this.id = id;
        return this;
    }

    public AlertGroup getAlertGroup() {
        return alertGroup;
    }

    public AlertGroupMember setAlertGroup(AlertGroup alertGroup) {
        this.alertGroup = alertGroup;
        return this;
    }

    public AlertUser getAlertUser() {
        return alertUser;
    }

    public AlertGroupMember setAlertUser(AlertUser alertUser) {
        this.alertUser = alertUser;
        return this;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public AlertGroupMember setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
        return this;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public AlertGroupMember setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }
}
