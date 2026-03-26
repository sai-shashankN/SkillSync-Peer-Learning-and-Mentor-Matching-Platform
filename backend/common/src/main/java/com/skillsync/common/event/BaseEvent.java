package com.skillsync.common.event;

import java.time.Instant;
import java.util.Map;

public record BaseEvent(
        String eventId,
        String eventType,
        String schemaVersion,
        String serviceName,
        String correlationId,
        String causationId,
        Instant timestamp,
        Map<String, Object> metadata
) {
}
