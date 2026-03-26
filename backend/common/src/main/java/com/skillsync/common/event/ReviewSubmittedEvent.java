package com.skillsync.common.event;

public record ReviewSubmittedEvent(BaseEvent base, Long reviewId, Long mentorId, Long learnerId, Integer rating) {
}
