package com.techasians.alertservice.dto;

public class TrafficRegionResponse {
    private Long id;
    private String name;

    public TrafficRegionResponse(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public TrafficRegionResponse setId(Long id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public TrafficRegionResponse setName(String name) {
        this.name = name;
        return this;
    }
}
