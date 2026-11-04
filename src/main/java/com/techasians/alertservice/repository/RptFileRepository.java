package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.RptFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RptFileRepository extends JpaRepository<RptFile, Long> {
    List<RptFile> findByReportInstanceIdOrderByVersionNoDesc(Long reportInstanceId);
    RptFile findTopByReportInstanceIdOrderByVersionNoDesc(Long reportInstanceId);
}
