package com.techasians.alertservice.dto;

public class ChannelStatusDto {
    private boolean hasEmail;
    private boolean hasSms;

    public ChannelStatusDto(boolean hasEmail, boolean hasSms) {
        this.hasEmail = hasEmail;
        this.hasSms = hasSms;
    }

    public boolean isHasEmail() {
        return hasEmail;
    }

    public void setHasEmail(boolean hasEmail) {
        this.hasEmail = hasEmail;
    }

    public boolean isHasSms() {
        return hasSms;
    }

    public void setHasSms(boolean hasSms) {
        this.hasSms = hasSms;
    }
}
