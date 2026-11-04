package com.techasians.alertservice.dto;

public class TrafficChannelResponse {
    private Integer id;
    private String name;

    public TrafficChannelResponse(Integer id, String name) {
        this.id = id;
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public TrafficChannelResponse setId(Integer id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public TrafficChannelResponse setName(String name) {
        this.name = name;
        return this;
    }
}
