package com.skillsync.common.security;

import com.skillsync.common.exception.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.util.StringUtils;

public final class InternalServiceAuth {

    public static final String HEADER_NAME = "X-Internal-Service-Token";

    private InternalServiceAuth() {
    }

    public static void requireValidToken(String providedToken, String expectedToken) {
        if (!StringUtils.hasText(expectedToken)
                || !StringUtils.hasText(providedToken)
                || !MessageDigest.isEqual(
                        expectedToken.getBytes(StandardCharsets.UTF_8),
                        providedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException("Valid internal service token is required");
        }
    }
}
