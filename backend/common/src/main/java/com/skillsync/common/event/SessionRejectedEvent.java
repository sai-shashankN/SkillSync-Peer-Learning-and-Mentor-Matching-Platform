package com.skillsync.common.event;

public record SessionRejectedEvent(BaseEvent base, Long sessionId, Long mentorId, Long learnerId) {
}
