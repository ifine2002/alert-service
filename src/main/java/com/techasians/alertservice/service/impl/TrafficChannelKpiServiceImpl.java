package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.dto.TrafficChannelKpiProjection;
import com.techasians.alertservice.dto.TrafficChannelKpiRequest;
import com.techasians.alertservice.dto.TrafficChannelKpiResponse;
import com.techasians.alertservice.dto.TrafficChannelKpiSearchRequest;
import com.techasians.alertservice.entity.TrafficChannel;
import com.techasians.alertservice.entity.TrafficChannelKpi;
import com.techasians.alertservice.exception.DuplicateResourceException;
import com.techasians.alertservice.exception.ResourceNotFoundException;
import com.techasians.alertservice.repository.TrafficChannelKpiRepository;
import com.techasians.alertservice.repository.TrafficChannelRepository;
import com.techasians.alertservice.service.TrafficChannelKpiService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class TrafficChannelKpiServiceImpl implements TrafficChannelKpiService {

    private final TrafficChannelKpiRepository trafficChannelKpiRepository;
    private final TrafficChannelRepository trafficChannelRepository;

    public TrafficChannelKpiServiceImpl(TrafficChannelKpiRepository trafficChannelKpiRepository,
                                        TrafficChannelRepository trafficChannelRepository) {
        this.trafficChannelKpiRepository = trafficChannelKpiRepository;
        this.trafficChannelRepository = trafficChannelRepository;
    }

    @Override
    @Transactional
    public Integer create(TrafficChannelKpiRequest request, String createdBy) {
        if (!trafficChannelRepository.existsById(request.getChannelId())) {
            throw new ResourceNotFoundException("Không tìm thấy kênh lưu lượng với ID: " + request.getChannelId());
        }

        if (trafficChannelKpiRepository.existsByChannelIdAndDeletedAtIsNull(request.getChannelId())) {
            throw new DuplicateResourceException("Cấu hình KPI cho kênh lưu lượng này đã tồn tại.");
        }

        TrafficChannelKpi kpi = new TrafficChannelKpi()
                .setChannelId(request.getChannelId())
                .setKpiRate(request.getKpiRate())
                .setProductivityValue(request.getProductivityValue())
                .setCreatedBy(createdBy)
                .setUpdatedBy(createdBy);

        TrafficChannelKpi saved = trafficChannelKpiRepository.save(kpi);
        return saved.getId();
    }

    @Override
    @Transactional
    public Integer update(Integer id, TrafficChannelKpiRequest request, String updatedBy) {
        TrafficChannelKpi kpi = trafficChannelKpiRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cấu hình KPI với ID: " + id));

        if (!trafficChannelRepository.existsById(request.getChannelId())) {
            throw new ResourceNotFoundException("Không tìm thấy kênh lưu lượng với ID: " + request.getChannelId());
        }

        if (trafficChannelKpiRepository.existsByChannelIdAndIdNotAndDeletedAtIsNull(request.getChannelId(), id)) {
            throw new DuplicateResourceException("Cấu hình KPI cho kênh lưu lượng này đã tồn tại.");
        }

        kpi.setChannelId(request.getChannelId())
                .setKpiRate(request.getKpiRate())
                .setProductivityValue(request.getProductivityValue())
                .setUpdatedBy(updatedBy);

        trafficChannelKpiRepository.save(kpi);
        return kpi.getId();
    }

    @Override
    @Transactional
    public void delete(Integer id, String deletedBy) {
        TrafficChannelKpi kpi = trafficChannelKpiRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cấu hình KPI với ID: " + id));

        kpi.setDeletedAt(LocalDateTime.now())
                .setDeletedBy(deletedBy)
                .setUpdatedBy(deletedBy);

        trafficChannelKpiRepository.save(kpi);
    }

    @Override
    public TrafficChannelKpiResponse getById(Integer id) {
        TrafficChannelKpi kpi = trafficChannelKpiRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cấu hình KPI với ID: " + id));
        String channelName = null;
        if (kpi.getChannelId() != null) {
            channelName = trafficChannelRepository.findById(kpi.getChannelId())
                    .map(TrafficChannel::getName)
                    .orElse(null);
        }
        return mapToResponse(kpi, channelName);
    }

    @Override
    public Page<TrafficChannelKpiResponse> search(TrafficChannelKpiSearchRequest searchRequest) {
        Sort sort = Sort.unsorted();
        if (StringUtils.hasText(searchRequest.getSort())) {
            String[] arr = searchRequest.getSort().split(",");
            if (arr.length == 2) {
                sort = Sort.by(Sort.Direction.fromString(arr[1].trim()), arr[0].trim());
            } else {
                sort = Sort.by(arr[0].trim());
            }
        } else {
             sort = Sort.by(Sort.Direction.DESC, "updatedAt");
        }

        Pageable pageable = PageRequest.of(searchRequest.getPage(), searchRequest.getSize(), sort);

        String keyword = searchRequest.getKeyword() != null ? escapeSqlWildcards(searchRequest.getKeyword()) : null;

        Page<TrafficChannelKpiProjection> pageData = trafficChannelKpiRepository.search(
                searchRequest.getChannelId(),
                keyword,
                pageable);

        return pageData.map(proj -> {
            return new TrafficChannelKpiResponse()
                    .setId(proj.getId())
                    .setChannelId(proj.getChannelId())
                    .setKpiRate(formatBigDecimal(proj.getKpiRate()))
                    .setProductivityValue(formatBigDecimal(proj.getProductivityValue()))
                    .setCreatedAt(proj.getCreatedAt())
                    .setCreatedBy(proj.getCreatedBy())
                    .setUpdatedAt(proj.getUpdatedAt())
                    .setUpdatedBy(proj.getUpdatedBy())
                    .setChannelName(proj.getChannelName());
        });
    }

    private TrafficChannelKpiResponse mapToResponse(TrafficChannelKpi entity, String channelName) {
        return new TrafficChannelKpiResponse()
                .setId(entity.getId())
                .setChannelId(entity.getChannelId())
                .setKpiRate(entity.getKpiRate())
                .setProductivityValue(entity.getProductivityValue())
                .setCreatedAt(entity.getCreatedAt())
                .setCreatedBy(entity.getCreatedBy())
                .setUpdatedAt(entity.getUpdatedAt())
                .setUpdatedBy(entity.getUpdatedBy())
                .setChannelName(channelName);
    }

    private BigDecimal formatBigDecimal(BigDecimal value) {
        if (value == null) {
            return null;
        }

        if (value.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal result = value.stripTrailingZeros();

        if (result.scale() < 0) {
            return result.setScale(0);
        }

        return result;
    }

    private String escapeSqlWildcards(String input) {
        if (input == null) {
            return null;
        }
        return input.trim()
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
}
