package com.skillsync.review.service;

import com.skillsync.common.config.RabbitMQConstants;
import com.skillsync.common.event.BadgeEarnedEvent;
import com.skillsync.common.event.BaseEvent;
import com.skillsync.common.event.ReviewSubmittedEvent;
import com.skillsync.common.util.EventFactory;
import com.skillsync.review.client.SessionClient;
import com.skillsync.review.model.Badge;
import com.skillsync.review.model.Review;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventPublisherService {

    private static final String SERVICE_NAME = "review-service";

    private final RabbitTemplate rabbitTemplate;

    public void publishReviewSubmitted(Review review, SessionClient.SessionSnapshot metadata) {
        BaseEvent baseEvent = buildBaseEvent(RabbitMQConstants.RK_REVIEW_SUBMITTED);
        ReviewSubmittedEvent event = new ReviewSubmittedEvent(
                baseEvent,
                review.getId(),
                metadata.mentorId(),
                metadata.learnerId(),
                review.getRating()
        );
        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE, RabbitMQConstants.RK_REVIEW_SUBMITTED, event);
    }

    public void publishBadgeEarned(Long userId, Badge badge) {
        BaseEvent baseEvent = buildBaseEvent(RabbitMQConstants.RK_BADGE_EARNED);
        BadgeEarnedEvent event = new BadgeEarnedEvent(baseEvent, userId, badge.getId(), badge.getName());
        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE, RabbitMQConstants.RK_BADGE_EARNED, event);
    }

    private BaseEvent buildBaseEvent(String eventType) {
        return EventFactory.create(eventType, SERVICE_NAME, MDC.get("correlationId"));
    }
}
