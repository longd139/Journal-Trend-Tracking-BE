package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.auth.AuthResponse;
import com.sra.journal_tracking.dto.auth.LoginRequest;
import com.sra.journal_tracking.dto.auth.RegisterRequest;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    void logout(String token);
}
