package com.skillsync.notification.config;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "skillsync.webhooks")
public class WebhookProperties {

    private boolean enabled = false;
    private Map<String, String> endpoints = new HashMap<>();
}
