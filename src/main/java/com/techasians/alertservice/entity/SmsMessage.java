package com.techasians.alertservice.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "messages", catalog = "cos_dashboard")
public class SmsMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "receiver_phone", length = 20)
    private String receiverPhone;

    @Column(name = "receiver_full_name", length = 255)
    private String receiverFullName;

    @Column(name = "unit", length = 100)
    private String unit;

    // 0 = Chờ gửi
    @Column(name = "status")
    private Integer status = 0;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        this.createTime = LocalDateTime.now();
    }

    // --- Getters & Setters ---

    public Long getId() {
        return id;
    }

    public SmsMessage setId(Long id) {
        this.id = id;
        return this;
    }

    public String getContent() {
        return content;
    }

    public SmsMessage setContent(String content) {
        this.content = content;
        return this;
    }

    public String getReceiverPhone() {
        return receiverPhone;
    }

    public SmsMessage setReceiverPhone(String receiverPhone) {
        this.receiverPhone = receiverPhone;
        return this;
    }

    public String getReceiverFullName() {
        return receiverFullName;
    }

    public SmsMessage setReceiverFullName(String receiverFullName) {
        this.receiverFullName = receiverFullName;
        return this;
    }

    public String getUnit() {
        return unit;
    }

    public SmsMessage setUnit(String unit) {
        this.unit = unit;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public SmsMessage setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public SmsMessage setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
        return this;
    }
}
