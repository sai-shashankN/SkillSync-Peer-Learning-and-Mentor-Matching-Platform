package com.skillsync.notification.service;

import com.skillsync.common.config.RabbitMQConstants;
import com.skillsync.common.security.InternalServiceAuth;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventPayloadEnricher {

    private final RestTemplate restTemplate;

    @Value("${skillsync.services.user-service-url:http://localhost:8082}")
    private String userServiceUrl;

    @Value("${skillsync.services.session-service-url:http://localhost:8085}")
    private String sessionServiceUrl;

    @Value("${internal.service-token:}")
    private String internalServiceToken;

    public Map<String, Object> enrich(String eventType, Map<String, Object> payload) {
        Map<String, Object> enriched = new LinkedHashMap<>(payload);

        switch (eventType) {
            case RabbitMQConstants.RK_SESSION_CANCELLED -> enrichSessionCancelled(enriched);
            case RabbitMQConstants.RK_PAYMENT_RECEIVED -> enrichUserEmail(enriched, "payerId", "payerEmail");
            case RabbitMQConstants.RK_PAYMENT_REFUNDED -> enrichPaymentRefunded(enriched);
            case RabbitMQConstants.RK_WAITLIST_SLOT_OPEN -> enrichUserEmail(enriched, "learnerId", "learnerEmail");
            default -> {
            }
        }

        return enriched;
    }

    private void enrichSessionCancelled(Map<String, Object> payload) {
        Map<String, Object> session = fetchSession(longValue(payload.get("sessionId")));
        if (session.isEmpty()) {
            return;
        }

        copyIfPresent(payload, session, "mentorId", "mentorProfileId");
        copyIfPresent(payload, session, "mentorUserId", "mentorId");
        copyIfPresent(payload, session, "learnerId");
        enrichUserEmail(payload, "mentorId", "mentorEmail");
        enrichUserEmail(payload, "learnerId", "learnerEmail");
    }

    private void enrichPaymentRefunded(Map<String, Object> payload) {
        if (!payload.containsKey("payerId")) {
            Map<String, Object> session = fetchSession(longValue(payload.get("sessionId")));
            copyIfPresent(payload, session, "learnerId", "payerId");
        }
        enrichUserEmail(payload, "payerId", "payerEmail");
    }

    private void enrichUserEmail(Map<String, Object> payload, String userIdKey, String emailKey) {
        if (hasText(payload.get(emailKey))) {
            return;
        }

        Map<String, Object> user = fetchUser(longValue(payload.get(userIdKey)));
        Object email = user.get("email");
        if (hasText(email)) {
            payload.put(emailKey, email);
        }
    }

    private Map<String, Object> fetchSession(Long sessionId) {
        return fetchApiData(sessionId, sessionServiceUrl + "/sessions/internal/" + sessionId, "session");
    }

    private Map<String, Object> fetchUser(Long userId) {
        return fetchApiData(userId, userServiceUrl + "/users/internal/" + userId, "user");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchApiData(Long id, String url, String resourceName) {
        if (id == null) {
            return Map.of();
        }

        try {
            ResponseEntity<Map<String, Object>> responseEntity = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(internalHeaders()),
                    new ParameterizedTypeReference<>() {
                    }
            );
            Map<String, Object> response = responseEntity.getBody();
            if (response != null && response.get("data") instanceof Map<?, ?> data) {
                return (Map<String, Object>) data;
            }
        } catch (RestClientException ex) {
            log.warn("Could not enrich notification payload from {} id={}: {}", resourceName, id, ex.getMessage());
        }

        return Map.of();
    }

    private void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        copyIfPresent(target, source, key, key);
    }

    private void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String sourceKey, String targetKey) {
        if (target.get(targetKey) != null) {
            return;
        }

        Object value = source.get(sourceKey);
        if (value != null) {
            target.put(targetKey, value);
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

    private boolean hasText(Object value) {
        return value instanceof String text && !text.isBlank();
    }

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(InternalServiceAuth.HEADER_NAME, internalServiceToken);
        return headers;
    }
}
