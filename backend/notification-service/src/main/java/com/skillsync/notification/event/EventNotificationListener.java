package com.skillsync.notification.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsync.common.config.RabbitMQConstants;
import com.skillsync.notification.service.NotificationService;
import com.skillsync.notification.service.WebhookForwarderService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventNotificationListener {

    private final NotificationService notificationService;
    private final WebhookForwarderService webhookForwarderService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConstants.NOTIFICATION_QUEUE)
    public void handleEvent(Map<String, Object> payload) {
        Map<String, Object> base = getMap(payload, "base");
        String eventType = stringValue(base.get("eventType"));
        if (eventType == null) {
            log.warn("Received notification event without base.eventType: {}", payload);
            return;
        }

        String dataJson = toJson(payload);
        String eventId = stringValue(base.get("eventId"));

        switch (eventType) {
            case RabbitMQConstants.RK_USER_REGISTERED -> createSingleNotification(
                    longValue(payload.get("userId")),
                    "WELCOME",
                    "Welcome to SkillSync!",
                    "Your account has been created successfully.",
                    dataJson,
                    eventId
            );
            case RabbitMQConstants.RK_MENTOR_APPLIED -> createSingleNotification(
                    longValue(payload.get("userId")),
                    "MENTOR_APPLICATION",
                    "Mentor Application Submitted",
                    "Your mentor application is under review.",
                    dataJson,
                    eventId
            );
            case RabbitMQConstants.RK_MENTOR_APPROVED -> createSingleNotification(
                    longValue(payload.get("userId")),
                    "MENTOR_APPROVED",
                    "Mentor Application Approved",
                    "Congratulations! You are now an approved mentor.",
                    dataJson,
                    eventId
            );
            case RabbitMQConstants.RK_MENTOR_REJECTED -> createSingleNotification(
                    longValue(payload.get("userId")),
                    "MENTOR_REJECTED",
                    "Mentor Application Rejected",
                    "Your mentor application was not approved at this time.",
                    dataJson,
                    eventId
            );
            case RabbitMQConstants.RK_SESSION_BOOKED -> createSingleNotification(
                    longValue(payload.get("mentorId")),
                    "SESSION_BOOKED",
                    "New Session Booking",
                    "A learner has booked a session with you.",
                    dataJson,
                    eventId
            );
            case RabbitMQConstants.RK_SESSION_ACCEPTED -> createSingleNotification(
                    longValue(payload.get("learnerId")),
                    "SESSION_ACCEPTED",
                    "Session Accepted",
                    "Your session has been accepted by the mentor.",
                    dataJson,
                    eventId
            );
            case RabbitMQConstants.RK_SESSION_REJECTED -> createSingleNotification(
                    longValue(payload.get("learnerId")),
                    "SESSION_REJECTED",
                    "Session Rejected",
                    "Your session was rejected by the mentor.",
                    dataJson,
                    eventId
            );
            case RabbitMQConstants.RK_SESSION_CANCELLED -> log.warn(
                    "Skipping session cancellation notification because target user is unavailable in payload. eventId={}, sessionId={}",
                    eventId,
                    longValue(payload.get("sessionId"))
            );
            case RabbitMQConstants.RK_SESSION_COMPLETED -> {
                createSingleNotification(
                        longValue(payload.get("learnerId")),
                        "SESSION_COMPLETED",
                        "Session Completed",
                        "Your session has been marked as complete.",
                        dataJson,
                        buildRecipientDedupeKey(eventId, longValue(payload.get("learnerId")))
                );
                createSingleNotification(
                        longValue(payload.get("mentorId")),
                        "SESSION_COMPLETED",
                        "Session Completed",
                        "Your session has been marked as complete.",
                        dataJson,
                        buildRecipientDedupeKey(eventId, longValue(payload.get("mentorId")))
                );
            }
            case RabbitMQConstants.RK_PAYMENT_RECEIVED -> createSingleNotification(
                    longValue(payload.get("payerId")),
                    "PAYMENT_RECEIVED",
                    "Payment Successful",
                    "Your payment has been processed.",
                    dataJson,
                    eventId
            );
            case RabbitMQConstants.RK_PAYMENT_REFUNDED -> log.warn(
                    "Skipping payment refunded notification because payerId is unavailable in payload. eventId={}, paymentId={}",
                    eventId,
                    longValue(payload.get("paymentId"))
            );
            case RabbitMQConstants.RK_REVIEW_SUBMITTED -> createSingleNotification(
                    longValue(payload.get("mentorId")),
                    "REVIEW_RECEIVED",
                    "New Review",
                    "You received a new review.",
                    dataJson,
                    eventId
            );
            case RabbitMQConstants.RK_BADGE_EARNED -> createSingleNotification(
                    longValue(payload.get("userId")),
                    "BADGE_EARNED",
                    "Badge Earned!",
                    "You earned the " + stringValue(payload.get("badgeName")) + " badge!",
                    dataJson,
                    eventId
            );
            case RabbitMQConstants.RK_WAITLIST_SLOT_OPEN -> createSingleNotification(
                    longValue(payload.get("learnerId")),
                    "WAITLIST_AVAILABLE",
                    "Mentor Available",
                    "A mentor you were waitlisted for is now available.",
                    dataJson,
                    eventId
            );
            default -> log.debug("Ignoring unsupported notification eventType={}", eventType);
        }

        webhookForwarderService.forwardEvent(eventType, payload);
    }

    private void createSingleNotification(
            Long userId,
            String type,
            String title,
            String message,
            String dataJson,
            String dedupeKey
    ) {
        if (userId == null) {
            log.warn("Skipping notification type={} because target userId is missing", type);
            return;
        }

        notificationService.createNotification(userId, type, title, message, dataJson, dedupeKey);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Map<?, ?> mapValue) {
            return (Map<String, Object>) mapValue;
        }
        return Map.of();
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to serialize notification payload", ex);
        }
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String buildRecipientDedupeKey(String eventId, Long userId) {
        if (eventId == null || userId == null) {
            return eventId;
        }
        return eventId + ":" + userId;
    }
}
