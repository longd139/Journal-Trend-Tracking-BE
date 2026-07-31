package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.auth.AuthResponse;
import com.sra.journal_tracking.dto.auth.GoogleLoginRequest;
import com.sra.journal_tracking.dto.auth.LoginRequest;
import com.sra.journal_tracking.dto.auth.RefreshTokenRequest;
import com.sra.journal_tracking.dto.auth.RegisterRequest;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse googleLogin(GoogleLoginRequest request);
    AuthResponse refreshToken(RefreshTokenRequest request);
    void logout(String token);
    void verifyEmail(String token);
    void resendVerification(String email);
    void forgotPassword(String email);
    void resetPassword(String token, String newPassword);
}
