package com.techasians.alertservice.dto;

import java.util.List;

public class AlertGroupListResponse {
    private Integer id;
    private String targetNames;
    private String code;
    private String name;
    private Integer status;
    private String statusName;
    private String description;
    private Long memberCount;

    public Integer getId() {
        return id;
    }

    public AlertGroupListResponse setId(Integer id) {
        this.id = id;
        return this;
    }

    public String getTargetNames() {
        return targetNames;
    }

    public AlertGroupListResponse setTargetNames(String targetNames) {
        this.targetNames = targetNames;
        return this;
    }

    public String getCode() {
        return code;
    }

    public AlertGroupListResponse setCode(String code) {
        this.code = code;
        return this;
    }

    public String getName() {
        return name;
    }

    public AlertGroupListResponse setName(String name) {
        this.name = name;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public AlertGroupListResponse setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public String getStatusName() {
        return statusName;
    }

    public AlertGroupListResponse setStatusName(String statusName) {
        this.statusName = statusName;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public AlertGroupListResponse setDescription(String description) {
        this.description = description;
        return this;
    }

    public Long getMemberCount() {
        return memberCount;
    }

    public AlertGroupListResponse setMemberCount(Long memberCount) {
        this.memberCount = memberCount;
        return this;
    }
}
