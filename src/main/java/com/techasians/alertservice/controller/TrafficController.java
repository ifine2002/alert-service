package com.techasians.alertservice.controller;

import com.techasians.alertservice.dto.ApiResponse;
import com.techasians.alertservice.dto.TrafficChannelResponse;
import com.techasians.alertservice.dto.TrafficChannelSearchRequest;
import com.techasians.alertservice.dto.TrafficRegionResponse;
import com.techasians.alertservice.service.TrafficCatalogService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/traffic")
public class TrafficController {

    private final TrafficCatalogService trafficCatalogService;

    public TrafficController(TrafficCatalogService trafficCatalogService) {
        this.trafficCatalogService = trafficCatalogService;
    }

    @GetMapping("/regions")
    public ApiResponse<List<TrafficRegionResponse>> getAllActiveRegions() {
        try {
            List<TrafficRegionResponse> response = trafficCatalogService.getAllActiveRegions();
            return ApiResponse.success(response);
        } catch (Exception e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/channels/search")
    public ApiResponse<Page<TrafficChannelResponse>> searchChannels(@RequestBody TrafficChannelSearchRequest request) {
        try {
            Page<TrafficChannelResponse> response = trafficCatalogService.searchChannels(request);
            return ApiResponse.success(response);
        } catch (Exception e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
