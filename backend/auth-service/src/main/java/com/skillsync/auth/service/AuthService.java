package com.skillsync.auth.service;

import com.skillsync.auth.config.JwtProperties;
import com.skillsync.auth.dto.AuthResponse;
import com.skillsync.auth.dto.InternalUserSummaryResponse;
import com.skillsync.auth.dto.LoginRequest;
import com.skillsync.auth.dto.RegisterRequest;
import com.skillsync.auth.dto.UserPrincipal;
import com.skillsync.auth.mapper.AuthMapper;
import com.skillsync.auth.model.RefreshToken;
import com.skillsync.auth.model.Role;
import com.skillsync.auth.model.User;
import com.skillsync.auth.repository.RefreshTokenRepository;
import com.skillsync.auth.repository.RoleRepository;
import com.skillsync.auth.repository.UserRepository;
import com.skillsync.common.exception.BadRequestException;
import com.skillsync.common.exception.ConflictException;
import com.skillsync.common.exception.ResourceNotFoundException;
import com.skillsync.common.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String DEFAULT_ROLE = "ROLE_LEARNER";
    private static final String LOCAL_PROVIDER = "LOCAL";
    private static final String DEFAULT_PRIVACY_POLICY_VERSION = "v1";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final EventPublisherService eventPublisherService;
    private final AuthMapper authMapper;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties,
            EventPublisherService eventPublisherService,
            AuthMapper authMapper
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.eventPublisherService = eventPublisherService;
        this.authMapper = authMapper;
    }

    @Transactional
    public AuthenticationResult register(RegisterRequest request, String deviceId, String userAgent, String ipAddress) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Email is already registered");
        }
        if (Boolean.FALSE.equals(request.getTermsAccepted())) {
            throw new BadRequestException("Terms must be accepted");
        }

        Role learnerRole = roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", DEFAULT_ROLE));

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName().trim())
                .authProvider(LOCAL_PROVIDER)
                .isActive(true)
                .isEmailVerified(false)
                .termsAcceptedAt(Instant.now())
                .privacyPolicyVersion(resolvePrivacyPolicyVersion(request.getPrivacyPolicyVersion()))
                .roles(new LinkedHashSet<>(List.of(learnerRole)))
                .build();

        User savedUser = userRepository.save(user);
        AuthenticationResult result = issueTokens(savedUser, deviceId, userAgent, ipAddress);
        eventPublisherService.publishUserRegistered(savedUser);
        return result;
    }

    @Transactional
    public AuthenticationResult login(LoginRequest request, String deviceId, String userAgent, String ipAddress) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", normalizedEmail));

        if (!user.isActive()) {
            throw new UnauthorizedException("User account is inactive");
        }
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        user.setLastLoginAt(Instant.now());
        User savedUser = userRepository.save(user);
        return issueTokens(savedUser, deviceId, userAgent, ipAddress);
    }

    @Transactional
    public AuthenticationResult refreshToken(String rawRefreshToken, String deviceId, String userAgent, String ipAddress) {
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (storedToken.getRevokedAt() != null || storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token is expired or revoked");
        }

        storedToken.setRevokedAt(Instant.now());
        refreshTokenRepository.save(storedToken);
        return issueTokens(storedToken.getUser(), deviceId, userAgent, ipAddress);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        storedToken.setRevokedAt(Instant.now());
        refreshTokenRepository.save(storedToken);
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public UserPrincipal validateAccessToken(String token) {
        Claims claims = jwtService.validateToken(token);
        return UserPrincipal.builder()
                .userId(claims.get("userId", Long.class))
                .email(claims.get("email", String.class))
                .name(claims.get("name", String.class))
                .roles((List<String>) claims.get("roles", List.class))
                .permissions((List<String>) claims.get("permissions", List.class))
                .build();
    }

    @Transactional(readOnly = true)
    public InternalUserSummaryResponse getInternalUserSummary(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        return InternalUserSummaryResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .roles(authMapper.extractRoles(user))
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }

    AuthenticationResult issueTokens(User user, String deviceId, String userAgent, String ipAddress) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(rawRefreshToken))
                .deviceId(deviceId)
                .userAgent(userAgent)
                .ipAddress(ipAddress)
                .expiresAt(Instant.now().plusMillis(jwtProperties.getRefreshTokenExpiryMs()))
                .build();
        refreshTokenRepository.save(refreshToken);

        AuthResponse response = AuthResponse.builder()
                .accessToken(accessToken)
                .user(authMapper.toUserInfo(user))
                .build();

        return new AuthenticationResult(response, rawRefreshToken);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String resolvePrivacyPolicyVersion(String privacyPolicyVersion) {
        if (!StringUtils.hasText(privacyPolicyVersion)) {
            return DEFAULT_PRIVACY_POLICY_VERSION;
        }
        return privacyPolicyVersion.trim();
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    public record AuthenticationResult(AuthResponse authResponse, String refreshToken) {
    }
}
