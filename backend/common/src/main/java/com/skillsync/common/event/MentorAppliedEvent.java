package com.skillsync.common.event;

public record MentorAppliedEvent(BaseEvent base, Long mentorId, Long userId, String name) {
}
