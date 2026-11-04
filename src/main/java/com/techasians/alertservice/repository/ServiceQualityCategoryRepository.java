package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.ServiceQualityCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServiceQualityCategoryRepository extends JpaRepository<ServiceQualityCategory, Integer> {

    // Tìm Category theo tên (không phân biệt hoa/thường) — dùng khi import Excel
    Optional<ServiceQualityCategory> findByNameIgnoreCase(String name);
}

