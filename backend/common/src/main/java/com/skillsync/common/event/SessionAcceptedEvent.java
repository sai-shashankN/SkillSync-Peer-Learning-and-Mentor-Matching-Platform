package com.skillsync.common.event;

import java.time.Instant;

public record SessionAcceptedEvent(
        BaseEvent base,
        Long sessionId,
        Long mentorId,
        Long learnerId,
        Instant startAt
) {
}
