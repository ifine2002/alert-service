package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.RptExcelSheetDef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RptExcelSheetDefRepository extends JpaRepository<RptExcelSheetDef, Long> {

    // Lấy toàn bộ sheet bắt buộc (status = 1) của một definition,
    // sắp xếp theo sort_order để thông báo lỗi đúng thứ tự cấu hình.
    // Mọi dòng status = 1 đều là sheet bắt buộc — không có cột required riêng.
    List<RptExcelSheetDef> findByDefinitionIdAndStatusOrderBySortOrderAsc(Long definitionId, Integer status);
}
