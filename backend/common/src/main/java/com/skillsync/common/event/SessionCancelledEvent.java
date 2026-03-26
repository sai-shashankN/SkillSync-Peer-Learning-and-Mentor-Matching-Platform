package com.skillsync.common.event;

public record SessionCancelledEvent(BaseEvent base, Long sessionId, Long cancelledBy, String reason) {
}
