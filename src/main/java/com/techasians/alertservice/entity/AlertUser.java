package com.techasians.alertservice.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "alert_users",
    catalog = "cos_dashboard",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_email_alert_users",
            columnNames = {"email"}
        ),
        @UniqueConstraint(
            name = "uk_phone_alert_users",
            columnNames = {"phone"}
        )
    }
)
public class AlertUser extends BaseAuditEntity<AlertUser> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName = "";

    @Column(name = "email", nullable = false)
    private String email = "";

    @Column(name = "phone", nullable = false, length = 20)
    private String phone = "";

    @Column(name = "position", nullable = false)
    private String position = "";

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    // Soft delete theo chuẩn schema: null = chưa xóa, có giá trị = đã xóa
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by", length = 100)
    private String deletedBy;

    // Getters and Fluent Setters

    public Long getId() {
        return id;
    }

    public AlertUser setId(Long id) {
        this.id = id;
        return this;
    }

    public String getFullName() {
        return fullName;
    }

    public AlertUser setFullName(String fullName) {
        this.fullName = fullName;
        return this;
    }

    public String getEmail() {
        return email;
    }

    public AlertUser setEmail(String email) {
        this.email = email;
        return this;
    }

    public String getPhone() {
        return phone;
    }

    public AlertUser setPhone(String phone) {
        this.phone = phone;
        return this;
    }

    public String getPosition() {
        return position;
    }

    public AlertUser setPosition(String position) {
        this.position = position;
        return this;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public AlertUser setDeleted(boolean deleted) {
        this.deleted = deleted;
        return this;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public AlertUser setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
        return this;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    public AlertUser setDeletedBy(String deletedBy) {
        this.deletedBy = deletedBy;
        return this;
    }
}
