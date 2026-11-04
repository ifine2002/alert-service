package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.AlertConfigHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertConfigHistoryRepository extends JpaRepository<AlertConfigHistory, Long> {
}
