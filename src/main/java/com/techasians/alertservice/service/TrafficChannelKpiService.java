package com.techasians.alertservice.service;

import com.techasians.alertservice.dto.TrafficChannelKpiRequest;
import com.techasians.alertservice.dto.TrafficChannelKpiResponse;
import com.techasians.alertservice.dto.TrafficChannelKpiSearchRequest;
import org.springframework.data.domain.Page;

public interface TrafficChannelKpiService {
    
    Integer create(TrafficChannelKpiRequest request, String createdBy);

    Integer update(Integer id, TrafficChannelKpiRequest request, String updatedBy);

    void delete(Integer id, String deletedBy);

    TrafficChannelKpiResponse getById(Integer id);

    Page<TrafficChannelKpiResponse> search(TrafficChannelKpiSearchRequest request);
}
