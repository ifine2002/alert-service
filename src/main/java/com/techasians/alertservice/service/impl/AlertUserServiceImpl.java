package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.dto.AlertUserRequestDTO;
import com.techasians.alertservice.dto.AlertUserDTO;
import com.techasians.alertservice.entity.AlertUser;
import com.techasians.alertservice.exception.DuplicateResourceException;
import com.techasians.alertservice.exception.ResourceNotFoundException;
import com.techasians.alertservice.repository.AlertUserRepository;
import com.techasians.alertservice.service.AlertUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class AlertUserServiceImpl implements AlertUserService {
    @Override
    public AlertUserDTO getDetail(Long id) {
        AlertUser alertUser = this.getAlertUser(id);

        return mapToResponse(alertUser);
    }

    private final AlertUserRepository alertUserRepository;

    // Constructor Injection (thay vì dùng @Autowired trực tiếp)
    public AlertUserServiceImpl(AlertUserRepository alertUserRepository) {
        this.alertUserRepository = alertUserRepository;
    }

    @Override
    public Page<AlertUserDTO> searchAlertUsers(String keyword, Pageable pageable) {
        keyword = escapeSqlWildcards(keyword);
        return alertUserRepository.searchUsers(keyword, pageable);
    }

    @Override
    public AlertUserDTO createAlertUser(AlertUserRequestDTO requestDTO) {
        // 1. Kiểm tra Email xem có bị trùng không
        if (alertUserRepository.existsByEmail(requestDTO.getEmail())) {
            throw new DuplicateResourceException("Email " + requestDTO.getEmail() + " đã tồn tại trong hệ thống.");
        }

        // 2. Kiểm tra Số điện thoại xem có bị trùng không
        if (alertUserRepository.existsByPhone(requestDTO.getPhone())) {
            throw new DuplicateResourceException(
                    "Số điện thoại " + requestDTO.getPhone() + " đã tồn tại trong hệ thống.");
        }

        // 3. Khởi tạo Entity để lưu xuống DB
        AlertUser newUser = new AlertUser()
                .setFullName(requestDTO.getFullName())
                .setEmail(requestDTO.getEmail())
                .setPhone(requestDTO.getPhone())
                .setPosition(requestDTO.getPosition());

        // 4. Lưu vào CSDL
        AlertUser savedUser = alertUserRepository.save(newUser);
        return mapToResponse(savedUser);
    }

    @Override
    public AlertUserDTO updateAlertUser(Long id, AlertUserRequestDTO requestDTO) {
        // 1. Tìm user cần sửa
        AlertUser existingUser = this.getAlertUser(id);

        // 2. Kiểm tra trùng Email (nếu email truyền vào khác email hiện tại)
        if (!existingUser.getEmail().equals(requestDTO.getEmail())
                && alertUserRepository.existsByEmail(requestDTO.getEmail())) {
            throw new DuplicateResourceException("Email " + requestDTO.getEmail() + " đã tồn tại trong hệ thống.");
        }

        // 3. Kiểm tra trùng Số điện thoại (nếu phone truyền vào khác phone hiện tại)
        if (!existingUser.getPhone().equals(requestDTO.getPhone())
                && alertUserRepository.existsByPhone(requestDTO.getPhone())) {
            throw new DuplicateResourceException(
                    "Số điện thoại " + requestDTO.getPhone() + " đã tồn tại trong hệ thống.");
        }

        // 4. Cập nhật thông vị trí
        existingUser.setFullName(requestDTO.getFullName())
                .setEmail(requestDTO.getEmail())
                .setPhone(requestDTO.getPhone())
                .setPosition(requestDTO.getPosition());

        // 5. Lưu lại
        AlertUser updatedUser = alertUserRepository.save(existingUser);
        return mapToResponse(updatedUser);
    }

    @Override
    public void deleteAlertUser(Long id) {
        // Tìm user
        AlertUser existingUser = this.getAlertUser(id);

        // Đánh dấu là đã xóa (Xóa mềm - Soft Delete) thay vì xóa hẳn khỏi CSDL
        long timestamp = System.currentTimeMillis();
        existingUser.setEmail(existingUser.getEmail() + "_deleted_" + timestamp);
        existingUser.setPhone(existingUser.getPhone() + "_deleted_" + timestamp);
        existingUser.setDeleted(true);

        alertUserRepository.save(existingUser);
    }

    // Phương thức phụ: Hàm giúp map dữ liệu từ Entity (AlertUser) -> DTO
    // (AlertUserResponseDTO)
    private AlertUserDTO mapToResponse(AlertUser entity) {
        return new AlertUserDTO()
                .setId(entity.getId())
                .setFullName(entity.getFullName())
                .setEmail(entity.getEmail())
                .setPhone(entity.getPhone())
                .setPosition(entity.getPosition());
    }

    private AlertUser getAlertUser(Long id) {
        return alertUserRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Không tìm thấy người dùng nhận cảnh báo với ID: " + id));
    }

    private void validateRequest(AlertUserRequestDTO request) {
        if (request.getEmail() != null) {
            request.setEmail(request.getEmail().trim());
        }

        if (request.getPhone() != null) {
            request.setEmail(request.getPhone().trim());
        }

        if (request.getFullName() != null) {
            request.setEmail(request.getFullName().trim());
        }

        if (request.getPosition() != null) {
            request.setEmail(request.getPosition().trim());
        }
    }

    /**
     * Xử lý chuỗi đầu vào để an toàn khi dùng trong câu SQL LIKE (với ESCAPE '!').
     * Thay thế các ký tự '%', '_' và '!' thành dạng literal.
     */
    private String escapeSqlWildcards(String input) {
        if (input == null) {
            return null;
        }
        return input.trim()
                .replace("!", "!!") // Phải escape chính ký tự '!' trước
                .replace("%", "!%")
                .replace("_", "!_");
    }
}
