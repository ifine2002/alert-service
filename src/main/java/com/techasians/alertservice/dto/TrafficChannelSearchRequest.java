package com.techasians.alertservice.dto;

import java.util.List;

public class TrafficChannelSearchRequest {

    private List<Long> regionIds;

    private int page = 0;

    private int size = 10;

    public List<Long> getRegionIds() {
        return regionIds;
    }

    public TrafficChannelSearchRequest setRegionIds(List<Long> regionIds) {
        this.regionIds = regionIds;
        return this;
    }

    public int getPage() {
        return page;
    }

    public TrafficChannelSearchRequest setPage(int page) {
        this.page = page;
        return this;
    }

    public int getSize() {
        return size;
    }

    public TrafficChannelSearchRequest setSize(int size) {
        this.size = size;
        return this;
    }
}
