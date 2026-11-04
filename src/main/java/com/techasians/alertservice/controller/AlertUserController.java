package com.techasians.alertservice.controller;

import com.techasians.alertservice.dto.AlertUserRequestDTO;
import com.techasians.alertservice.dto.AlertUserDTO;
import com.techasians.alertservice.dto.ApiResponse;
import com.techasians.alertservice.service.AlertUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/alert-users")
@Validated // Bật Validation cho Controller
public class AlertUserController {

    private final AlertUserService alertUserService;

    public AlertUserController(AlertUserService alertUserService) {
        this.alertUserService = alertUserService;
    }

    // 1. API Lấy danh sách (Có tìm kiếm và phân trang)
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AlertUserDTO>>> searchAlertUsers(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<AlertUserDTO> result = alertUserService.searchAlertUsers(keyword, pageable);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // 2. API Thêm mới
    @PostMapping
    public ResponseEntity<ApiResponse<AlertUserDTO>> createAlertUser(@Valid @RequestBody AlertUserRequestDTO requestDTO) {
        // @Valid giúp tự động kiểm tra xem requestDTO có đúng định dạng không (VD: email đúng chuẩn, không bỏ trống)
        AlertUserDTO createdUser = alertUserService.createAlertUser(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(201,"Thêm mới người dùng thành công", createdUser ));
    }

    // 3. API Cập nhật (Sửa)
    @PutMapping("/{id}")
    public ApiResponse<AlertUserDTO> updateAlertUser(
            @PathVariable Long id, 
            @Valid @RequestBody AlertUserRequestDTO requestDTO) {
        AlertUserDTO updatedUser = alertUserService.updateAlertUser(id, requestDTO);
        return ApiResponse.success("Cập nhật thông tin thành công", updatedUser);
    }

    // 4. API Xóa mềm (Không xóa khỏi DB mà chỉ đổi cờ deleted = true)
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteAlertUser(@PathVariable Long id) {
        alertUserService.deleteAlertUser(id);
        return ApiResponse.success("Xóa người dùng thành công", null);
    }

    // 4. API Xóa mềm (Không xóa khỏi DB mà chỉ đổi cờ deleted = true)
    @GetMapping("/{id}")
    public ApiResponse<AlertUserDTO> getDetail(@PathVariable Long id) {
        AlertUserDTO responseDTO = alertUserService.getDetail(id);
        return ApiResponse.success("Xóa người dùng thành công", responseDTO);
    }
}
