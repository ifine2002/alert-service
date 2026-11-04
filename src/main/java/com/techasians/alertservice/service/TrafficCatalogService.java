package com.techasians.alertservice.service;

import com.techasians.alertservice.dto.TrafficChannelResponse;
import com.techasians.alertservice.dto.TrafficChannelSearchRequest;
import com.techasians.alertservice.dto.TrafficRegionResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public interface TrafficCatalogService {
    List<TrafficRegionResponse> getAllActiveRegions();
    Page<TrafficChannelResponse> searchChannels(TrafficChannelSearchRequest request);
}
