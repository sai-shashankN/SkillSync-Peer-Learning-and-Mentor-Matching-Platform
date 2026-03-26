package com.skillsync.notification.service;

import com.skillsync.notification.config.WebhookProperties;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookForwarderService {

    private final WebhookProperties webhookProperties;
    private final RestTemplate restTemplate;

    public void forwardEvent(String eventType, Map<String, Object> payload) {
        if (!webhookProperties.isEnabled()) {
            return;
        }

        String url = webhookProperties.getEndpoints().get(eventType);
        if (url == null) {
            log.debug("No webhook endpoint configured for eventType={}", eventType);
            return;
        }

        try {
            restTemplate.postForEntity(url, payload, String.class);
            log.info("Forwarded event {} to webhook {}", eventType, url);
        } catch (Exception ex) {
            log.error("Failed to forward event {} to webhook {}: {}", eventType, url, ex.getMessage());
        }
    }
}
