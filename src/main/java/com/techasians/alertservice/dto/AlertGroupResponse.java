package com.techasians.alertservice.dto;

import com.techasians.alertservice.enums.TargetType;
import java.util.List;

public class AlertGroupResponse {
    private Integer id;
    private List<AlertGroupTargetDTO> options;
    private String code;
    private String name;
    private Integer status;
    private String description;
    private List<AlertUserDTO> alertUsers;

    public Integer getId() {
        return id;
    }

    public AlertGroupResponse setId(Integer id) {
        this.id = id;
        return this;
    }

    public List<AlertGroupTargetDTO> getOptions() {
        return options;
    }

    public AlertGroupResponse setOptions(List<AlertGroupTargetDTO> options) {
        this.options = options;
        return this;
    }

    public String getCode() {
        return code;
    }

    public AlertGroupResponse setCode(String code) {
        this.code = code;
        return this;
    }

    public String getName() {
        return name;
    }

    public AlertGroupResponse setName(String name) {
        this.name = name;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public AlertGroupResponse setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public AlertGroupResponse setDescription(String description) {
        this.description = description;
        return this;
    }

    public List<AlertUserDTO> getAlertUsers() {
        return alertUsers;
    }

    public AlertGroupResponse setAlertUsers(List<AlertUserDTO> alertUsers) {
        this.alertUsers = alertUsers;
        return this;
    }
}
