package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.RptSectionDef;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RptSectionDefRepository extends JpaRepository<RptSectionDef, Long> {
    List<RptSectionDef> findBySourceDefinitionIdAndStatus(Long sourceDefinitionId, Integer status);
    Optional<RptSectionDef> findBySourceDefinitionIdAndSectionName(Long sourceDefinitionId, String sectionName);
}
