package com.techasians.alertservice.dto;

import com.techasians.alertservice.enums.TargetType;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

public class AlertGroupRequest {

    private List<AlertGroupTargetDTO> options;

    @NotBlank(message = "Mã đầu mối không được để trống")
    private String code;

    @NotBlank
    private String name;

    @NotNull(message = "Trạng thái không được để trống")
    private Integer status;

    private String description;

    @NotEmpty
    private List<@NotNull Long> alertUserIds;

    public List<AlertGroupTargetDTO> getOptions() {
        return options;
    }

    public AlertGroupRequest setOptions(List<AlertGroupTargetDTO> options) {
        this.options = options;
        return this;
    }

    public String getCode() {
        return code;
    }

    public AlertGroupRequest setCode(String code) {
        this.code = code;
        return this;
    }

    public String getName() {
        return name;
    }

    public AlertGroupRequest setName(String name) {
        this.name = name;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public AlertGroupRequest setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public AlertGroupRequest setDescription(String description) {
        this.description = description;
        return this;
    }

    public List<Long> getAlertUserIds() {
        return alertUserIds;
    }

    public AlertGroupRequest setAlertUserIds(List<Long> alertUserIds) {
        this.alertUserIds = alertUserIds;
        return this;
    }
}
