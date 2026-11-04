package com.techasians.alertservice.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * Request body khi reject một file input.
 * Hiện tại (Phase 2) chưa kiểm tra quyền/role reject; actor lấy từ request body.
 */
public class ReportRejectRequest {

    // Lý do từ chối — bắt buộc, tối đa 2000 ký tự
    @NotBlank(message = "Lý do từ chối không được để trống")
    @Size(max = 2000, message = "Lý do từ chối không được vượt quá 2000 ký tự")
    private String reason;

    // Người thực hiện từ chối — bắt buộc, tối đa 100 ký tự
    // Phase 2: tạm nhận từ request; khi có authentication sẽ lấy từ SecurityContext
    @NotBlank(message = "Người thực hiện không được để trống")
    @Size(max = 100, message = "Tên người thực hiện không được vượt quá 100 ký tự")
    private String actor;

    public String getReason() {
        return reason;
    }

    public ReportRejectRequest setReason(String reason) {
        this.reason = reason;
        return this;
    }

    public String getActor() {
        return actor;
    }

    public ReportRejectRequest setActor(String actor) {
        this.actor = actor;
        return this;
    }
}
