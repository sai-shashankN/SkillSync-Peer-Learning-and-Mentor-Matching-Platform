package com.skillsync.common.event;

public record MentorRejectedEvent(BaseEvent base, Long mentorId, Long userId, String email) {
}
