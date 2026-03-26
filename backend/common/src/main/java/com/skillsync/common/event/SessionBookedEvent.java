package com.skillsync.common.event;

import java.math.BigDecimal;
import java.time.Instant;

public record SessionBookedEvent(
        BaseEvent base,
        Long sessionId,
        Long mentorId,
        Long learnerId,
        Instant startAt,
        BigDecimal amount
) {
}
