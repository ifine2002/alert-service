package com.techasians.alertservice.service.forecast;

import com.techasians.alertservice.dto.TrafficForecastDayResponse;
import com.techasians.alertservice.dto.TrafficForecastResponse;
import com.techasians.alertservice.dto.forecast.EventCalendar;
import com.techasians.alertservice.dto.forecast.ForecastBranch;
import com.techasians.alertservice.dto.forecast.ForecastContext;
import com.techasians.alertservice.dto.forecast.ForecastDay;
import com.techasians.alertservice.dto.forecast.ForecastEvent;
import com.techasians.alertservice.dto.forecast.ForecastInputData;
import com.techasians.alertservice.dto.forecast.RegionChannelConfig;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Map ForecastContext thành response group theo region-channel.
 *
 * Chỉ round giá trị khi build response.
 */
@Component
public class ForecastResponseMapper {

    private static final String CALL_METRIC_NAME = "Cuộc gọi";

    public List<TrafficForecastResponse> map(Map<Long, ForecastContext> contexts,
                                              ForecastInputData input,
                                              EventCalendar calendar,
                                              LocalDate fromDate,
                                              LocalDate toDate) {
        List<TrafficForecastResponse> result = new ArrayList<>();

        for (RegionChannelConfig config : input.getRegionChannels()) {
            ForecastContext context = contexts.get(config.getRegionChannelId());
            if (context == null) {
                continue;
            }

            List<TrafficForecastDayResponse> forecasts = buildForecastDays(
                    context,
                    calendar,
                    fromDate,
                    toDate,
                    config.getRegionChannelId(),
                    config.getChannelId()
            );

            TrafficForecastResponse response = new TrafficForecastResponse()
                    .setRegionChannelId(config.getRegionChannelId())
                    .setRegionId(config.getRegionId())
                    .setRegionName(config.getRegionName())
                    .setChannelId(config.getChannelId())
                    .setChannelName(config.getChannelName())
                    .setMetricName(CALL_METRIC_NAME)
                    .setActualCutoff(config.getActualCutoff())
                    .setCalculationStart(config.getCalculationStart())
                    .setDataQuality(config.getDataQuality())
                    .setDataQualityNote(config.getDataQualityNote())
                    .setGapDays(config.getGapDays())
                    .setForecasts(forecasts);

            result.add(response);
        }

        return result;
    }

    private List<TrafficForecastDayResponse> buildForecastDays(ForecastContext context,
                                                                 EventCalendar calendar,
                                                                 LocalDate fromDate,
                                                                 LocalDate toDate,
                                                                 long regionChannelId,
                                                                 long channelId) {
        List<TrafficForecastDayResponse> forecasts = new ArrayList<>();

        for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
            ForecastDay day = context.get(date);
            if (day == null || day.getValue() == null) {
                continue;
            }

            BigDecimal responseValue = day.getValue();
            if (day.getBranch() == ForecastBranch.NORMAL) {
                BigDecimal variationRate = calculateDeterministicVariation(date, regionChannelId, channelId);
                responseValue = responseValue.multiply(BigDecimal.ONE.add(variationRate));
            }

            forecasts.add(new TrafficForecastDayResponse()
                    .setDate(date)
                    .setValue(responseValue.setScale(0, RoundingMode.HALF_UP))
                    .setSource(day.getSource().name())
                    .setHasEvent(day.isHasEvent())
                    .setEventNames(resolveEventNames(day, date, calendar, Math.toIntExact(channelId)))
                    .setImpactRate(day.getImpactRate()));
        }

        return forecasts;
    }

    private BigDecimal calculateDeterministicVariation(
            LocalDate date,
            long regionChannelId,
            long channelId) {
        long seed = date.toEpochDay();
        seed = seed * 31 + regionChannelId;
        seed = seed * 31 + channelId;
        seed ^= seed >>> 16;
        seed *= 0x45d9f3bL;
        seed ^= seed >>> 16;
        int maximumBasisPoints = 200;
        int numberOfValues = maximumBasisPoints * 2 + 1;
        int variationBasisPoints =
                Math.floorMod(seed, numberOfValues)
                        - maximumBasisPoints;
        return BigDecimal.valueOf(variationBasisPoints)
                .divide(BigDecimal.valueOf(10_000));
    }

    private List<String> resolveEventNames(ForecastDay day, LocalDate date, EventCalendar calendar, Integer channelId) {
        if (!day.isHasEvent()) {
            return new ArrayList<>();
        }

        List<ForecastEvent> events = calendar.getEvents(date, channelId);
        return events.stream()
                .map(ForecastEvent::getName)
                .collect(Collectors.toList());
    }
}
