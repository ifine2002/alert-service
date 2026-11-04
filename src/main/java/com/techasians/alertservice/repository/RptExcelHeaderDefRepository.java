package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.RptExcelHeaderDef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RptExcelHeaderDefRepository extends JpaRepository<RptExcelHeaderDef, Long> {

    // Lấy toàn bộ header bắt buộc của các sheet trong một query để tránh N+1.
    // Sắp xếp theo sheet và ID để thứ tự thông báo lỗi luôn ổn định.
    List<RptExcelHeaderDef> findBySheetDefIdInAndStatusOrderBySheetDefIdAscIdAsc(
            List<Long> sheetDefIds,
            Integer status
    );
}
