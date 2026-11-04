package com.techasians.alertservice.entity;

import javax.persistence.*;

@Entity
@Table(name = "traffic_regions")
public class TrafficRegion extends BaseAuditEntity<TrafficRegion> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name = "";

    @Column(name = "status", nullable = false)
    private Integer status = 1;

    public Long getId() {
        return id;
    }

    public TrafficRegion setId(Long id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public TrafficRegion setName(String name) {
        this.name = name;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public TrafficRegion setStatus(Integer status) {
        this.status = status;
        return this;
    }
}
