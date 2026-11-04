package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.RptOutputSourceMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RptOutputSourceMapRepository extends JpaRepository<RptOutputSourceMap, Long> {

    // Lấy tất cả source của một block theo thứ tự sort_order
    List<RptOutputSourceMap> findByOutputBlockIdAndStatusOrderBySortOrder(Long outputBlockId, Integer status);

    @Query("SELECT DISTINCT block.outputDefinitionId, sourceMap.sourceDefinitionId " +
            "FROM RptOutputSourceMap sourceMap " +
            "JOIN RptOutputBlockDef block ON block.id = sourceMap.outputBlockId " +
            "WHERE block.outputDefinitionId IN :outputDefinitionIds " +
            "AND sourceMap.status = 1 AND block.status = 1")
    List<Object[]> findActiveSourceDefinitionIdsByOutputDefinitionIds(
            @Param("outputDefinitionIds") Collection<Long> outputDefinitionIds);

    @Query("SELECT DISTINCT block.outputDefinitionId " +
            "FROM RptOutputSourceMap sourceMap " +
            "JOIN RptOutputBlockDef block ON block.id = sourceMap.outputBlockId " +
            "WHERE sourceMap.sourceDefinitionId = :sourceDefinitionId " +
            "AND sourceMap.status = 1 AND block.status = 1")
    List<Long> findActiveOutputDefinitionIdsBySourceDefinitionId(
            @Param("sourceDefinitionId") Long sourceDefinitionId);
}
