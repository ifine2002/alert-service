package com.techasians.alertservice.dto;

import com.techasians.alertservice.enums.TargetType;

public class AlertGroupTargetDTO {
    private Long id;
    private TargetType type;
    private String name;

    public Long getId() {
        return id;
    }

    public AlertGroupTargetDTO setId(Long id) {
        this.id = id;
        return this;
    }

    public TargetType getType() {
        return type;
    }

    public AlertGroupTargetDTO setType(TargetType type) {
        this.type = type;
        return this;
    }

    public String getName() {
        return name;
    }

    public AlertGroupTargetDTO setName(String name) {
        this.name = name;
        return this;
    }
}
