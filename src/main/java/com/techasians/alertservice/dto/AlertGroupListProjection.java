package com.techasians.alertservice.dto;

public interface AlertGroupListProjection {
    Integer getId();
    String getCode();
    String getName();
    Integer getStatus();
    String getDescription();
    Long getMemberCount();
    String getTargetNames();
}
