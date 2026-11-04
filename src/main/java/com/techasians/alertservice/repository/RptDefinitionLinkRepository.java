package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.RptDefinitionLink;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RptDefinitionLinkRepository extends JpaRepository<RptDefinitionLink, Long> {
    List<RptDefinitionLink> findByParentDefinitionIdAndStatusOrderBySortOrder(Long parentDefinitionId, Integer status);
    List<RptDefinitionLink> findByParentDefinitionIdInAndStatusOrderByParentDefinitionIdAscSortOrderAsc(
            Collection<Long> parentDefinitionIds, Integer status);
    List<RptDefinitionLink> findByChildDefinitionIdInAndStatusOrderByChildDefinitionIdAscSortOrderAsc(
            Collection<Long> childDefinitionIds, Integer status);
    Optional<RptDefinitionLink> findByParentDefinitionIdAndChildDefinitionId(Long parentDefinitionId, Long childDefinitionId);
}
