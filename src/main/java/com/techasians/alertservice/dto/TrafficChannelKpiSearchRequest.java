package com.techasians.alertservice.dto;

public class TrafficChannelKpiSearchRequest {

    private Integer channelId;

    private String keyword;

    private int page = 0;

    private int size = 10;

    private String sort;

    public Integer getChannelId() {
        return channelId;
    }

    public TrafficChannelKpiSearchRequest setChannelId(Integer channelId) {
        this.channelId = channelId;
        return this;
    }

    public String getKeyword() {
        return keyword;
    }

    public TrafficChannelKpiSearchRequest setKeyword(String keyword) {
        this.keyword = keyword;
        return this;
    }

    public int getPage() {
        return page;
    }

    public TrafficChannelKpiSearchRequest setPage(int page) {
        this.page = page;
        return this;
    }

    public int getSize() {
        return size;
    }

    public TrafficChannelKpiSearchRequest setSize(int size) {
        this.size = size;
        return this;
    }

    public String getSort() {
        return sort;
    }

    public TrafficChannelKpiSearchRequest setSort(String sort) {
        this.sort = sort;
        return this;
    }
}
