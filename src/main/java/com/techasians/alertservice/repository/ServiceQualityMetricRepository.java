package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.ServiceQualityMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServiceQualityMetricRepository extends JpaRepository<ServiceQualityMetric, Integer> {

    // Tìm Metric theo tên và Category cha (không phân biệt hoa/thường) — dùng khi import Excel
    Optional<ServiceQualityMetric> findByCategoryIdAndNameIgnoreCase(Integer categoryId, String name);
}

