package com.skillsync.review.service;

import com.skillsync.review.client.SessionClient;
import com.skillsync.review.dto.BadgeResponse;
import com.skillsync.review.dto.UserBadgeResponse;
import com.skillsync.review.mapper.ReviewMapper;
import com.skillsync.review.model.Badge;
import com.skillsync.review.model.UserBadge;
import com.skillsync.review.repository.BadgeRepository;
import com.skillsync.review.repository.UserBadgeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BadgeService {

    private final SessionClient sessionClient;
    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final ReviewMapper reviewMapper;
    private final EventPublisherService eventPublisherService;

    @Transactional
    public void checkAndAwardBadges(Long userId, Long skillId) {
        long completedSessionCount = sessionClient.getCompletedSessionCount(userId, skillId);
        List<Badge> badges = badgeRepository.findBySkillId(skillId);

        for (Badge badge : badges) {
            if (completedSessionCount < badge.getRequiredSessions()
                    || userBadgeRepository.existsByUserIdAndBadgeId(userId, badge.getId())) {
                continue;
            }

            userBadgeRepository.save(UserBadge.builder()
                    .userId(userId)
                    .badgeId(badge.getId())
                    .awardedForSkillId(skillId)
                    .build());
            eventPublisherService.publishBadgeEarned(userId, badge);
        }
    }

    @Transactional(readOnly = true)
    public List<UserBadgeResponse> getUserBadges(Long userId) {
        return userBadgeRepository.findByUserId(userId).stream()
                .map(reviewMapper::toUserBadgeResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BadgeResponse> getAvailableBadges() {
        return badgeRepository.findAll().stream()
                .map(reviewMapper::toBadgeResponse)
                .toList();
    }
}
