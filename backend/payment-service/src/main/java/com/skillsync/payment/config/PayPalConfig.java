package com.skillsync.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "paypal")
public class PayPalConfig {

    private String clientId;
    private String clientSecret;
    private String baseUrl;
    private String currency = "INR";
    private String brandName = "SkillSync";
}
