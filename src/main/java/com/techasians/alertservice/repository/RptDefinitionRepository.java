package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.RptDefinition;
import com.techasians.alertservice.enums.RptDirection;
import com.techasians.alertservice.enums.RptNodeType;
import com.techasians.alertservice.enums.RptSourceMode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RptDefinitionRepository extends JpaRepository<RptDefinition, Long> {
    List<RptDefinition> findByDirectionAndNodeTypeAndStatusOrderBySortOrder(
            RptDirection direction, RptNodeType nodeType, Integer status);
    List<RptDefinition> findByDirectionAndNodeTypeAndStatusOrderBySortOrderAscIdAsc(
            RptDirection direction, RptNodeType nodeType, Integer status);
    List<RptDefinition> findByNodeTypeAndStatusOrderByDirectionAscSortOrderAsc(
            RptNodeType nodeType, Integer status);
    List<RptDefinition> findByDirectionAndNodeTypeAndSourceModeAndStatusOrderBySortOrderAscIdAsc(
            RptDirection direction, RptNodeType nodeType, RptSourceMode sourceMode, Integer status);
}
