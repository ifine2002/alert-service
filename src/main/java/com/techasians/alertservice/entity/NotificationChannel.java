package com.techasians.alertservice.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "notification_channels")
public class NotificationChannel extends BaseAuditEntity<NotificationChannel> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", nullable = false)
    private String name = "";

    @Column(name = "status", nullable = false)
    private Integer status = 1;

    public Integer getId() {
        return id;
    }

    public NotificationChannel setId(Integer id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public NotificationChannel setName(String name) {
        this.name = name;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public NotificationChannel setStatus(Integer status) {
        this.status = status;
        return this;
    }
}
