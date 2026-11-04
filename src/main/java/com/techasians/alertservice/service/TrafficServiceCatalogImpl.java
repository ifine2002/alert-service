package com.techasians.alertservice.service;

import com.techasians.alertservice.dto.TrafficChannelResponse;
import com.techasians.alertservice.dto.TrafficChannelSearchRequest;
import com.techasians.alertservice.dto.TrafficRegionResponse;
import com.techasians.alertservice.entity.TrafficChannel;
import com.techasians.alertservice.entity.TrafficRegion;
import com.techasians.alertservice.repository.TrafficChannelRepository;
import com.techasians.alertservice.repository.TrafficRegionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TrafficServiceCatalogImpl implements TrafficCatalogService {

    private final TrafficRegionRepository trafficRegionRepository;
    private final TrafficChannelRepository trafficChannelRepository;

    public TrafficServiceCatalogImpl(TrafficRegionRepository trafficRegionRepository,
                                     TrafficChannelRepository trafficChannelRepository) {
        this.trafficRegionRepository = trafficRegionRepository;
        this.trafficChannelRepository = trafficChannelRepository;
    }

    @Override
    public List<TrafficRegionResponse> getAllActiveRegions() {
        List<TrafficRegion> regions = trafficRegionRepository.findByStatus(1);
        return regions.stream()
                .map(region -> new TrafficRegionResponse(
                        region.getId(),
                        region.getName()
                ))
                .collect(Collectors.toList());
    }

    @Override
    public Page<TrafficChannelResponse> searchChannels(TrafficChannelSearchRequest request) {
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());

        Page<TrafficChannel> channels;
        List<Long> regionIds = request.getRegionIds();

        if (regionIds == null || regionIds.isEmpty()) {
            channels = trafficChannelRepository.findAllActiveChannels(pageable);
        } else {
            channels = trafficChannelRepository.searchChannelsByRegionIds(regionIds, pageable);
        }

        return channels.map(channel -> new TrafficChannelResponse(
                channel.getId(),
                channel.getName()
        ));
    }
}
