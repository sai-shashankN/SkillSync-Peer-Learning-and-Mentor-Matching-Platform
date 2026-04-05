
package com.skillsync.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.skillsync.auth.config.JwtProperties;
import com.skillsync.auth.dto.LoginRequest;
import com.skillsync.auth.dto.RegisterRequest;
import com.skillsync.auth.dto.UserInfo;
import com.skillsync.auth.mapper.AuthMapper;
import com.skillsync.auth.model.RefreshToken;
import com.skillsync.auth.model.Role;
import com.skillsync.auth.model.User;
import com.skillsync.auth.repository.RefreshTokenRepository;
import com.skillsync.auth.repository.RoleRepository;
import com.skillsync.auth.repository.UserRepository;
import com.skillsync.common.exception.ConflictException;
import com.skillsync.common.exception.UnauthorizedException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private EventPublisherService eventPublisherService;

    @Mock
    private AuthMapper authMapper;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setRefreshTokenExpiryMs(60_000L);
        authService = new AuthService(
                userRepository,
                roleRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtService,
                jwtProperties,
                eventPublisherService,
                authMapper
        );
    }

    @Test
    void registerCreatesLearnerIssuesTokensAndPublishesEvent() {
        RegisterRequest request = RegisterRequest.builder()
                .name("  Alice Johnson  ")
                .email("  Alice@Example.com  ")
                .password("Secret123!")
                .termsAccepted(true)
                .build();
        Role learnerRole = Role.builder()
                .id(1)
                .name("ROLE_LEARNER")
                .build();
        UserInfo userInfo = UserInfo.builder()
                .id(42L)
                .email("alice@example.com")
                .name("Alice Johnson")
                .roles(List.of("ROLE_LEARNER"))
                .permissions(List.of())
                .build();

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(roleRepository.findByName("ROLE_LEARNER")).thenReturn(Optional.of(learnerRole));
        when(passwordEncoder.encode("Secret123!")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(42L);
            return user;
        });
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(authMapper.toUserInfo(any(User.class))).thenReturn(userInfo);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthService.AuthenticationResult result = authService.register(
                request,
                "device-1",
                "Chrome",
                "127.0.0.1"
        );

        ArgumentCaptor<User> savedUserCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUserCaptor.capture());
        User savedUser = savedUserCaptor.getValue();

        assertThat(savedUser.getEmail()).isEqualTo("alice@example.com");
        assertThat(savedUser.getName()).isEqualTo("Alice Johnson");
        assertThat(savedUser.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(savedUser.isActive()).isTrue();
        assertThat(savedUser.isEmailVerified()).isFalse();
        assertThat(savedUser.getPrivacyPolicyVersion()).isEqualTo("v1");
        assertThat(savedUser.getRoles()).containsExactly(learnerRole);

        ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
        RefreshToken storedRefreshToken = refreshTokenCaptor.getValue();

        assertThat(storedRefreshToken.getUser()).isSameAs(savedUser);
        assertThat(storedRefreshToken.getDeviceId()).isEqualTo("device-1");
        assertThat(storedRefreshToken.getUserAgent()).isEqualTo("Chrome");
        assertThat(storedRefreshToken.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(storedRefreshToken.getTokenHash()).isNotBlank();
        assertThat(storedRefreshToken.getTokenHash()).isNotEqualTo(result.refreshToken());
        assertThat(storedRefreshToken.getExpiresAt()).isAfter(Instant.now());

        assertThat(result.authResponse().getAccessToken()).isEqualTo("access-token");
        assertThat(result.authResponse().getUser()).isEqualTo(userInfo);
        assertThat(result.refreshToken()).isNotBlank();

        verify(eventPublisherService).publishUserRegistered(savedUser);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        RegisterRequest request = RegisterRequest.builder()
                .name("Alice")
                .email("alice@example.com")
                .password("Secret123!")
                .termsAccepted(true)
                .build();

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request, "device-1", "Chrome", "127.0.0.1"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Email is already registered");

        verifyNoInteractions(roleRepository, refreshTokenRepository, jwtService, eventPublisherService, authMapper);
    }

    @Test
    void loginRejectsInvalidPassword() {
        LoginRequest request = LoginRequest.builder()
                .email("alice@example.com")
                .password("wrong-password")
                .build();
        User user = User.builder()
                .id(7L)
                .email("alice@example.com")
                .passwordHash("stored-password")
                .name("Alice")
                .isActive(true)
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "stored-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request, "device-1", "Chrome", "127.0.0.1"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void refreshTokenRevokesCurrentTokenAndIssuesReplacement() {
        User user = User.builder()
                .id(9L)
                .email("alice@example.com")
                .name("Alice")
                .isActive(true)
                .build();
        RefreshToken existingToken = RefreshToken.builder()
                .user(user)
                .tokenHash("stored-hash")
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        UserInfo userInfo = UserInfo.builder()
                .id(9L)
                .email("alice@example.com")
                .name("Alice")
                .roles(List.of("ROLE_LEARNER"))
                .permissions(List.of())
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(existingToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateAccessToken(user)).thenReturn("replacement-access-token");
        when(authMapper.toUserInfo(user)).thenReturn(userInfo);

        AuthService.AuthenticationResult result = authService.refreshToken(
                "raw-refresh-token",
                "device-2",
                "Firefox",
                "10.0.0.5"
        );

        ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(refreshTokenCaptor.capture());

        RefreshToken revokedToken = refreshTokenCaptor.getAllValues().get(0);
        RefreshToken replacementToken = refreshTokenCaptor.getAllValues().get(1);

        assertThat(revokedToken.getRevokedAt()).isNotNull();
        assertThat(replacementToken.getUser()).isSameAs(user);
        assertThat(replacementToken.getDeviceId()).isEqualTo("device-2");
        assertThat(replacementToken.getUserAgent()).isEqualTo("Firefox");
        assertThat(replacementToken.getIpAddress()).isEqualTo("10.0.0.5");
        assertThat(replacementToken.getTokenHash()).isNotBlank();
        assertThat(replacementToken.getTokenHash()).isNotEqualTo(result.refreshToken());

        assertThat(result.authResponse().getAccessToken()).isEqualTo("replacement-access-token");
        assertThat(result.authResponse().getUser()).isEqualTo(userInfo);
    }
}
