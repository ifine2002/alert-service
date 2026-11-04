package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.TrafficEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrafficEventRepository extends JpaRepository<TrafficEvent, Integer> {

    // Dùng trong import event để build map theo tên sự kiện
    List<TrafficEvent> findByDeletedAtIsNull();

    // Dùng trong buildEventMap() của import — thay thế findByStatus()
    List<TrafficEvent> findByStatusAndDeletedAtIsNull(Integer status);

    // Tìm theo id chưa bị xóa mềm — dùng cho getById, update, delete
    Optional<TrafficEvent> findByIdAndDeletedAtIsNull(Integer id);

    // Kiểm tra tên trùng khi tạo mới
    Optional<TrafficEvent> findByNameAndDeletedAtIsNull(String name);

    // Kiểm tra tên trùng khi cập nhật (loại trừ bản ghi hiện tại)
    Optional<TrafficEvent> findByNameAndDeletedAtIsNullAndIdNot(String name, Integer excludeId);

    // Tìm theo tên + loại sự kiện — dùng trong import để phân biệt định kỳ / phát sinh
    Optional<TrafficEvent> findByNameAndEventTypeAndDeletedAtIsNull(String name, Integer eventType);

    // Search phân trang với các filter tùy chọn
    // Dùng @Query để xử lý filter linh hoạt (tránh derived query quá dài)
    @Query("SELECT e FROM TrafficEvent e " +
           "WHERE e.deletedAt IS NULL " +
           "AND (:name IS NULL OR LOWER(e.name) LIKE LOWER(CONCAT('%', :name, '%'))) " +
           "AND (:eventType IS NULL OR e.eventType = :eventType) " +
           "AND (:status IS NULL OR e.status = :status)")
    Page<TrafficEvent> search(
            @Param("name") String name,
            @Param("eventType") Integer eventType,
            @Param("status") Integer status,
            Pageable pageable);
}
