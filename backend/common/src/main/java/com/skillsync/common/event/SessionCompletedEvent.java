package com.skillsync.common.event;

public record SessionCompletedEvent(BaseEvent base, Long sessionId, Long mentorId, Long learnerId) {
}
