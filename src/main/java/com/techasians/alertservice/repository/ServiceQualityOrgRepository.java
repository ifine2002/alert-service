package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.ServiceQualityOrg;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceQualityOrgRepository extends JpaRepository<ServiceQualityOrg, Long> {

    /**
     * [BATCH] Lấy tất cả ServiceQualityOrg theo nhiều id cùng lúc.
     * Dùng trong KpiCalculationService để build Map: orgId → orgName
     * (thay thế cho việc JOIN FETCH trong ServiceQualityMetricOrg).
     *
     * Ví dụ: có 10 cặp (metric, org) với 4 org khác nhau → 1 query thay vì 4 query.
     */
    @Query("SELECT o FROM ServiceQualityOrg o WHERE o.id IN :orgIds")
    List<ServiceQualityOrg> findAllByIdIn(@Param("orgIds") List<Long> orgIds);
}
