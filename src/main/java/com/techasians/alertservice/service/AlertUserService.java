package com.techasians.alertservice.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.techasians.alertservice.dto.AlertUserRequestDTO;
import com.techasians.alertservice.dto.AlertUserDTO;

public interface AlertUserService {

    // Tìm kiếm danh sách người dùng nhận cảnh báo, có phân trang
    Page<AlertUserDTO> searchAlertUsers(String keyword, Pageable pageable);

    // Thêm mới người dùng
    AlertUserDTO createAlertUser(AlertUserRequestDTO requestDTO);

    // Cập nhật thông tin người dùng
    AlertUserDTO updateAlertUser(Long id, AlertUserRequestDTO requestDTO);

    // Xóa mềm (chuyển deleted = true) người dùng
    void deleteAlertUser(Long id);

    AlertUserDTO getDetail(Long id);
}
