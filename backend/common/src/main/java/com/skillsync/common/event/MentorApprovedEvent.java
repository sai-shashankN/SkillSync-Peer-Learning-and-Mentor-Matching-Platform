package com.skillsync.common.event;

public record MentorApprovedEvent(BaseEvent base, Long mentorId, Long userId, String email) {
}
