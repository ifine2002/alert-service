package com.techasians.alertservice.dto;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class AlertUserRequestDTO {

    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 255, message = "Họ tên quá dài")
    private String fullName;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email sai định dạng")
    private String email;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Size(max = 20, message = "Số điện thoại quá dài")
    private String phone;

    @NotBlank(message = "Vị trí/Chức vụ không được để trống")
    @Size(max = 255, message = "Vị trí/Chức vụ quá dài")
    private String position;

    public String getFullName() {
        return fullName;
    }

    public AlertUserRequestDTO setFullName(String fullName) {
        this.fullName = fullName;
        return this;
    }

    public String getEmail() {
        return email;
    }

    public AlertUserRequestDTO setEmail(String email) {
        this.email = email;
        return this;
    }

    public String getPhone() {
        return phone;
    }

    public AlertUserRequestDTO setPhone(String phone) {
        this.phone = phone;
        return this;
    }

    public String getPosition() {
        return position;
    }

    public AlertUserRequestDTO setPosition(String position) {
        this.position = position;
        return this;
    }
}
