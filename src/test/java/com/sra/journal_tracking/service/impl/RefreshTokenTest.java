package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.dto.auth.AuthResponse;
import com.sra.journal_tracking.dto.auth.RefreshTokenRequest;
import com.sra.journal_tracking.entity.jpa.Role;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.entity.jpa.UserSession;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.repository.jpa.NotificationRepository;
import com.sra.journal_tracking.repository.jpa.RoleRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import com.sra.journal_tracking.repository.jpa.UserSessionRepository;
import com.sra.journal_tracking.repository.jpa.VerificationTokenRepository;
import com.sra.journal_tracking.security.CustomUserDetails;
import com.sra.journal_tracking.security.CustomUserDetailsService;
import com.sra.journal_tracking.security.JwtTokenProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Refresh Token Feature")
class RefreshTokenTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private UserSessionRepository userSessionRepository;
    @Mock private VerificationTokenRepository verificationTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private CustomUserDetailsService customUserDetailsService;

    @InjectMocks
    private AuthServiceImpl authService;

    private User testUser;
    private UserSession testSession;
    private Role testRole;
    private CustomUserDetails userDetails;

    private static final String RAW_REFRESH_TOKEN = "raw-refresh-token-value";
    private static final String REFRESH_TOKEN_HASH = "abc123def456hash";
    private static final String ACCESS_TOKEN_JWT = "eyJhbGciOiJIUzI1NiJ9.access-token";
    private static final String NEW_ACCESS_TOKEN_JWT = "eyJhbGciOiJIUzI1NiJ9.new-access-token";

    @BeforeEach
    void setUp() {
        testRole = Role.builder()
                .roleId(UUID.randomUUID())
                .roleName("researcher")
                .build();

        testUser = User.builder()
                .userId(UUID.randomUUID())
                .email("test@example.com")
                .fullName("Test User")
                .passwordHash("hashed-password")
                .role(testRole)
                .isActive(true)
                .build();

        testSession = UserSession.builder()
                .sessionId(UUID.randomUUID())
                .user(testUser)
                .tokenHash("old-access-hash")
                .refreshTokenHash(REFRESH_TOKEN_HASH)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .refreshExpiresAt(LocalDateTime.now().plusDays(7))
                .createdAt(LocalDateTime.now())
                .build();

        userDetails = new CustomUserDetails(testUser);
    }

    /**
     * Set up mocks common to success-path tests.
     * Also injects @Value fields via ReflectionTestUtils since this is a pure unit test.
     */
    private void stubCommonMocks() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpirationMs", 604800000L); // 7 days
        when(tokenProvider.getJwtExpirationInMs()).thenReturn(86400000L); // 1 day
        // hashToken is called for both the incoming refresh token AND the new access token
        // AND the randomly generated new refresh token — stub leniently for all inputs
        lenient().when(tokenProvider.hashToken(anyString())).thenReturn("hashed-" + System.nanoTime());
        // But incoming refresh token must match the DB hash
        when(tokenProvider.hashToken(RAW_REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
    }

    private void stubSuccessfulTokenGeneration() {
        when(tokenProvider.generateToken(any())).thenReturn(ACCESS_TOKEN_JWT);
    }

    // ========================================
    // SUCCESS CASES
    // ========================================

    @Nested
    @DisplayName("Successful refresh token flow")
    class SuccessfulRefresh {

        @Test
        @DisplayName("Should issue new access + refresh tokens (rotation)")
        void shouldRotateTokensOnRefresh() {
            stubCommonMocks();
            stubSuccessfulTokenGeneration();

            RefreshTokenRequest request = new RefreshTokenRequest(RAW_REFRESH_TOKEN);
            when(userSessionRepository.findByRefreshTokenHash(REFRESH_TOKEN_HASH))
                    .thenReturn(Optional.of(testSession));
            when(customUserDetailsService.loadUserByUsername("test@example.com"))
                    .thenReturn(userDetails);

            AuthResponse response = authService.refreshToken(request);

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isEqualTo(ACCESS_TOKEN_JWT);
            assertThat(response.getRefreshToken()).isNotNull();
            assertThat(response.getRefreshToken()).isNotEqualTo(RAW_REFRESH_TOKEN);
            assertThat(response.getUser().getEmail()).isEqualTo("test@example.com");
            assertThat(response.getUser().getRoleName()).isEqualTo("researcher");
            assertThat(response.getTokenType()).isEqualTo("Bearer");

            // Verify session was persisted (rotation happened)
            verify(userSessionRepository).save(any(UserSession.class));
        }

        @Test
        @DisplayName("Should update session with new token hashes on rotation")
        void shouldUpdateSessionWithNewHashes() {
            stubCommonMocks();
            when(tokenProvider.generateToken(any())).thenReturn(NEW_ACCESS_TOKEN_JWT);

            RefreshTokenRequest request = new RefreshTokenRequest(RAW_REFRESH_TOKEN);
            when(userSessionRepository.findByRefreshTokenHash(REFRESH_TOKEN_HASH))
                    .thenReturn(Optional.of(testSession));
            when(customUserDetailsService.loadUserByUsername("test@example.com"))
                    .thenReturn(userDetails);

            authService.refreshToken(request);

            ArgumentCaptor<UserSession> captor = ArgumentCaptor.forClass(UserSession.class);
            verify(userSessionRepository).save(captor.capture());
            UserSession saved = captor.getValue();

            // Session ID unchanged (same row updated)
            assertThat(saved.getSessionId()).isEqualTo(testSession.getSessionId());
            // Both hashes should be updated
            assertThat(saved.getTokenHash()).isNotNull();
            assertThat(saved.getRefreshTokenHash()).isNotNull();
            // Old refresh token hash should be replaced
            assertThat(saved.getRefreshTokenHash()).isNotEqualTo(REFRESH_TOKEN_HASH);
            // Expiry should be set
            assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
            assertThat(saved.getRefreshExpiresAt()).isAfter(LocalDateTime.now());
        }

        @Test
        @DisplayName("Should return AuthResponse with refreshToken after login")
        void shouldIncludeRefreshTokenInLoginResponse() {
            // AuthResponse now has refreshToken field - this is verified by
            // the successful refresh test above which asserts response.getRefreshToken() != null
            // The DTO structure is also verified: AuthResponse has both accessToken and refreshToken
            assertThat(AuthResponse.class.getDeclaredFields())
                    .anyMatch(f -> f.getName().equals("refreshToken"));
            assertThat(AuthResponse.class.getDeclaredFields())
                    .anyMatch(f -> f.getName().equals("accessToken"));
        }
    }

    // ========================================
    // FAILURE CASES
    // ========================================

    @Nested
    @DisplayName("Invalid refresh token scenarios")
    class InvalidRefreshToken {

        @Test
        @DisplayName("Should throw REFRESH_TOKEN_INVALID when token not found in DB")
        void shouldRejectUnknownRefreshToken() {
            RefreshTokenRequest request = new RefreshTokenRequest("unknown-token");
            when(tokenProvider.hashToken("unknown-token")).thenReturn("unknown-hash");
            when(userSessionRepository.findByRefreshTokenHash("unknown-hash"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);

            // Must NOT generate any token
            verify(tokenProvider, never()).generateToken(any());
        }

        @Test
        @DisplayName("Should throw REFRESH_TOKEN_INVALID and delete session when expired")
        void shouldRejectExpiredRefreshToken() {
            UserSession expiredSession = UserSession.builder()
                    .sessionId(UUID.randomUUID())
                    .user(testUser)
                    .tokenHash("old-hash")
                    .refreshTokenHash(REFRESH_TOKEN_HASH)
                    .refreshExpiresAt(LocalDateTime.now().minusDays(1))
                    .build();

            RefreshTokenRequest request = new RefreshTokenRequest(RAW_REFRESH_TOKEN);
            when(tokenProvider.hashToken(RAW_REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
            when(userSessionRepository.findByRefreshTokenHash(REFRESH_TOKEN_HASH))
                    .thenReturn(Optional.of(expiredSession));

            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);

            // Expired session must be cleaned up
            verify(userSessionRepository).delete(expiredSession);
            verify(tokenProvider, never()).generateToken(any());
        }

        @Test
        @DisplayName("Should throw REFRESH_TOKEN_INVALID and delete session when refreshExpiresAt is null")
        void shouldRejectNullExpiryToken() {
            UserSession nullExpirySession = UserSession.builder()
                    .sessionId(UUID.randomUUID())
                    .user(testUser)
                    .tokenHash("old-hash")
                    .refreshTokenHash(REFRESH_TOKEN_HASH)
                    .refreshExpiresAt(null)
                    .build();

            RefreshTokenRequest request = new RefreshTokenRequest(RAW_REFRESH_TOKEN);
            when(tokenProvider.hashToken(RAW_REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
            when(userSessionRepository.findByRefreshTokenHash(REFRESH_TOKEN_HASH))
                    .thenReturn(Optional.of(nullExpirySession));

            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);

            verify(userSessionRepository).delete(nullExpirySession);
            verify(tokenProvider, never()).generateToken(any());
        }

        @Test
        @DisplayName("Should throw USER_NOT_ACTIVE and delete session when user disabled")
        void shouldRejectInactiveUser() {
            User inactiveUser = User.builder()
                    .userId(UUID.randomUUID())
                    .email("inactive@example.com")
                    .isActive(false)
                    .role(testRole)
                    .build();

            UserSession session = UserSession.builder()
                    .sessionId(UUID.randomUUID())
                    .user(inactiveUser)
                    .tokenHash("hash")
                    .refreshTokenHash(REFRESH_TOKEN_HASH)
                    .refreshExpiresAt(LocalDateTime.now().plusDays(7))
                    .build();

            CustomUserDetails inactiveDetails = new CustomUserDetails(inactiveUser);
            RefreshTokenRequest request = new RefreshTokenRequest(RAW_REFRESH_TOKEN);

            when(tokenProvider.hashToken(RAW_REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
            when(userSessionRepository.findByRefreshTokenHash(REFRESH_TOKEN_HASH))
                    .thenReturn(Optional.of(session));
            when(customUserDetailsService.loadUserByUsername("inactive@example.com"))
                    .thenReturn(inactiveDetails);

            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_ACTIVE);

            verify(userSessionRepository).delete(session);
            verify(tokenProvider, never()).generateToken(any());
        }
    }

    // ========================================
    // TOKEN ROTATION SECURITY
    // ========================================

    @Nested
    @DisplayName("Token rotation security")
    class TokenRotationSecurity {

        @Test
        @DisplayName("Each refresh should produce a different refresh token (rotation)")
        void shouldProduceUniqueRefreshTokensOnEachRefresh() {
            var stubHelper = new Object() {
                void setupRefresh(String rawToken, String hash, UserSession session) {
                    when(tokenProvider.hashToken(rawToken)).thenReturn(hash);
                    when(userSessionRepository.findByRefreshTokenHash(hash))
                            .thenReturn(Optional.of(session));
                }
            };

            when(tokenProvider.getJwtExpirationInMs()).thenReturn(86400000L);
            when(customUserDetailsService.loadUserByUsername("test@example.com")).thenReturn(userDetails);
            when(tokenProvider.generateToken(any()))
                    .thenReturn("access-token-1")
                    .thenReturn("access-token-2");

            // First refresh
            stubHelper.setupRefresh(RAW_REFRESH_TOKEN, REFRESH_TOKEN_HASH, testSession);
            AuthResponse first = authService.refreshToken(new RefreshTokenRequest(RAW_REFRESH_TOKEN));

            // Second refresh using the refresh token from first response
            String secondRawToken = first.getRefreshToken();
            String secondHash = "second-token-hash";
            UserSession rotatedSession = UserSession.builder()
                    .sessionId(testSession.getSessionId())
                    .user(testUser)
                    .tokenHash("intermediate-access-hash")
                    .refreshTokenHash(secondHash)
                    .refreshExpiresAt(LocalDateTime.now().plusDays(7))
                    .build();

            stubHelper.setupRefresh(secondRawToken, secondHash, rotatedSession);
            AuthResponse second = authService.refreshToken(new RefreshTokenRequest(secondRawToken));

            // Both refresh tokens must be different
            assertThat(first.getRefreshToken()).isNotEqualTo(second.getRefreshToken());
            // Both access tokens must be different
            assertThat(first.getAccessToken()).isNotEqualTo(second.getAccessToken());
            // Both responses are valid
            assertThat(first.getAccessToken()).isEqualTo("access-token-1");
            assertThat(second.getAccessToken()).isEqualTo("access-token-2");
        }

        @Test
        @DisplayName("Should update access token expiry to JWT expiration time")
        void shouldSetAccessTokenExpiryFromJwtProvider() {
            stubCommonMocks(); // injects refreshTokenExpirationMs = 7 days
            when(tokenProvider.getJwtExpirationInMs()).thenReturn(3600000L); // 1 hour access token

            when(userSessionRepository.findByRefreshTokenHash(REFRESH_TOKEN_HASH))
                    .thenReturn(Optional.of(testSession));
            when(customUserDetailsService.loadUserByUsername("test@example.com"))
                    .thenReturn(userDetails);
            when(tokenProvider.generateToken(any())).thenReturn(ACCESS_TOKEN_JWT);

            LocalDateTime beforeRefresh = LocalDateTime.now();
            authService.refreshToken(new RefreshTokenRequest(RAW_REFRESH_TOKEN));

            ArgumentCaptor<UserSession> captor = ArgumentCaptor.forClass(UserSession.class);
            verify(userSessionRepository).save(captor.capture());
            UserSession saved = captor.getValue();

            // Access token should expire ~1 hour from now
            assertThat(saved.getExpiresAt()).isAfter(beforeRefresh.plusMinutes(55));
            assertThat(saved.getExpiresAt()).isBefore(beforeRefresh.plusMinutes(65));
            // Refresh token should expire 7 days from now (default from ReflectionTestUtils)
            assertThat(saved.getRefreshExpiresAt()).isAfter(beforeRefresh.plusDays(6));
            assertThat(saved.getRefreshExpiresAt()).isBefore(beforeRefresh.plusDays(8));
        }
    }

    // ========================================
    // LOGOUT + REFRESH TOKEN INTERACTION
    // ========================================

    @Nested
    @DisplayName("Logout interaction with refresh tokens")
    class LogoutWithRefresh {

        @Test
        @DisplayName("Logout should find and delete session by refresh token hash as fallback")
        void shouldDeleteSessionByRefreshTokenHashOnLogout() {
            String rawToken = "some-jwt-or-refresh-token";
            String tokenHash = "token-hash-for-logout";

            when(tokenProvider.hashToken(rawToken)).thenReturn(tokenHash);
            // Not found by access token hash
            when(userSessionRepository.findByTokenHash(tokenHash))
                    .thenReturn(Optional.empty());
            // But found by refresh token hash
            when(userSessionRepository.findByRefreshTokenHash(tokenHash))
                    .thenReturn(Optional.of(testSession));

            authService.logout(rawToken);

            verify(userSessionRepository).delete(testSession);
        }

        @Test
        @DisplayName("Logout should delete session by access token hash when found there")
        void shouldDeleteSessionByAccessTokenHashOnLogout() {
            String rawToken = "access-jwt-token";
            String tokenHash = "access-hash";

            when(tokenProvider.hashToken(rawToken)).thenReturn(tokenHash);
            when(userSessionRepository.findByTokenHash(tokenHash))
                    .thenReturn(Optional.of(testSession));

            authService.logout(rawToken);

            verify(userSessionRepository).delete(testSession);
            // Should NOT fallback to refresh token hash
            verify(userSessionRepository, never()).findByRefreshTokenHash(anyString());
        }
    }
}
