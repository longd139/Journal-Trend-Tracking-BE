package com.sra.journal_tracking.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sra.journal_tracking.dto.auth.AuthResponse;
import com.sra.journal_tracking.dto.auth.ForgotPasswordRequest;
import com.sra.journal_tracking.dto.auth.GoogleLoginRequest;
import com.sra.journal_tracking.dto.auth.LoginRequest;
import com.sra.journal_tracking.dto.auth.RegisterRequest;
import com.sra.journal_tracking.dto.auth.ResetPasswordRequest;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication APIs")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register new account", description = "Create a new user account and return a JWT token.")
    @ApiResponse(responseCode = "200", description = "Register successful")
    @PostMapping("/register")
    public ResponseEntity<AppResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthResponse authResponse = authService.register(request);
        return ResponseEntity.ok(AppResponse.success("Register successful", authResponse));
    }

    @Operation(summary = "Login", description = "Authenticate user and return JWT token.")
    @ApiResponse(responseCode = "200", description = "Login successful")
    @PostMapping("/login")
    public ResponseEntity<AppResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(AppResponse.success("Login successful", authResponse));
    }

    @Operation(summary = "Google login", description = "Authenticate user via Google OAuth ID token. If the user does not exist, creates a new account automatically. Returns a JWT token for subsequent API calls.")
    @ApiResponse(responseCode = "200", description = "Google login successful")
    @PostMapping("/google")
    public ResponseEntity<AppResponse<AuthResponse>> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request) {
        AuthResponse authResponse = authService.googleLogin(request);
        return ResponseEntity.ok(AppResponse.success("Google login successful", authResponse));
    }

    @Operation(summary = "Logout", description = "Invalidate current JWT token")
    @ApiResponse(responseCode = "200", description = "Logout successful")
    @PostMapping("/logout")
    public ResponseEntity<AppResponse<Void>> logout(
            @RequestHeader(value = "Authorization", required = false) String token) {

        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        authService.logout(token);
        return ResponseEntity.ok(AppResponse.success("Logged out successfully"));
    }

    @Operation(summary = "Verify email", description = "Verify email address using token from registration email")
    @ApiResponse(responseCode = "200", description = "Email verified successfully")
    @GetMapping("/verify-email")
    public ResponseEntity<AppResponse<Void>> verifyEmail(@RequestParam("token") String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(AppResponse.success("Email verified successfully! You can now login."));
    }

    @Operation(summary = "Forgot password", description = "Send password reset link to email. Check terminal for the link when testing.")
    @ApiResponse(responseCode = "200", description = "Reset link sent")
    @PostMapping("/forgot-password")
    public ResponseEntity<AppResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(AppResponse.success("If the email exists, a password reset link has been sent."));
    }

    @Operation(summary = "Reset password", description = "Reset password using token from forgot password email")
    @ApiResponse(responseCode = "200", description = "Password reset successful")
    @PostMapping("/reset-password")
    public ResponseEntity<AppResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(AppResponse.success("Password reset successfully! You can now login with your new password."));
    }
}
