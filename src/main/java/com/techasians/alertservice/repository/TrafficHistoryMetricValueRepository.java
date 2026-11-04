package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.TrafficHistoryMetricValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TrafficHistoryMetricValueRepository extends JpaRepository<TrafficHistoryMetricValue, Long> {
}
