package com.skillsync.common.event;

public record WaitlistSlotOpenEvent(BaseEvent base, Long mentorId, Long learnerId) {
}
