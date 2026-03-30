package com.skillsync.auth.service;

import com.skillsync.auth.config.OAuth2Properties;
import com.skillsync.auth.dto.GitHubOAuth2Request;
import com.skillsync.auth.dto.GoogleOAuth2Request;
import com.skillsync.auth.model.Role;
import com.skillsync.auth.model.User;
import com.skillsync.auth.repository.RoleRepository;
import com.skillsync.auth.repository.UserRepository;
import com.skillsync.common.exception.ConflictException;
import com.skillsync.common.exception.ResourceNotFoundException;
import com.skillsync.common.exception.UnauthorizedException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class OAuth2Service {

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuth2Service.class);
    private static final String DEFAULT_ROLE = "ROLE_LEARNER";
    private static final String LOCAL_PROVIDER = "LOCAL";
    private static final String GOOGLE_PROVIDER = "GOOGLE";
    private static final String GITHUB_PROVIDER = "GITHUB";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EventPublisherService eventPublisherService;
    private final AuthService authService;
    private final OAuth2Properties oAuth2Properties;
    private final RestClient restClient;

    public OAuth2Service(
            UserRepository userRepository,
            RoleRepository roleRepository,
            EventPublisherService eventPublisherService,
            AuthService authService,
            OAuth2Properties oAuth2Properties,
            RestClient.Builder restClientBuilder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.eventPublisherService = eventPublisherService;
        this.authService = authService;
        this.oAuth2Properties = oAuth2Properties;
        this.restClient = restClientBuilder.build();
    }

    @Transactional
    public AuthService.AuthenticationResult loginWithGoogle(
            GoogleOAuth2Request request,
            String deviceId,
            String userAgent,
            String ipAddress
    ) {
        Map<String, Object> tokenInfo = verifyGoogleIdToken(request.getIdToken());
        String email = normalizeEmail(getRequiredString(tokenInfo, "email"));
        String name = getRequiredString(tokenInfo, "name");
        String providerId = getRequiredString(tokenInfo, "sub");
        boolean emailVerified = Boolean.parseBoolean(String.valueOf(tokenInfo.get("email_verified")));

        if (!emailVerified) {
            throw new UnauthorizedException("Google email is not verified");
        }

        String audience = String.valueOf(tokenInfo.getOrDefault("aud", ""));
        if (StringUtils.hasText(oAuth2Properties.getGoogle().getClientId())
                && !oAuth2Properties.getGoogle().getClientId().equals(audience)) {
            LOGGER.error("Google token audience mismatch! Expected: {}, Actual: {}", oAuth2Properties.getGoogle().getClientId(), audience);
            throw new UnauthorizedException("Google token audience is invalid: expected " + oAuth2Properties.getGoogle().getClientId() + ", got " + audience);
        }

        UserResolution resolution = resolveSocialUser(email, name, providerId, GOOGLE_PROVIDER);
        User user = resolution.user();
        user.setLastLoginAt(Instant.now());
        User savedUser = userRepository.save(user);

        if (resolution.newUser()) {
            eventPublisherService.publishUserRegistered(savedUser);
        }

        return authService.issueTokens(savedUser, deviceId, userAgent, ipAddress);
    }

    @Transactional
    public AuthService.AuthenticationResult loginWithGitHub(
            GitHubOAuth2Request request,
            String deviceId,
            String userAgent,
            String ipAddress
    ) {
        String accessToken = exchangeGithubCodeForAccessToken(request.getCode());
        Map<String, Object> githubUser = fetchGithubUser(accessToken);
        List<Map<String, Object>> githubEmails = fetchGithubEmails(accessToken);

        Map<String, Object> primaryEmail = githubEmails.stream()
                .filter(email -> Boolean.TRUE.equals(email.get("primary")) && Boolean.TRUE.equals(email.get("verified")))
                .findFirst()
                .or(() -> githubEmails.stream()
                        .filter(email -> Boolean.TRUE.equals(email.get("verified")))
                        .findFirst())
                .orElseThrow(() -> new UnauthorizedException("GitHub account does not have a verified email"));

        String email = normalizeEmail(String.valueOf(primaryEmail.get("email")));
        String login = String.valueOf(githubUser.getOrDefault("login", ""));
        String name = StringUtils.hasText(String.valueOf(githubUser.get("name")))
                ? String.valueOf(githubUser.get("name"))
                : login;
        String providerId = String.valueOf(githubUser.get("id"));

        if (!StringUtils.hasText(email) || !StringUtils.hasText(providerId) || !StringUtils.hasText(name)) {
            throw new UnauthorizedException("GitHub account response is incomplete");
        }

        UserResolution resolution = resolveSocialUser(email, name, providerId, GITHUB_PROVIDER);
        User user = resolution.user();
        user.setLastLoginAt(Instant.now());
        User savedUser = userRepository.save(user);

        if (resolution.newUser()) {
            eventPublisherService.publishUserRegistered(savedUser);
        }

        return authService.issueTokens(savedUser, deviceId, userAgent, ipAddress);
    }

    private Map<String, Object> verifyGoogleIdToken(String idToken) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri("https://oauth2.googleapis.com/tokeninfo?id_token={idToken}", idToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return response != null ? response : Map.of();
        } catch (RestClientException ex) {
            throw new UnauthorizedException("Failed to verify Google ID token");
        }
    }

    private String exchangeGithubCodeForAccessToken(String code) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri("https://github.com/login/oauth/access_token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "client_id", oAuth2Properties.getGithub().getClientId(),
                            "client_secret", oAuth2Properties.getGithub().getClientSecret(),
                            "code", code
                    ))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            String accessToken = response != null ? String.valueOf(response.get("access_token")) : null;
            if (!StringUtils.hasText(accessToken)) {
                throw new UnauthorizedException("GitHub access token exchange failed");
            }
            return accessToken;
        } catch (RestClientException ex) {
            throw new UnauthorizedException("Failed to exchange GitHub authorization code");
        }
    }

    private Map<String, Object> fetchGithubUser(String accessToken) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri("https://api.github.com/user")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return response != null ? response : Map.of();
        } catch (RestClientException ex) {
            throw new UnauthorizedException("Failed to fetch GitHub user profile");
        }
    }

    private List<Map<String, Object>> fetchGithubEmails(String accessToken) {
        try {
            List<Map<String, Object>> response = restClient.get()
                    .uri("https://api.github.com/user/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return response != null ? response : List.of();
        } catch (RestClientException ex) {
            throw new UnauthorizedException("Failed to fetch GitHub user emails");
        }
    }

    private UserResolution resolveSocialUser(String email, String name, String providerId, String provider) {
        return userRepository.findByEmail(email)
                .map(existingUser -> resolveExistingUser(existingUser, providerId, provider))
                .orElseGet(() -> new UserResolution(createNewUser(email, name, providerId, provider), true));
    }

    private UserResolution resolveExistingUser(User existingUser, String providerId, String provider) {
        if (!existingUser.isActive()) {
            throw new UnauthorizedException("User account is inactive");
        }

        if (LOCAL_PROVIDER.equals(existingUser.getAuthProvider())) {
            LOGGER.warn(
                    "OAuth2 login linked to existing LOCAL account for email={}; provider={}",
                    existingUser.getEmail(),
                    provider
            );
            existingUser.setProviderId(providerId);
            existingUser.setEmailVerified(true);
            if (existingUser.getEmailVerifiedAt() == null) {
                existingUser.setEmailVerifiedAt(Instant.now());
            }
            return new UserResolution(existingUser, false);
        }

        if (!provider.equals(existingUser.getAuthProvider())) {
            throw new ConflictException("Email already linked to another provider");
        }

        if (StringUtils.hasText(existingUser.getProviderId()) && !providerId.equals(existingUser.getProviderId())) {
            throw new ConflictException("Email already linked to another provider account");
        }

        existingUser.setProviderId(providerId);
        existingUser.setEmailVerified(true);
        if (existingUser.getEmailVerifiedAt() == null) {
            existingUser.setEmailVerifiedAt(Instant.now());
        }
        return new UserResolution(existingUser, false);
    }

    private User createNewUser(String email, String name, String providerId, String provider) {
        Role learnerRole = roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", DEFAULT_ROLE));

        return User.builder()
                .email(email)
                .name(name.trim())
                .authProvider(provider)
                .providerId(providerId)
                .isActive(true)
                .isEmailVerified(true)
                .emailVerifiedAt(Instant.now())
                .roles(new LinkedHashSet<>(List.of(learnerRole)))
                .build();
    }

    private String getRequiredString(Map<String, Object> payload, String key) {
        String value = String.valueOf(payload.getOrDefault(key, ""));
        if (!StringUtils.hasText(value)) {
            throw new UnauthorizedException("OAuth2 provider response is incomplete");
        }
        return value;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private record UserResolution(User user, boolean newUser) {
    }
}
