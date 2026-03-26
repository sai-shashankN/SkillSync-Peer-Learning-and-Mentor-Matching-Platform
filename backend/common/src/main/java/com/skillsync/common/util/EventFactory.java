package com.skillsync.common.util;

import com.skillsync.common.event.BaseEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class EventFactory {

    private EventFactory() {
    }

    public static BaseEvent create(String eventType, String serviceName, String correlationId) {
        return new BaseEvent(
                UUID.randomUUID().toString(),
                eventType,
                "1.0",
                serviceName,
                correlationId != null ? correlationId : UUID.randomUUID().toString(),
                null,
                Instant.now(),
                Map.of()
        );
    }
}
