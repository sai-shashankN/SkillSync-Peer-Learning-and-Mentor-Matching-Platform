package com.skillsync.audit.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsync.audit.service.AnalyticsProjectionService;
import com.skillsync.audit.service.AuditLogService;
import com.skillsync.common.config.RabbitMQConstants;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventListener {

    private final AuditLogService auditLogService;
    private final AnalyticsProjectionService analyticsProjectionService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConstants.AUDIT_QUEUE)
    public void handleEvent(Map<String, Object> payload) {
        Map<String, Object> base = getMap(payload, "base");
        String eventType = stringValue(base.get("eventType"));
        if (eventType == null) {
            log.warn("Received audit event without base.eventType: {}", payload);
            return;
        }

        String detailsJson = toJson(payload);
        String serviceName = stringValue(base.get("serviceName"));
        String correlationId = stringValue(base.get("correlationId"));
        LocalDate eventDate = resolveEventDate(base.get("timestamp"));

        switch (eventType) {
            case RabbitMQConstants.RK_USER_REGISTERED -> {
                auditLogService.logEvent(
                        longValue(payload.get("userId")),
                        "USER_REGISTERED",
                        serviceName,
                        "USER",
                        stringValue(payload.get("userId")),
                        detailsJson,
                        correlationId,
                        "SERVICE"
                );
                analyticsProjectionService.incrementNewUsers(eventDate);
            }
            case RabbitMQConstants.RK_MENTOR_APPLIED -> auditLogService.logEvent(
                    longValue(payload.get("userId")),
                    "MENTOR_APPLIED",
                    serviceName,
                    "MENTOR",
                    stringValue(payload.get("mentorId")),
                    detailsJson,
                    correlationId,
                    "SERVICE"
            );
            case RabbitMQConstants.RK_MENTOR_APPROVED -> {
                auditLogService.logEvent(
                        longValue(payload.get("userId")),
                        "MENTOR_APPROVED",
                        serviceName,
                        "MENTOR",
                        stringValue(payload.get("mentorId")),
                        detailsJson,
                        correlationId,
                        "SERVICE"
                );
                analyticsProjectionService.incrementActiveMentors(eventDate);
            }
            case RabbitMQConstants.RK_MENTOR_REJECTED -> auditLogService.logEvent(
                    longValue(payload.get("userId")),
                    "MENTOR_REJECTED",
                    serviceName,
                    "MENTOR",
                    stringValue(payload.get("mentorId")),
                    detailsJson,
                    correlationId,
                    "SERVICE"
            );
            case RabbitMQConstants.RK_SESSION_BOOKED -> {
                auditLogService.logEvent(
                        longValue(payload.get("learnerId")),
                        "SESSION_BOOKED",
                        serviceName,
                        "SESSION",
                        stringValue(payload.get("sessionId")),
                        detailsJson,
                        correlationId,
                        "SERVICE"
                );
                analyticsProjectionService.incrementSessions(eventDate);
            }
            case RabbitMQConstants.RK_SESSION_ACCEPTED -> auditLogService.logEvent(
                    longValue(payload.get("mentorId")),
                    "SESSION_ACCEPTED",
                    serviceName,
                    "SESSION",
                    stringValue(payload.get("sessionId")),
                    detailsJson,
                    correlationId,
                    "SERVICE"
            );
            case RabbitMQConstants.RK_SESSION_REJECTED -> auditLogService.logEvent(
                    longValue(payload.get("mentorId")),
                    "SESSION_REJECTED",
                    serviceName,
                    "SESSION",
                    stringValue(payload.get("sessionId")),
                    detailsJson,
                    correlationId,
                    "SERVICE"
            );
            case RabbitMQConstants.RK_SESSION_CANCELLED -> {
                auditLogService.logEvent(
                        longValue(payload.get("cancelledBy")),
                        "SESSION_CANCELLED",
                        serviceName,
                        "SESSION",
                        stringValue(payload.get("sessionId")),
                        detailsJson,
                        correlationId,
                        "SERVICE"
                );
                analyticsProjectionService.incrementCancelledSessions(eventDate);
                analyticsProjectionService.updateMentorPerformance(
                        eventDate,
                        metadataLong(base, "mentorId"),
                        "cancelled",
                        BigDecimal.ZERO
                );
            }
            case RabbitMQConstants.RK_SESSION_COMPLETED -> {
                Long mentorId = longValue(payload.get("mentorId"));
                auditLogService.logEvent(
                        mentorId,
                        "SESSION_COMPLETED",
                        serviceName,
                        "SESSION",
                        stringValue(payload.get("sessionId")),
                        detailsJson,
                        correlationId,
                        "SERVICE"
                );
                analyticsProjectionService.incrementCompletedSessions(eventDate);
                analyticsProjectionService.updateMentorPerformance(eventDate, mentorId, "completed", BigDecimal.ZERO);
                Long skillId = metadataLong(base, "skillId");
                if (skillId != null) {
                    analyticsProjectionService.incrementSkillPopularity(eventDate, skillId);
                }
            }
            case RabbitMQConstants.RK_PAYMENT_RECEIVED -> {
                BigDecimal amount = decimalValue(payload.get("amount"));
                auditLogService.logEvent(
                        longValue(payload.get("payerId")),
                        "PAYMENT_RECEIVED",
                        serviceName,
                        "PAYMENT",
                        stringValue(payload.get("paymentId")),
                        detailsJson,
                        correlationId,
                        "SERVICE"
                );
                analyticsProjectionService.addRevenue(eventDate, amount);
                analyticsProjectionService.updateMentorPerformance(
                        eventDate,
                        metadataLong(base, "mentorId"),
                        "revenue",
                        amount
                );
            }
            case RabbitMQConstants.RK_PAYMENT_REFUNDED -> {
                auditLogService.logEvent(
                        null,
                        "PAYMENT_REFUNDED",
                        serviceName,
                        "PAYMENT",
                        stringValue(payload.get("paymentId")),
                        detailsJson,
                        correlationId,
                        "SERVICE"
                );
                analyticsProjectionService.addRefund(eventDate, decimalValue(payload.get("amount")));
            }
            case RabbitMQConstants.RK_REVIEW_SUBMITTED -> {
                Integer rating = integerValue(payload.get("rating"));
                Long mentorId = longValue(payload.get("mentorId"));
                auditLogService.logEvent(
                        longValue(payload.get("learnerId")),
                        "REVIEW_SUBMITTED",
                        serviceName,
                        "REVIEW",
                        stringValue(payload.get("reviewId")),
                        detailsJson,
                        correlationId,
                        "SERVICE"
                );
                analyticsProjectionService.incrementReviews(eventDate);
                if (rating != null) {
                    analyticsProjectionService.updateMentorPerformance(
                            eventDate,
                            mentorId,
                            "rating",
                            BigDecimal.valueOf(rating)
                    );
                }
            }
            case RabbitMQConstants.RK_BADGE_EARNED -> {
                auditLogService.logEvent(
                        longValue(payload.get("userId")),
                        "BADGE_EARNED",
                        serviceName,
                        "BADGE",
                        stringValue(payload.get("badgeId")),
                        detailsJson,
                        correlationId,
                        "SERVICE"
                );
                analyticsProjectionService.incrementBadges(eventDate);
            }
            case RabbitMQConstants.RK_WAITLIST_SLOT_OPEN -> auditLogService.logEvent(
                    longValue(payload.get("learnerId")),
                    "WAITLIST_SLOT_OPEN",
                    serviceName,
                    "WAITLIST",
                    stringValue(payload.get("mentorId")),
                    detailsJson,
                    correlationId,
                    "SERVICE"
            );
            default -> log.debug("Ignoring unsupported audit eventType={}", eventType);
        }
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
            throw new IllegalArgumentException("Unable to serialize audit payload", ex);
        }
    }

    private LocalDate resolveEventDate(Object timestampValue) {
        if (timestampValue instanceof String text) {
            try {
                return Instant.parse(text).atZone(ZoneOffset.UTC).toLocalDate();
            } catch (Exception ignored) {
                return LocalDate.now(ZoneOffset.UTC);
            }
        }
        return LocalDate.now(ZoneOffset.UTC);
    }

    private Long metadataLong(Map<String, Object> base, String key) {
        Map<String, Object> metadata = getMap(base, "metadata");
        return longValue(metadata.get(key));
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

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue()).setScale(2, java.math.RoundingMode.HALF_UP);
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return new BigDecimal(text.trim()).setScale(2, java.math.RoundingMode.HALF_UP);
            } catch (NumberFormatException ignored) {
                return BigDecimal.ZERO.setScale(2);
            }
        }
        return BigDecimal.ZERO.setScale(2);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
