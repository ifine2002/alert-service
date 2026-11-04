package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.RptOutputSourceFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RptOutputSourceFileRepository extends JpaRepository<RptOutputSourceFile, Long> {
    List<RptOutputSourceFile> findByOutputFileId(Long outputFileId);
}
