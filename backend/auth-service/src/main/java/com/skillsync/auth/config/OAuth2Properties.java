package com.skillsync.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "oauth2")
public class OAuth2Properties {

    private ProviderProperties google = new ProviderProperties();
    private ProviderProperties github = new ProviderProperties();

    @Data
    public static class ProviderProperties {
        private String clientId;
        private String clientSecret;
    }
}
