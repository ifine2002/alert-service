package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.TrafficMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TrafficMetricRepository extends JpaRepository<TrafficMetric, Integer> {
}
