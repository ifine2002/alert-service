package com.techasians.alertservice.dto;

import com.techasians.alertservice.enums.TargetType;

public class AlertGroupSearchRequest {
    private String keyword;
    private Integer status;
    private int page = 0;
    private int size = 10;

    public String getKeyword() {
        return keyword;
    }

    public AlertGroupSearchRequest setKeyword(String keyword) {
        this.keyword = keyword;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public AlertGroupSearchRequest setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public int getPage() {
        return page;
    }

    public AlertGroupSearchRequest setPage(int page) {
        this.page = page;
        return this;
    }

    public int getSize() {
        return size;
    }

    public AlertGroupSearchRequest setSize(int size) {
        this.size = size;
        return this;
    }
}
