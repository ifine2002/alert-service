package com.techasians.alertservice.dto;

public class AlertGroupSumaryResponse {
    private Integer groupId;
    private String groupCode;

    public Integer getGroupId() {
        return groupId;
    }

    public AlertGroupSumaryResponse setGroupId(Integer groupId) {
        this.groupId = groupId;
        return this;
    }

    public String getGroupCode() {
        return groupCode;
    }

    public AlertGroupSumaryResponse setGroupCode(String groupCode) {
        this.groupCode = groupCode;
        return this;
    }

}