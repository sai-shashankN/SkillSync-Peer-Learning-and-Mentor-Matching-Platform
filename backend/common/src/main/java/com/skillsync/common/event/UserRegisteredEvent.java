package com.skillsync.common.event;

public record UserRegisteredEvent(BaseEvent base, Long userId, String email, String name) {
}
