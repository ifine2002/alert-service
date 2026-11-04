package com.techasians.alertservice.dto;

public class AlertUserDTO {
    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private String position;

    public AlertUserDTO() {
    }

    public AlertUserDTO(Long id, String fullName, String email, String phone, String position) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    public AlertUserDTO setId(Long id) {
        this.id = id;
        return this;
    }

    public String getFullName() {
        return fullName;
    }

    public AlertUserDTO setFullName(String fullName) {
        this.fullName = fullName;
        return this;
    }

    public String getEmail() {
        return email;
    }

    public AlertUserDTO setEmail(String email) {
        this.email = email;
        return this;
    }

    public String getPhone() {
        return phone;
    }

    public AlertUserDTO setPhone(String phone) {
        this.phone = phone;
        return this;
    }

    public String getPosition() {
        return position;
    }

    public AlertUserDTO setPosition(String position) {
        this.position = position;
        return this;
    }
}
