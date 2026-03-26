package com.skillsync.review.util;

import com.skillsync.common.exception.BadRequestException;
import com.skillsync.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import org.springframework.util.StringUtils;

public final class RequestHeaderUtils {

    private RequestHeaderUtils() {
    }

    public static Long extractUserId(HttpServletRequest request) {
        String userIdHeader = request.getHeader("X-User-Id");
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BadRequestException("X-User-Id header is required");
        }

        try {
            return Long.parseLong(userIdHeader.trim());
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Invalid X-User-Id header");
        }
    }

    public static List<String> extractRoles(HttpServletRequest request) {
        String rolesHeader = request.getHeader("X-User-Roles");
        if (!StringUtils.hasText(rolesHeader)) {
            return List.of();
        }

        return Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    public static boolean hasAdminRole(HttpServletRequest request) {
        return extractRoles(request).stream()
                .anyMatch(role -> "ADMIN".equalsIgnoreCase(role) || "ROLE_ADMIN".equalsIgnoreCase(role));
    }

    public static void requireAdmin(HttpServletRequest request) {
        if (!hasAdminRole(request)) {
            throw new UnauthorizedException("ADMIN role is required");
        }
    }
}
