package com.skillsync.common.event;

public record BadgeEarnedEvent(BaseEvent base, Long userId, Integer badgeId, String badgeName) {
}
