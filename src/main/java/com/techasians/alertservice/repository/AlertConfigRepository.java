package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.AlertConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertConfigRepository extends JpaRepository<AlertConfig, Integer> {

       // Tìm cấu hình theo ID và chưa bị xóa mềm, dùng EntityGraph để load luôn
       // Category và Metric (Tránh N+1 Query)
       @EntityGraph(attributePaths = { "serviceQualityMetric", "serviceQualityCategory" })
       Optional<AlertConfig> findByIdAndDeletedFalse(Integer id);

       // Kiểm tra xem cấu hình cho category và metric này đã tồn tại chưa (chưa bị
       // xóa)
       boolean existsByServiceQualityCategoryIdAndServiceQualityMetricIdAndDeletedFalse(Integer categoryId,
                     Integer metricId);

       // Lấy danh sách các cặp CategoryID và MetricID đã có cấu hình (để tránh N+1
       // Query)
       @Query("SELECT a.serviceQualityCategory.id, a.serviceQualityMetric.id FROM AlertConfig a WHERE a.deleted = false")
       java.util.List<Object[]> findAllActiveConfigKeys();

       // Tìm kiếm danh sách cấu hình cảnh báo theo các tiêu chí lọc và phân trang.
       // Dùng JOIN FETCH để load Category và Metric trong 1 câu SQL duy nhất.
       // Cần khai báo countQuery riêng khi dùng JOIN FETCH với Pageable.
       @Query(value = "SELECT ac FROM AlertConfig ac " +
                     "JOIN FETCH ac.serviceQualityMetric m " +
                     "JOIN FETCH ac.serviceQualityCategory c " +
                     "WHERE ac.deleted = false " +
                     "AND (:categoryId IS NULL OR c.id = :categoryId) " +
                     "AND (:metricName IS NULL OR LOWER(m.name) LIKE LOWER(CONCAT('%', :metricName, '%')) ESCAPE '!') "
                     +
                     "AND (:createdBy IS NULL OR LOWER(ac.createdBy) LIKE LOWER(CONCAT('%', :createdBy, '%')) ESCAPE '!')", countQuery = "SELECT count(ac) FROM AlertConfig ac "
                                   +
                                   "JOIN ac.serviceQualityMetric m " +
                                   "JOIN ac.serviceQualityCategory c " +
                                   "WHERE ac.deleted = false " +
                                   "AND (:categoryId IS NULL OR c.id = :categoryId) " +
                                   "AND (:metricName IS NULL OR LOWER(m.name) LIKE LOWER(CONCAT('%', :metricName, '%')) ESCAPE '!') "
                                   +
                                   "AND (:createdBy IS NULL OR LOWER(ac.createdBy) LIKE LOWER(CONCAT('%', :createdBy, '%')) ESCAPE '!')")
       Page<AlertConfig> search(
                     @Param("categoryId") Integer categoryId,
                     @Param("metricName") String metricName,
                     @Param("createdBy") String createdBy,
                     Pageable pageable);

       @Query("SELECT c FROM AlertConfig c JOIN FETCH c.serviceQualityMetric m WHERE m.status = 1 AND c.status = 1 AND c.deletedAt IS NULL")
       List<AlertConfig> findAllActiveConfigs();
}