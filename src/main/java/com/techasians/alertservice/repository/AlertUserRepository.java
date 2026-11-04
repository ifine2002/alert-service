package com.techasians.alertservice.repository;

import com.techasians.alertservice.dto.AlertUserDTO;
import com.techasians.alertservice.entity.AlertUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AlertUserRepository extends JpaRepository<AlertUser, Long> {

    // Tìm kiếm theo từ khóa (họ tên hoặc email)
    @Query("SELECT new com.techasians.alertservice.dto.AlertUserDTO(" +
            " u.id, u.fullName, u.email, u.phone, u.position " +
            ") " +
            "FROM AlertUser u WHERE u.deleted = false AND " +
            "(:keyword IS NULL " +
            "OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!' " +
            "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!' " +
            "OR LOWER(u.phone) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!')")
    Page<AlertUserDTO> searchUsers(@Param("keyword") String keyword, Pageable pageable);

    // Kiểm tra xem email đã tồn tại chưa
    boolean existsByEmail(String email);

    // Kiểm tra xem số điện thoại đã tồn tại chưa
    boolean existsByPhone(String phone);

    // Lấy user theo ID và chưa bị xóa
    Optional<AlertUser> findByIdAndDeletedFalse(Long id);
}
