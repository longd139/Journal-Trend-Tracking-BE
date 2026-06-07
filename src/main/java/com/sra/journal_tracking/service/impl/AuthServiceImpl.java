package com.sra.journal_tracking.service.impl;

import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sra.journal_tracking.dto.auth.AuthResponse;
import com.sra.journal_tracking.dto.auth.LoginRequest;
import com.sra.journal_tracking.dto.auth.RegisterRequest;
import com.sra.journal_tracking.entity.jpa.Role;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.entity.jpa.UserSession;
import com.sra.journal_tracking.entity.jpa.VerificationToken;
import com.sra.journal_tracking.entity.jpa.VerificationToken.TokenType;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.repository.jpa.RoleRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import com.sra.journal_tracking.repository.jpa.UserSessionRepository;
import com.sra.journal_tracking.repository.jpa.VerificationTokenRepository;
import com.sra.journal_tracking.security.JwtTokenProvider;
import com.sra.journal_tracking.service.AuthService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

        private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

        private final UserRepository userRepository;
        private final RoleRepository roleRepository;
        private final UserSessionRepository userSessionRepository;
        private final VerificationTokenRepository verificationTokenRepository;
        private final PasswordEncoder passwordEncoder;
        private final AuthenticationManager authenticationManager;
        private final JwtTokenProvider tokenProvider;

        @Value("${app.frontend-url:http://localhost:3000}")
        private String frontendUrl;

        @Value("${app.verification-token-expiration-ms:86400000}")
        private long verificationTokenExpirationMs;

        @Value("${app.reset-token-expiration-ms:900000}")
        private long resetTokenExpirationMs;

        @Override
        @Transactional
        public AuthResponse register(RegisterRequest request) {
                if (userRepository.existsByEmail(request.getEmail())) {
                        throw new AppException(ErrorCode.USER_EXISTED);
                }

                String roleName = request.getRole() != null ? request.getRole() : "academic_user";
                Role role = roleRepository.findByRoleNameIgnoreCase(roleName)
                                .orElseThrow(() -> new RuntimeException("Role not found."));

                User user = User.builder()
                                .fullName(request.getFullName())
                                .email(request.getEmail())
                                .passwordHash(passwordEncoder.encode(request.getPassword()))
                                .institution(request.getInstitution())
                                .role(role)
                                .isActive(false) // Email chưa verify
                                .build();

                userRepository.save(user);

                // Tạo verification token và log link ra terminal
                createAndLogVerificationToken(user);

                // Authenticate và trả JWT ngay để user vào app, xác thực email sau
                Authentication authentication = authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

                String jwt = tokenProvider.generateToken(authentication);
                saveUserSession(user, jwt);

                return buildAuthResponse(jwt, user);
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

                saveUserSession(user, jwt);

                return buildAuthResponse(jwt, user);
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
                        // Đã verify rồi, vẫn đánh dấu token đã dùng
                        verificationToken.setIsUsed(true);
                        verificationTokenRepository.save(verificationToken);
                        throw new AppException(ErrorCode.EMAIL_ALREADY_VERIFIED);
                }

                // Kích hoạt tài khoản
                user.setIsActive(true);
                userRepository.save(user);

                // Đánh dấu token đã dùng
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

                // ============================================
                // LOG LINK RA TERMINAL ĐỂ TEST VỚI EMAIL ẢO
                // ============================================
                String resetLink = frontendUrl + "/reset-password?token=" + tokenValue;
                log.info("============================================");
                log.info("📧 PASSWORD RESET LINK (copy link bên dưới):");
                log.info("   {}", resetLink);
                log.info("   Token: {}", tokenValue);
                log.info("   Email: {}", email);
                log.info("   Hết hạn: {}", expiresAt);
                log.info("============================================");
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

        private void createAndLogVerificationToken(User user) {
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

                // ============================================
                // LOG LINK RA TERMINAL ĐỂ TEST VỚI EMAIL ẢO
                // ============================================
                String verificationLink = frontendUrl + "/verify-email?token=" + tokenValue;
                log.info("============================================");
                log.info("📧 EMAIL VERIFICATION LINK (copy link bên dưới):");
                log.info("   {}", verificationLink);
                log.info("   Token: {}", tokenValue);
                log.info("   Email: {}", user.getEmail());
                log.info("   Hết hạn: {}", expiresAt);
                log.info("============================================");
        }

        private void saveUserSession(User user, String jwt) {
                UserSession session = UserSession.builder()
                                .user(user)
                                .tokenHash(tokenProvider.hashToken(jwt))
                                .createdAt(LocalDateTime.now())
                                .expiresAt(LocalDateTime.now().plusDays(1))
                                .build();
                userSessionRepository.save(session);
        }

        @Override
        @Transactional
        public void logout(String token) {
                if (token != null) {
                        String tokenHash = tokenProvider.hashToken(token);
                        userSessionRepository.findByTokenHash(tokenHash)
                                        .ifPresent(userSessionRepository::delete);
                }
        }

        private AuthResponse buildAuthResponse(String token, User user) {
                return AuthResponse.builder()
                                .accessToken(token)
                                .tokenType("Bearer")
                                .user(AuthResponse.UserAuthInfo.builder()
                                                .id(user.getUserId().toString())
                                                .fullName(user.getFullName())
                                                .email(user.getEmail())
                                                .roleName(user.getRole().getRoleName())
                                                .build())
                                .build();
        }
}
