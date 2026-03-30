package com.skillsync.auth.controller;

import com.skillsync.auth.config.JwtProperties;
import com.skillsync.auth.dto.AuthResponse;
import com.skillsync.auth.dto.GitHubOAuth2Request;
import com.skillsync.auth.dto.GoogleOAuth2Request;
import com.skillsync.auth.dto.InternalUserSummaryResponse;
import com.skillsync.auth.dto.LoginRequest;
import com.skillsync.auth.dto.RegisterRequest;
import com.skillsync.auth.dto.UserPrincipal;
import com.skillsync.auth.service.AuthService;
import com.skillsync.auth.service.OAuth2Service;
import com.skillsync.common.dto.ApiResponse;
import com.skillsync.common.exception.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    private static final String DEVICE_ID_HEADER = "X-Device-Id";

    private final AuthService authService;
    private final OAuth2Service oAuth2Service;
    private final JwtProperties jwtProperties;
    private final boolean refreshCookieSecure;

    public AuthController(
            AuthService authService,
            OAuth2Service oAuth2Service,
            JwtProperties jwtProperties,
            @Value("${app.security.refresh-cookie-secure:true}") boolean refreshCookieSecure
    ) {
        this.authService = authService;
        this.oAuth2Service = oAuth2Service;
        this.jwtProperties = jwtProperties;
        this.refreshCookieSecure = refreshCookieSecure;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            @RequestHeader(value = DEVICE_ID_HEADER, required = false) String deviceId,
            HttpServletRequest httpRequest
    ) {
        AuthService.AuthenticationResult result = authService.register(
                request,
                deviceId,
                httpRequest.getHeader(HttpHeaders.USER_AGENT),
                httpRequest.getRemoteAddr()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(result.refreshToken()).toString())
                .body(ApiResponse.ok("User registered successfully", result.authResponse()));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = DEVICE_ID_HEADER, required = false) String deviceId,
            HttpServletRequest httpRequest
    ) {
        AuthService.AuthenticationResult result = authService.login(
                request,
                deviceId,
                httpRequest.getHeader(HttpHeaders.USER_AGENT),
                httpRequest.getRemoteAddr()
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(result.refreshToken()).toString())
                .body(ApiResponse.ok("Login successful", result.authResponse()));
    }

    @PostMapping("/oauth2/google")
    public ResponseEntity<ApiResponse<AuthResponse>> loginWithGoogle(
            @Valid @RequestBody GoogleOAuth2Request request,
            @RequestHeader(value = DEVICE_ID_HEADER, required = false) String deviceId,
            HttpServletRequest httpRequest
    ) {
        AuthService.AuthenticationResult result = oAuth2Service.loginWithGoogle(
                request,
                deviceId,
                httpRequest.getHeader(HttpHeaders.USER_AGENT),
                httpRequest.getRemoteAddr()
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(result.refreshToken()).toString())
                .body(ApiResponse.ok("Google login successful", result.authResponse()));
    }

    @PostMapping("/oauth2/github")
    public ResponseEntity<ApiResponse<AuthResponse>> loginWithGithub(
            @Valid @RequestBody GitHubOAuth2Request request,
            @RequestHeader(value = DEVICE_ID_HEADER, required = false) String deviceId,
            HttpServletRequest httpRequest
    ) {
        AuthService.AuthenticationResult result = oAuth2Service.loginWithGitHub(
                request,
                deviceId,
                httpRequest.getHeader(HttpHeaders.USER_AGENT),
                httpRequest.getRemoteAddr()
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(result.refreshToken()).toString())
                .body(ApiResponse.ok("GitHub login successful", result.authResponse()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(value = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            @RequestHeader(value = DEVICE_ID_HEADER, required = false) String deviceId,
            HttpServletRequest httpRequest
    ) {
        validateCookieBasedRequest(refreshToken);
        AuthService.AuthenticationResult result = authService.refreshToken(
                refreshToken,
                deviceId,
                httpRequest.getHeader(HttpHeaders.USER_AGENT),
                httpRequest.getRemoteAddr()
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(result.refreshToken()).toString())
                .body(ApiResponse.ok("Token refreshed successfully", result.authResponse()));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(value = REFRESH_TOKEN_COOKIE, required = false) String refreshToken
    ) {
        validateCookieBasedRequest(refreshToken);
        authService.logout(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearRefreshTokenCookie().toString())
                .body(ApiResponse.<Void>ok("Logout successful", null));
    }

    @GetMapping("/validate")
    public ResponseEntity<ApiResponse<UserPrincipal>> validate(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        String token = extractBearerToken(authorizationHeader);
        UserPrincipal principal = authService.validateAccessToken(token);
        return ResponseEntity.ok(ApiResponse.ok("Token is valid", principal));
    }

    @GetMapping("/internal/users/{id}")
    public ResponseEntity<ApiResponse<InternalUserSummaryResponse>> getInternalUserById(@PathVariable Long id) {
        InternalUserSummaryResponse response = authService.getInternalUserSummary(id);
        return ResponseEntity.ok(ApiResponse.ok("Internal user fetched successfully", response));
    }

    private void validateCookieBasedRequest(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new BadRequestException("Refresh token cookie is required");
        }
    }

    private String extractBearerToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            throw new BadRequestException("Authorization header must contain a Bearer token");
        }
        return authorizationHeader.substring(7);
    }

    private ResponseCookie buildRefreshTokenCookie(String refreshToken) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Strict")
                .path("/")
                .maxAge(jwtProperties.getRefreshTokenExpiryMs() / 1000)
                .build();
    }

    private ResponseCookie clearRefreshTokenCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
    }
}
