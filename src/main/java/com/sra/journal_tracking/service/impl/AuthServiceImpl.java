package com.sra.journal_tracking.service.impl;

import java.time.LocalDateTime;

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
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.repository.jpa.RoleRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import com.sra.journal_tracking.repository.jpa.UserSessionRepository;
import com.sra.journal_tracking.security.JwtTokenProvider;
import com.sra.journal_tracking.service.AuthService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

        private final UserRepository userRepository;
        private final RoleRepository roleRepository;
        private final UserSessionRepository userSessionRepository;
        private final PasswordEncoder passwordEncoder;
        private final AuthenticationManager authenticationManager;
        private final JwtTokenProvider tokenProvider;

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
                                .isActive(true)
                                .build();

                userRepository.save(user);

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

        private void saveUserSession(User user, String jwt) {
                UserSession session = UserSession.builder()
                                .user(user)
                                .tokenHash(tokenProvider.hashToken(jwt)) // store SHA-256 hash of token for security
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
