package com.techasians.alertservice.service;

import com.techasians.alertservice.dto.AlertGroupListResponse;
import com.techasians.alertservice.dto.AlertGroupRequest;
import com.techasians.alertservice.dto.AlertGroupResponse;
import com.techasians.alertservice.dto.AlertGroupSearchRequest;
import com.techasians.alertservice.entity.AlertGroup;
import com.techasians.alertservice.enums.TargetType;
import org.springframework.data.domain.Page;

import java.util.List;

public interface AlertGroupService {

    AlertGroupResponse create(AlertGroupRequest request);

    AlertGroupResponse update(Integer id, AlertGroupRequest request);

    void delete(Integer id);

    AlertGroupResponse getById(Integer id);

    Page<AlertGroupListResponse> search(AlertGroupSearchRequest request);

    List<AlertGroup> findGroupsByLevelAndTarget(Integer levelId, Long targetId, TargetType targetType);
}
