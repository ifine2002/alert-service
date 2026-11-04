package com.techasians.alertservice.controller;

import com.techasians.alertservice.dto.*;
import com.techasians.alertservice.service.AlertGroupService;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alert-groups")
public class AlertGroupController {

    private final AlertGroupService alertGroupService;

    public AlertGroupController(AlertGroupService alertGroupService) {
        this.alertGroupService = alertGroupService;
    }

    @PostMapping
    public ApiResponse<AlertGroupResponse> create(@Validated @RequestBody AlertGroupRequest request) {
        try {
            AlertGroupResponse response = alertGroupService.create(request);
            return ApiResponse.success(response);
        } catch (Exception e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<AlertGroupResponse> update(@PathVariable Integer id, @Validated @RequestBody AlertGroupRequest request) {
        try {
            AlertGroupResponse response = alertGroupService.update(id, request);
            return ApiResponse.success(response);
        } catch (Exception e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        try {
            alertGroupService.delete(id);
            return ApiResponse.success(null);
        } catch (Exception e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<AlertGroupResponse> getById(@PathVariable Integer id) {
        try {
            AlertGroupResponse response = alertGroupService.getById(id);
            return ApiResponse.success(response);
        } catch (Exception e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/search")
    public ApiResponse<Page<AlertGroupListResponse>> search(@RequestBody AlertGroupSearchRequest request) {
        try {
            Page<AlertGroupListResponse> response = alertGroupService.search(request);
            return ApiResponse.success(response);
        } catch (Exception e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
