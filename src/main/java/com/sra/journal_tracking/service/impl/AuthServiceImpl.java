package com.sra.journal_tracking.service.impl;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.sra.journal_tracking.dto.auth.AuthResponse;
import com.sra.journal_tracking.dto.auth.GoogleLoginRequest;
import com.sra.journal_tracking.dto.auth.LoginRequest;
import com.sra.journal_tracking.dto.auth.RefreshTokenRequest;
import com.sra.journal_tracking.dto.auth.RegisterRequest;
import com.sra.journal_tracking.entity.jpa.Notification;
import com.sra.journal_tracking.entity.jpa.NotificationType;
import com.sra.journal_tracking.entity.jpa.Role;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.entity.jpa.UserSession;
import com.sra.journal_tracking.entity.jpa.VerificationToken;
import com.sra.journal_tracking.entity.jpa.VerificationToken.TokenType;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.repository.jpa.NotificationRepository;
import com.sra.journal_tracking.repository.jpa.RoleRepository;
import com.sra.journal_tracking.service.NotificationEventPublisher;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import com.sra.journal_tracking.repository.jpa.UserSessionRepository;
import com.sra.journal_tracking.repository.jpa.VerificationTokenRepository;
import com.sra.journal_tracking.security.CustomUserDetails;
import com.sra.journal_tracking.security.CustomUserDetailsService;
import com.sra.journal_tracking.security.JwtTokenProvider;
import com.sra.journal_tracking.service.AuthService;
import com.sra.journal_tracking.service.EmailService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

        private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
        private static final SecureRandom SECURE_RANDOM = new SecureRandom();

        private final UserRepository userRepository;
        private final RoleRepository roleRepository;
        private final NotificationRepository notificationRepository;
        private final NotificationEventPublisher eventPublisher;
        private final UserSessionRepository userSessionRepository;
        private final VerificationTokenRepository verificationTokenRepository;
        private final PasswordEncoder passwordEncoder;
        private final AuthenticationManager authenticationManager;
        private final JwtTokenProvider tokenProvider;
        private final CustomUserDetailsService customUserDetailsService;
        private final EmailService emailService;

        @Value("${app.frontend-url:http://localhost:3000}")
        private String frontendUrl;

        @Value("${app.verification-token-expiration-ms:86400000}")
        private long verificationTokenExpirationMs;

        @Value("${app.reset-token-expiration-ms:900000}")
        private long resetTokenExpirationMs;

        @Value("${app.refresh-token-expiration-ms:604800000}")
        private long refreshTokenExpirationMs;

        @Value("${app.google-client-id:}")
        private String googleClientId;

        @Override
        @Transactional
        public AuthResponse googleLogin(GoogleLoginRequest request) {
                log.info(">>> GOOGLE LOGIN called, credential length={}",
                                request.getCredential() != null ? request.getCredential().length() : 0);
                // 1. Verify Google ID token
                Map<String, Object> payload = verifyGoogleToken(request.getCredential());

                String email = (String) payload.get("email");
                String name = (String) payload.get("name");

                log.info(">>> GOOGLE LOGIN: email from token = '{}'", email);
                if (email == null || email.isBlank()) {
                        log.warn(">>> GOOGLE LOGIN FAILED: email is null or blank in payload");
                        throw new AppException(ErrorCode.GOOGLE_TOKEN_INVALID);
                }

                log.info("Google login: email={}, name={}", email, name);

                // 2. Find or create user
                User user = userRepository.findByEmail(email).orElse(null);

                if (user == null) {
                        // Create new user from Google account → default RESEARCHER for 3 days
                        Role role = roleRepository.findByRoleNameIgnoreCase("researcher")
                                        .orElseThrow(() -> new RuntimeException("Role not found."));

                        user = User.builder()
                                        .fullName(name != null ? name : email)
                                        .email(email)
                                        .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                                        .institution((String) payload.getOrDefault("hd", null))
                                        .role(role)
                                        .roleExpiryAt(LocalDateTime.now().plusDays(3)) // Auto-downgrade after 3 days
                                        .isActive(true) // Google accounts are pre-verified
                                        .build();

                        userRepository.save(user);
                        log.info("Created new user from Google: {}", email);
                } else {
                        // Update last login time and name if changed
                        user.setLastLoginAt(LocalDateTime.now());
                        if (name != null && !name.isBlank() && !name.equals(user.getFullName())) {
                                user.setFullName(name);
                        }
                        userRepository.save(user);
                }

                // 3. Generate JWT (Google-authenticated, no password needed)
                CustomUserDetails userDetails = new CustomUserDetails(user);
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);

                String jwt = tokenProvider.generateToken(authentication);
                TokenPair tokenPair = createUserSession(user, jwt);

                return buildAuthResponse(jwt, tokenPair.refreshToken(), user);
        }

        /**
         * Verify a Google token.
         * Supports both ID token (JWT) and access token (opaque).
         * - ID token: decoded locally, audience verified against googleClientId.
         * - Access token: validated by calling Google's userinfo endpoint.
         */
        @SuppressWarnings("unchecked")
        private Map<String, Object> verifyGoogleToken(String token) {
                log.info("=== GOOGLE LOGIN V2: verifying token, length={} ===", token.length());

                // Detect token type: JWT has 3 dot-separated parts, access token does not
                boolean looksLikeJwt = token.chars().filter(c -> c == '.').count() >= 2;

                if (looksLikeJwt) {
                        return verifyIdToken(token);
                } else {
                        return verifyAccessToken(token);
                }
        }

        /**
         * Verify a standard Google ID token (JWT) by local decode.
         */
        @SuppressWarnings("unchecked")
        private Map<String, Object> verifyIdToken(String idToken) {
                log.info(">>> Verifying as ID token (JWT), preview={}...",
                                idToken.substring(0, Math.min(50, idToken.length())));
                try {
                        String[] parts = idToken.split("\\.");
                        if (parts.length < 2) {
                                log.warn("Google ID token has invalid JWT format");
                                throw new AppException(ErrorCode.GOOGLE_TOKEN_INVALID);
                        }

                        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
                        Map<String, Object> payload = new com.fasterxml.jackson.databind.ObjectMapper()
                                        .readValue(payloadJson, Map.class);

                        if (payload == null || payload.isEmpty()) {
                                log.warn("Google ID token payload is empty");
                                throw new AppException(ErrorCode.GOOGLE_TOKEN_INVALID);
                        }

                        // Check expiration
                        Object expObj = payload.get("exp");
                        if (expObj instanceof Number) {
                                long exp = ((Number) expObj).longValue();
                                if (System.currentTimeMillis() / 1000 > exp) {
                                        log.warn("Google ID token expired at {}", exp);
                                        throw new AppException(ErrorCode.GOOGLE_TOKEN_INVALID);
                                }
                        }

                        // Verify audience (client ID)
                        if (googleClientId != null && !googleClientId.isBlank()) {
                                Object audObj = payload.get("aud");
                                String aud = audObj instanceof String ? (String) audObj : null;
                                if (aud != null && !googleClientId.equals(aud)) {
                                        log.warn("Google ID token audience mismatch: expected={}, got={}",
                                                        googleClientId, aud);
                                        throw new AppException(ErrorCode.GOOGLE_TOKEN_INVALID);
                                }
                        }

                        log.info("Google ID token valid: email={}, aud={}, iss={}",
                                        payload.get("email"), payload.get("aud"), payload.get("iss"));
                        return payload;

                } catch (AppException e) {
                        throw e;
                } catch (Exception e) {
                        log.error("Failed to decode Google ID token: {}", e.getMessage());
                        throw new AppException(ErrorCode.GOOGLE_TOKEN_INVALID);
                }
        }

        /**
         * Verify a Google access token by calling the userinfo endpoint.
         * This handles the OAuth implicit flow where only access_token is returned.
         */
        @SuppressWarnings("unchecked")
        private Map<String, Object> verifyAccessToken(String accessToken) {
                log.info(">>> Verifying as access token (opaque) via Google userinfo API");
                try {
                        RestTemplate restTemplate = new RestTemplate();
                        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                        headers.setBearerAuth(accessToken);
                        org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);

                        ResponseEntity<String> response = restTemplate.exchange(
                                        "https://www.googleapis.com/oauth2/v3/userinfo",
                                        org.springframework.http.HttpMethod.GET,
                                        entity,
                                        String.class);

                        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                                log.warn("Google userinfo API returned status={}", response.getStatusCode());
                                throw new AppException(ErrorCode.GOOGLE_TOKEN_INVALID);
                        }

                        Map<String, Object> userInfo = new com.fasterxml.jackson.databind.ObjectMapper()
                                        .readValue(response.getBody(), Map.class);

                        log.info("Google userinfo API success: email={}, name={}",
                                        userInfo.get("email"), userInfo.get("name"));

                        return userInfo;

                } catch (AppException e) {
                        throw e;
                } catch (Exception e) {
                        log.error("Failed to verify Google access token: {}", e.getMessage());
                        throw new AppException(ErrorCode.GOOGLE_TOKEN_INVALID);
                }
        }

        @Override
        @Transactional
        public AuthResponse register(RegisterRequest request) {
                if (userRepository.existsByEmail(request.getEmail())) {
                        throw new AppException(ErrorCode.USER_EXISTED);
                }

                Role role;
                String requestedRole = request.getRoleName();
                if (requestedRole != null && !requestedRole.isBlank()) {
                        String normalized = requestedRole.trim().toLowerCase();
                        role = roleRepository.findByRoleNameIgnoreCase(normalized)
                                        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
                } else {
                        role = roleRepository.findByRoleNameIgnoreCase("academic_user")
                                        .orElseThrow(() -> new RuntimeException("Role not found."));
                }

                User user = User.builder()
                                .fullName(request.getFullName())
                                .email(request.getEmail())
                                .passwordHash(passwordEncoder.encode(request.getPassword()))
                                .institution(request.getInstitution())
                                .role(role)
                                .isActive(true) // Auto-active — no email verification required
                                .build();

                // If registering as researcher, set 3-day trial
                if ("researcher".equalsIgnoreCase(role.getRoleName())) {
                        user.setRoleExpiryAt(LocalDateTime.now().plusDays(3));
                        log.info("Researcher trial set for {}: expires at {}", request.getEmail(), user.getRoleExpiryAt());
                }

                user = userRepository.saveAndFlush(user);

                // Validate: role must match what was requested
                if (!role.getRoleName().equalsIgnoreCase(user.getRole().getRoleName())) {
                        log.error("ROLE MISMATCH after save! Requested={}, Actual={}",
                                        role.getRoleName(), user.getRole().getRoleName());
                        throw new AppException(ErrorCode.INVALID_CREDENTIALS);
                }

                createResearcherTrialNotification(user);

                // Generate verification token and send email
                createAndSendVerificationToken(user);

                log.info("User registered: email={}, role={}, roleExpiryAt={}",
                                user.getEmail(), user.getRole().getRoleName(), user.getRoleExpiryAt());

                // Tạo Authentication trực tiếp từ user vừa lưu — không gọi authenticate()
                // để tránh trigger loadUserByUsername() khi transaction chưa commit
                CustomUserDetails userDetails = new CustomUserDetails(user);
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);

                String jwt = tokenProvider.generateToken(authentication);
                TokenPair tokenPair = createUserSession(user, jwt);

                return buildAuthResponse(jwt, tokenPair.refreshToken(), user);
        }

        @Override
        @Transactional
        public AuthResponse login(LoginRequest request) {
                Authentication authentication = authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                String jwt = tokenProvider.generateToken(authentication);

                User user = userRepository.findByEmail(request.getEmail())
                                .orElseThrow(() -> new RuntimeException("User not found"));

                log.info("User logged in: email={}, role={}, roleExpiryAt={}",
                                user.getEmail(), user.getRole().getRoleName(), user.getRoleExpiryAt());

                TokenPair tokenPair = createUserSession(user, jwt);
                return buildAuthResponse(jwt, tokenPair.refreshToken(), user);
        }

        @Override
        @Transactional
        public AuthResponse refreshToken(RefreshTokenRequest request) {
                String refreshTokenHash = tokenProvider.hashToken(request.getRefreshToken());
                UserSession session = userSessionRepository.findByRefreshTokenHash(refreshTokenHash)
                                .orElseThrow(() -> new AppException(ErrorCode.REFRESH_TOKEN_INVALID));

                if (session.getRefreshExpiresAt() == null
                                || session.getRefreshExpiresAt().isBefore(LocalDateTime.now())) {
                        userSessionRepository.delete(session);
                        throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID);
                }

                CustomUserDetails userDetails = (CustomUserDetails) customUserDetailsService
                                .loadUserByUsername(session.getUser().getEmail());
                if (!userDetails.isEnabled()) {
                        userSessionRepository.delete(session);
                        throw new AppException(ErrorCode.USER_NOT_ACTIVE);
                }

                User user = userDetails.getUser();
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                String jwt = tokenProvider.generateToken(authentication);
                TokenPair tokenPair = rotateUserSession(session, jwt);

                return buildAuthResponse(jwt, tokenPair.refreshToken(), user);
        }

        @Override
        @Transactional
        public void verifyEmail(String token) {
                VerificationToken verificationToken = verificationTokenRepository.findByToken(token)
                                .orElseThrow(() -> new AppException(ErrorCode.VERIFICATION_TOKEN_INVALID));

                if (verificationToken.getIsUsed()) {
                        throw new AppException(ErrorCode.VERIFICATION_TOKEN_INVALID);
                }

                if (verificationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
                        throw new AppException(ErrorCode.VERIFICATION_TOKEN_EXPIRED);
                }

                if (verificationToken.getTokenType() != TokenType.EMAIL_VERIFICATION) {
                        throw new AppException(ErrorCode.VERIFICATION_TOKEN_INVALID);
                }

                User user = verificationToken.getUser();

                if (user.getIsActive()) {
                        // Already active — idempotent: just mark token as used
                        verificationToken.setIsUsed(true);
                        verificationTokenRepository.save(verificationToken);
                        log.info("Email already verified for user: {} — token marked as used", user.getEmail());
                        return;
                }

                // Activate account
                user.setIsActive(true);
                userRepository.save(user);

                // Mark token as used
                verificationToken.setIsUsed(true);
                verificationTokenRepository.save(verificationToken);

                log.info("✅ Email verified successfully for user: {}", user.getEmail());
        }

        @Override
        @Transactional
        public void forgotPassword(String email) {
                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

                // Vô hiệu hóa các token reset password cũ của user này
                verificationTokenRepository.invalidatePreviousTokens(user.getUserId(), TokenType.PASSWORD_RESET);

                // Tạo token reset password mới
                String tokenValue = UUID.randomUUID().toString();
                LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(resetTokenExpirationMs / 1000);

                VerificationToken resetToken = VerificationToken.builder()
                                .user(user)
                                .token(tokenValue)
                                .tokenType(TokenType.PASSWORD_RESET)
                                .expiresAt(expiresAt)
                                .isUsed(false)
                                .build();

                verificationTokenRepository.save(resetToken);

                // Send real email + log fallback for dev testing
                String resetLink = frontendUrl + "/reset-password?token=" + tokenValue;
                emailService.sendPasswordResetEmail(email, user.getFullName(), resetLink);

                log.info("Password reset requested for {}", email);
        }

        @Override
        @Transactional
        public void resetPassword(String token, String newPassword) {
                VerificationToken resetToken = verificationTokenRepository.findByToken(token)
                                .orElseThrow(() -> new AppException(ErrorCode.VERIFICATION_TOKEN_INVALID));

                if (resetToken.getIsUsed()) {
                        throw new AppException(ErrorCode.VERIFICATION_TOKEN_INVALID);
                }

                if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
                        throw new AppException(ErrorCode.VERIFICATION_TOKEN_EXPIRED);
                }

                if (resetToken.getTokenType() != TokenType.PASSWORD_RESET) {
                        throw new AppException(ErrorCode.VERIFICATION_TOKEN_INVALID);
                }

                User user = resetToken.getUser();

                // Cập nhật mật khẩu mới
                user.setPasswordHash(passwordEncoder.encode(newPassword));
                userRepository.save(user);

                // Đánh dấu token đã dùng
                resetToken.setIsUsed(true);
                verificationTokenRepository.save(resetToken);

                log.info("✅ Password reset successfully for user: {}", user.getEmail());
        }

        // ============================================
        // PRIVATE HELPER METHODS
        // ============================================

        private void createAndSendVerificationToken(User user) {
                String tokenValue = UUID.randomUUID().toString();
                LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(verificationTokenExpirationMs / 1000);

                VerificationToken verificationToken = VerificationToken.builder()
                                .user(user)
                                .token(tokenValue)
                                .tokenType(TokenType.EMAIL_VERIFICATION)
                                .expiresAt(expiresAt)
                                .isUsed(false)
                                .build();

                verificationTokenRepository.save(verificationToken);

                // Send real email + log fallback for dev testing
                String verificationLink = frontendUrl + "/verify-email?token=" + tokenValue;
                emailService.sendVerificationEmail(user.getEmail(), user.getFullName(), verificationLink);
        }

        private TokenPair createUserSession(User user, String jwt) {
                String refreshToken = generateRefreshToken();
                LocalDateTime now = LocalDateTime.now();
                UserSession session = UserSession.builder()
                                .user(user)
                                .tokenHash(tokenProvider.hashToken(jwt))
                                .refreshTokenHash(tokenProvider.hashToken(refreshToken))
                                .createdAt(now)
                                .expiresAt(now.plus(Duration.ofMillis(tokenProvider.getJwtExpirationInMs())))
                                .refreshExpiresAt(now.plus(Duration.ofMillis(refreshTokenExpirationMs)))
                                .build();
                userSessionRepository.save(session);
                return new TokenPair(jwt, refreshToken);
        }

        private TokenPair rotateUserSession(UserSession session, String jwt) {
                String refreshToken = generateRefreshToken();
                LocalDateTime now = LocalDateTime.now();
                session.setTokenHash(tokenProvider.hashToken(jwt));
                session.setRefreshTokenHash(tokenProvider.hashToken(refreshToken));
                session.setExpiresAt(now.plus(Duration.ofMillis(tokenProvider.getJwtExpirationInMs())));
                session.setRefreshExpiresAt(now.plus(Duration.ofMillis(refreshTokenExpirationMs)));
                userSessionRepository.save(session);
                return new TokenPair(jwt, refreshToken);
        }

        private String generateRefreshToken() {
                byte[] randomBytes = new byte[64];
                SECURE_RANDOM.nextBytes(randomBytes);
                return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        }

        private void createResearcherTrialNotification(User user) {
                if (user.getRole() == null || !"researcher".equalsIgnoreCase(user.getRole().getRoleName())) {
                        return;
                }

                Notification notification = notificationRepository.save(Notification.builder()
                                .user(user)
                                .type(NotificationType.SYSTEM)
                                .title("Researcher trial activated")
                                .message("Your Researcher trial is active for 3 days.")
                                .isRead(false)
                                .build());
                log.info("Researcher trial notification created for {}", user.getEmail());

                // Push to connected SSE clients
                try {
                        eventPublisher.publish(user.getUserId(), notification);
                } catch (Exception ignored) { /* best-effort */ }
        }

        @Override
        @Transactional
        public void logout(String token) {
                if (token != null) {
                        String tokenHash = tokenProvider.hashToken(token);
                        userSessionRepository.findByTokenHash(tokenHash)
                                        .or(() -> userSessionRepository.findByRefreshTokenHash(tokenHash))
                                        .ifPresent(userSessionRepository::delete);
                }
        }

        private AuthResponse buildAuthResponse(String token, String refreshToken, User user) {
                return AuthResponse.builder()
                                .accessToken(token)
                                .refreshToken(refreshToken)
                                .tokenType(token != null ? "Bearer" : null)
                                .user(AuthResponse.UserAuthInfo.builder()
                                                .id(user.getUserId().toString())
                                                .fullName(user.getFullName())
                                                .email(user.getEmail())
                                                .roleName(user.getRole().getRoleName())
                                                .roleExpiryAt(user.getRoleExpiryAt())
                                                .build())
                                .build();
        }

        private record TokenPair(String accessToken, String refreshToken) {
        }
}
