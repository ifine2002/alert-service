package com.techasians.alertservice.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class ReportDeleteRequest {
    @NotBlank(message = "Người thực hiện không được để trống")
    @Size(max = 100, message = "Tên người thực hiện không được vượt quá 100 ký tự")
    private String actor;

    @Size(max = 2000, message = "Lý do xóa không được vượt quá 2000 ký tự")
    private String reason;

    public String getActor() { return actor; }
    public ReportDeleteRequest setActor(String actor) { this.actor = actor; return this; }
    public String getReason() { return reason; }
    public ReportDeleteRequest setReason(String reason) { this.reason = reason; return this; }
}