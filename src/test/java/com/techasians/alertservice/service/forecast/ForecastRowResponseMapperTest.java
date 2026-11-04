package com.techasians.alertservice.service.forecast;

import com.techasians.alertservice.dto.*;
import com.techasians.alertservice.dto.forecast.EventCalendar;
import com.techasians.alertservice.dto.forecast.ForecastEvent;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ForecastRowResponseMapperTest {
    private final ForecastRowResponseMapper mapper = new ForecastRowResponseMapper();

    @Test
    void shouldReturnOnlyEventsMappedToEachChannel() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        Map<LocalDate, List<ForecastEvent>> events = new HashMap<>();
        events.put(date, Arrays.asList(event("Chu kỳ cước", 1, 10), event("Ngày Quốc khánh", 2, 20), event("Kênh khác", 2, 30)));
        TrafficForecastTableResponse result = mapper.mapToTable(Arrays.asList(channel(101L, 10, date), channel(102L, 20, date)), new EventCalendar(events), new HashMap<>(), date, date);
        TrafficForecastDayRow day = onlyDay(result);
        assertEquals(Collections.singletonList("Chu kỳ cước"), day.getChannelResults().get(0).getEvents());
        assertEquals(Collections.singletonList("Ngày Quốc khánh"), day.getChannelResults().get(1).getEvents());
    }

    @Test
    void shouldMergeAllEventTypesAndReturnEmptyListWithoutMapping() {
        LocalDate date = LocalDate.of(2026, 9, 2);
        Map<LocalDate, List<ForecastEvent>> events = new HashMap<>();
        events.put(date, Arrays.asList(event("Định kỳ", 1, 10), event("Phát sinh", 2, 10), event("Kênh khác", 2, 20)));
        TrafficForecastTableResponse result = mapper.mapToTable(Collections.singletonList(channel(101L, 10, date)), new EventCalendar(events), new HashMap<>(), date, date);
        assertEquals(Arrays.asList("Định kỳ", "Phát sinh"), onlyDay(result).getChannelResults().get(0).getEvents());

        TrafficForecastTableResponse noMapping = mapper.mapToTable(Collections.singletonList(channel(102L, 30, date)), new EventCalendar(events), new HashMap<>(), date, date);
        List<String> noEvents = onlyDay(noMapping).getChannelResults().get(0).getEvents();
        assertNotNull(noEvents);
        assertTrue(noEvents.isEmpty());
    }

    private ForecastEvent event(String name, Integer type, Integer channelId) {
        return new ForecastEvent().setName(name).setEventType(type).setChannelIds(Collections.singletonList(channelId));
    }

    private TrafficForecastResponse channel(Long regionChannelId, Integer channelId, LocalDate date) {
        TrafficForecastDayResponse day = new TrafficForecastDayResponse().setDate(date).setValue(BigDecimal.valueOf(1000)).setImpactRate(BigDecimal.valueOf(0.05));
        return new TrafficForecastResponse().setRegionChannelId(regionChannelId).setRegionId(1L).setRegionName("Hà Nội").setChannelId(channelId).setChannelName("Kênh " + channelId).setForecasts(Collections.singletonList(day));
    }

    private TrafficForecastDayRow onlyDay(TrafficForecastTableResponse result) {
        assertEquals(1, result.getRegions().size());
        TrafficForecastRegionResult region = result.getRegions().get(0);
        assertEquals(1, region.getDays().size());
        return region.getDays().get(0);
    }
}
