package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.dto.AlertGroupListResponse;
import com.techasians.alertservice.dto.AlertGroupRequest;
import com.techasians.alertservice.dto.AlertGroupResponse;
import com.techasians.alertservice.dto.AlertGroupSearchRequest;
import com.techasians.alertservice.dto.AlertGroupTargetDTO;
import com.techasians.alertservice.dto.AlertUserDTO;
import com.techasians.alertservice.entity.*;
import com.techasians.alertservice.enums.TargetType;
import com.techasians.alertservice.repository.*;
import com.techasians.alertservice.service.AlertGroupService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AlertGroupServiceImpl implements AlertGroupService {

    private final AlertGroupRepository alertGroupRepository;
    private final AlertGroupMemberRepository alertGroupMemberRepository;
    private final ServiceQualityOrgRepository orgRepository;
    private final ServiceQualityCategoryRepository categoryRepository;
    private final AlertUserRepository alertUserRepository;

    private final AlertGroupTargetRepository alertGroupTargetRepository;

    public AlertGroupServiceImpl(AlertGroupRepository alertGroupRepository,
                                 AlertGroupMemberRepository alertGroupMemberRepository,
                                 ServiceQualityOrgRepository orgRepository,
                                 ServiceQualityCategoryRepository categoryRepository,
                                 AlertUserRepository alertUserRepository,
                                 AlertGroupTargetRepository alertGroupTargetRepository) {
        this.alertGroupRepository = alertGroupRepository;
        this.alertGroupMemberRepository = alertGroupMemberRepository;
        this.orgRepository = orgRepository;
        this.categoryRepository = categoryRepository;
        this.alertUserRepository = alertUserRepository;
        this.alertGroupTargetRepository = alertGroupTargetRepository;
    }

    @Override
    @Transactional
    public AlertGroupResponse create(AlertGroupRequest request) {
        validateCodeUniqueness(request.getCode(), null);

        AlertGroup alertGroup = new AlertGroup();
        mapRequestToEntity(request, alertGroup);

        alertGroup = alertGroupRepository.save(alertGroup);

        saveGroupTargets(alertGroup, request.getOptions());
        saveGroupMembers(alertGroup, request.getAlertUserIds());

        return getById(alertGroup.getId());
    }

    @Override
    @Transactional
    public AlertGroupResponse update(Integer id, AlertGroupRequest request) {
        AlertGroup alertGroup = getAlertGroupOrThrow(id);

        validateCodeUniqueness(request.getCode(), id);

        mapRequestToEntity(request, alertGroup);
        alertGroup = alertGroupRepository.save(alertGroup);

        alertGroupTargetRepository.deleteByAlertGroupId(alertGroup.getId());
        saveGroupTargets(alertGroup, request.getOptions());

        alertGroupMemberRepository.deleteByAlertGroupId(alertGroup.getId());
        saveGroupMembers(alertGroup, request.getAlertUserIds());

        return getById(alertGroup.getId());
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        AlertGroup alertGroup = getAlertGroupOrThrow(id);
        alertGroup.setDeletedAt(LocalDateTime.now());
        alertGroup.setStatus(0);
        alertGroup.setDeletedBy("SYSTEM"); // or from SecurityContext

        // Cập nhật lại code để tránh lỗi unique constraint khi tạo mới lại cùng code
        alertGroup.setCode("deleted_" + alertGroup.getCode() + "_" + System.currentTimeMillis());

        alertGroupRepository.save(alertGroup);
    }

    @Override
    public AlertGroupResponse getById(Integer id) {
        AlertGroup alertGroup = getAlertGroupOrThrow(id);
        return mapEntityToResponse(alertGroup);
    }

    @Override
    public Page<AlertGroupListResponse> search(AlertGroupSearchRequest request) {
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
        Page<com.techasians.alertservice.dto.AlertGroupListProjection> projectionPage = alertGroupRepository.searchAlertGroupsNative(
                request.getKeyword(),
                request.getStatus(),
                pageable
        );

        return projectionPage.map(proj -> {
            AlertGroupListResponse response = new AlertGroupListResponse();
            response.setId(proj.getId());
            response.setCode(proj.getCode());
            response.setName(proj.getName());
            response.setStatus(proj.getStatus());
            response.setStatusName(proj.getStatus() != null && proj.getStatus() == 1 ? "Đang hoạt động" : "Tạm dừng");
            response.setDescription(proj.getDescription());
            response.setTargetNames(proj.getTargetNames());
            response.setMemberCount(proj.getMemberCount());
            return response;
        });
    }

    private AlertGroup getAlertGroupOrThrow(Integer id) {
        return alertGroupRepository.findById(id)
                .filter(group -> group.getDeletedAt() == null)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy AlertGroup với id: " + id));
    }

    private void validateCodeUniqueness(String code, Integer id) {
        if (id == null) {
            if (alertGroupRepository.existsByCodeAndDeletedAtIsNull(code)) {
                throw new RuntimeException("Mã AlertGroup đã tồn tại");
            }
        } else {
            if (alertGroupRepository.existsByCodeAndIdNotAndDeletedAtIsNull(code, id)) {
                throw new RuntimeException("Mã AlertGroup đã tồn tại");
            }
        }
    }

    private void mapRequestToEntity(AlertGroupRequest request, AlertGroup alertGroup) {
        alertGroup.setCode(request.getCode());
        alertGroup.setName(request.getName());
        alertGroup.setStatus(request.getStatus());
        alertGroup.setDescription(request.getDescription());

    }

    private void saveGroupTargets(AlertGroup alertGroup, List<AlertGroupTargetDTO> options) {
        if (options == null || options.isEmpty()) return;

        List<Long> orgIds = options.stream()
                .filter(opt -> opt.getType() == TargetType.ORG)
                .map(AlertGroupTargetDTO::getId)
                .collect(Collectors.toList());

        List<Integer> categoryIds = options.stream()
                .filter(opt -> opt.getType() == TargetType.CATEGORY)
                .map(opt -> opt.getId().intValue())
                .collect(Collectors.toList());

        if (!orgIds.isEmpty()) {
            List<ServiceQualityOrg> orgs = orgRepository.findAllById(orgIds);
            if (orgs.size() != orgIds.size()) {
                throw new RuntimeException("Một số đơn vị (ORG) không tồn tại");
            }
        }

        if (!categoryIds.isEmpty()) {
            List<ServiceQualityCategory> categories = categoryRepository.findAllById(categoryIds);
            if (categories.size() != categoryIds.size()) {
                throw new RuntimeException("Một số dịch vụ (CATEGORY) không tồn tại");
            }
        }

        List<AlertGroupTarget> targets = options.stream().map(opt -> {
            AlertGroupTarget t = new AlertGroupTarget();
            t.setAlertGroup(alertGroup);
            t.setTargetId(opt.getId());
            t.setTargetType(opt.getType());
            t.setCreatedBy("SYSTEM");
            return t;
        }).collect(Collectors.toList());
        alertGroupTargetRepository.saveAll(targets);
    }

    private void saveGroupMembers(AlertGroup alertGroup, List<Long> alertUserIds) {
        if (alertUserIds == null || alertUserIds.isEmpty()) return;

        List<AlertUser> users = alertUserRepository.findAllById(alertUserIds);
        if (users.size() != alertUserIds.size()) {
            throw new RuntimeException("Một số người dùng không tồn tại");
        }

        List<AlertGroupMember> members = new ArrayList<>();
        for (AlertUser user : users) {
            AlertGroupMember member = new AlertGroupMember();
            member.setAlertGroup(alertGroup);
            member.setAlertUser(user);
            member.setCreatedBy("SYSTEM"); // or from SecurityContext
            members.add(member);
        }
        alertGroupMemberRepository.saveAll(members);
    }

    private AlertGroupResponse mapEntityToResponse(AlertGroup alertGroup) {
        AlertGroupResponse response = new AlertGroupResponse();
        response.setId(alertGroup.getId());
        response.setCode(alertGroup.getCode());
        response.setName(alertGroup.getName());
        response.setStatus(alertGroup.getStatus());
        response.setDescription(alertGroup.getDescription());

        List<AlertGroupTarget> targets = alertGroupTargetRepository.findByAlertGroupId(alertGroup.getId());

        List<Long> orgIds = targets.stream()
                .filter(t -> t.getTargetType() == TargetType.ORG)
                .map(AlertGroupTarget::getTargetId)
                .collect(Collectors.toList());

        List<Integer> categoryIds = targets.stream()
                .filter(t -> t.getTargetType() == TargetType.CATEGORY)
                .map(t -> t.getTargetId().intValue())
                .collect(Collectors.toList());

        java.util.Map<Long, String> orgNameMap = new java.util.HashMap<>();
        if (!orgIds.isEmpty()) {
            orgRepository.findAllById(orgIds).forEach(org -> orgNameMap.put(org.getId(), org.getName()));
        }

        java.util.Map<Integer, String> categoryNameMap = new java.util.HashMap<>();
        if (!categoryIds.isEmpty()) {
            categoryRepository.findAllById(categoryIds).forEach(cat -> categoryNameMap.put(cat.getId(), cat.getName()));
        }

        List<AlertGroupTargetDTO> options = targets.stream().map(t -> {
            AlertGroupTargetDTO dto = new AlertGroupTargetDTO();
            dto.setId(t.getTargetId());
            dto.setType(t.getTargetType());
            if (t.getTargetType() == TargetType.ORG) {
                dto.setName(orgNameMap.get(t.getTargetId()));
            } else if (t.getTargetType() == TargetType.CATEGORY) {
                dto.setName(categoryNameMap.get(t.getTargetId().intValue()));
            }
            return dto;
        }).collect(Collectors.toList());
        response.setOptions(options);

        List<AlertGroupMember> members = alertGroupMemberRepository.findByAlertGroupId(alertGroup.getId());
        List<AlertUserDTO> userDTOs = members.stream()
                .map(this::mapUserToDTO)
                .collect(Collectors.toList());
        response.setAlertUsers(userDTOs);

        return response;
    }

    private AlertUserDTO mapUserToDTO(AlertGroupMember member) {
        com.techasians.alertservice.entity.AlertUser user = member.getAlertUser();
        return new AlertUserDTO(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(), user.getPosition());
    }

    @Override
    public List<AlertGroup> findGroupsByLevelAndTarget(Integer levelId, Long targetId, TargetType targetType) {
        return alertGroupRepository.findGroupsByLevelAndTarget(levelId, targetId, targetType);
    }
}
