package com.techasians.alertservice.dto.forecast;

import java.util.Objects;

/**
 * Key dùng trong Map để tra cứu impact_rate theo cặp (eventId, channelId).
 *
 * Ví dụ dùng:
 *   Map<EventChannelKey, BigDecimal> impactRates
 *   impactRates.get(new EventChannelKey(eventId, channelId)) → rate đã chia 100
 */
public class EventChannelKey {

    private final Integer eventId;
    private final Integer channelId;

    public EventChannelKey(Integer eventId, Integer channelId) {
        this.eventId = eventId;
        this.channelId = channelId;
    }

    public Integer getEventId() {
        return eventId;
    }

    public Integer getChannelId() {
        return channelId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EventChannelKey)) return false;
        EventChannelKey that = (EventChannelKey) o;
        return Objects.equals(eventId, that.eventId)
                && Objects.equals(channelId, that.channelId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, channelId);
    }
}
