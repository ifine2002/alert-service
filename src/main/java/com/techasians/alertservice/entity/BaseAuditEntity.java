package com.techasians.alertservice.entity;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;

@MappedSuperclass
public abstract class BaseAuditEntity<T extends BaseAuditEntity<T>> {

    @Column(name = "created_at", nullable = false, updatable = false)
    protected LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    protected LocalDateTime updatedAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    protected String createdBy;

    @Column(name = "updated_by", nullable = false)
    protected String updatedBy;

    @PrePersist
    protected void prePersist() {
        LocalDateTime now = LocalDateTime.now();

        this.createdAt = now;
        this.updatedAt = now;

        if (this.createdBy == null) {
            this.createdBy = "system";
        }

        if (this.updatedBy == null) {
            this.updatedBy = this.createdBy;
        }
    }

    @PreUpdate
    protected void preUpdate() {
        this.updatedAt = LocalDateTime.now();

        if (this.updatedBy == null) {
            this.updatedBy = "system";
        }
    }

    @SuppressWarnings("unchecked")
    protected T self() {
        return (T) this;
    }

    public T setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return self();
    }

    public T setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return self();
    }

    public T setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return self();
    }

    public T setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
        return self();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }
}
