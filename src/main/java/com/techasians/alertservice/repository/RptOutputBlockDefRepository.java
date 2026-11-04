package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.RptOutputBlockDef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RptOutputBlockDefRepository extends JpaRepository<RptOutputBlockDef, Long> {

    // Lấy tất cả block theo thứ tự xử lý cho một file output
    List<RptOutputBlockDef> findByOutputDefinitionIdAndStatusOrderBySortOrder(
            Long outputDefinitionId, Integer status);

}
