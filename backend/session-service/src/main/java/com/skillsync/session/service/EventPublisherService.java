package com.skillsync.session.service;

import com.skillsync.common.config.RabbitMQConstants;
import com.skillsync.common.event.BaseEvent;
import com.skillsync.common.event.ReviewSubmittedEvent;
import com.skillsync.common.event.SessionAcceptedEvent;
import com.skillsync.common.event.SessionBookedEvent;
import com.skillsync.common.event.SessionCancelledEvent;
import com.skillsync.common.event.SessionCompletedEvent;
import com.skillsync.common.event.SessionRejectedEvent;
import com.skillsync.common.util.EventFactory;
import com.skillsync.session.model.Session;
import com.skillsync.session.model.SessionFeedback;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventPublisherService {

    private static final String SERVICE_NAME = "session-service";

    private final RabbitTemplate rabbitTemplate;

    public void publishSessionBooked(Session session) {
        BaseEvent baseEvent = buildBaseEvent(RabbitMQConstants.RK_SESSION_BOOKED);
        SessionBookedEvent event = new SessionBookedEvent(
                baseEvent,
                session.getId(),
                session.getMentorId(),
                session.getLearnerId(),
                session.getStartAt(),
                session.getAmount()
        );
        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE, RabbitMQConstants.RK_SESSION_BOOKED, event);
    }

    public void publishSessionAccepted(Session session) {
        BaseEvent baseEvent = buildBaseEvent(RabbitMQConstants.RK_SESSION_ACCEPTED);
        SessionAcceptedEvent event = new SessionAcceptedEvent(
                baseEvent,
                session.getId(),
                session.getMentorId(),
                session.getLearnerId(),
                session.getStartAt()
        );
        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE, RabbitMQConstants.RK_SESSION_ACCEPTED, event);
    }

    public void publishSessionRejected(Session session) {
        BaseEvent baseEvent = buildBaseEvent(RabbitMQConstants.RK_SESSION_REJECTED);
        SessionRejectedEvent event = new SessionRejectedEvent(
                baseEvent,
                session.getId(),
                session.getMentorId(),
                session.getLearnerId()
        );
        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE, RabbitMQConstants.RK_SESSION_REJECTED, event);
    }

    public void publishSessionCompleted(Session session) {
        BaseEvent baseEvent = buildBaseEvent(RabbitMQConstants.RK_SESSION_COMPLETED);
        SessionCompletedEvent event = new SessionCompletedEvent(
                baseEvent,
                session.getId(),
                session.getMentorId(),
                session.getLearnerId()
        );
        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE, RabbitMQConstants.RK_SESSION_COMPLETED, event);
    }

    public void publishSessionCancelled(Session session, String reason) {
        BaseEvent baseEvent = buildBaseEvent(RabbitMQConstants.RK_SESSION_CANCELLED);
        SessionCancelledEvent event = new SessionCancelledEvent(baseEvent, session.getId(), session.getCancelledByUserId(), reason);
        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE, RabbitMQConstants.RK_SESSION_CANCELLED, event);
    }

    public void publishReviewSubmitted(SessionFeedback feedback, Session session) {
        BaseEvent baseEvent = buildBaseEvent(RabbitMQConstants.RK_REVIEW_SUBMITTED);
        ReviewSubmittedEvent event = new ReviewSubmittedEvent(
                baseEvent,
                feedback.getId(),
                session.getMentorId(),
                session.getLearnerId(),
                feedback.getRating()
        );
        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE, RabbitMQConstants.RK_REVIEW_SUBMITTED, event);
    }

    private BaseEvent buildBaseEvent(String eventType) {
        return EventFactory.create(eventType, SERVICE_NAME, MDC.get("correlationId"));
    }
}
