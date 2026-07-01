package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.auth.AuthResponse;
import com.sra.journal_tracking.dto.auth.ForgotPasswordRequest;
import com.sra.journal_tracking.dto.auth.GoogleLoginRequest;
import com.sra.journal_tracking.dto.auth.LoginRequest;
import com.sra.journal_tracking.dto.auth.RegisterRequest;
import com.sra.journal_tracking.dto.auth.ResetPasswordRequest;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController Unit Tests")
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private GoogleLoginRequest googleLoginRequest;
    private ForgotPasswordRequest forgotPasswordRequest;
    private ResetPasswordRequest resetPasswordRequest;
    private AuthResponse authResponse;

    @BeforeEach
    void setUp() {
        registerRequest = RegisterRequest.builder()
                .fullName("Test User")
                .email("test@example.com")
                .password("password123")
                .institution("Test University")
                .roleName("academic_user")
                .build();

        loginRequest = LoginRequest.builder()
                .email("test@example.com")
                .password("password123")
                .build();

        googleLoginRequest = GoogleLoginRequest.builder()
                .credential("google-credential-token")
                .build();

        forgotPasswordRequest = ForgotPasswordRequest.builder()
                .email("test@example.com")
                .build();

        resetPasswordRequest = ResetPasswordRequest.builder()
                .token("reset-token-123")
                .newPassword("newPassword456")
                .build();

        authResponse = AuthResponse.builder()
                .accessToken("jwt-token-abc")
                .tokenType("Bearer")
                .user(AuthResponse.UserAuthInfo.builder()
                        .id("user-id-1")
                        .fullName("Test User")
                        .email("test@example.com")
                        .roleName("ACADEMIC_USER")
                        .build())
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    //  REGISTER
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/auth/register")
    class RegisterTests {

        @Test
        @DisplayName("Should register successfully and return AuthResponse")
        void register_WithValidRequest_ReturnsAuthResponse() {
            // Arrange
            when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse);

            // Act
            ResponseEntity<AppResponse<AuthResponse>> result = authController.register(registerRequest);

            // Assert
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertEquals(200, result.getBody().getStatus());
            assertEquals("Register successful", result.getBody().getMessage());
            assertNotNull(result.getBody().getData());
            assertEquals("jwt-token-abc", result.getBody().getData().getAccessToken());
            assertEquals("Test User", result.getBody().getData().getUser().getFullName());
            verify(authService).register(registerRequest);
        }

        @Test
        @DisplayName("Should throw AppException when email already exists")
        void register_WhenEmailExists_ThrowsAppException() {
            // Arrange
            when(authService.register(any(RegisterRequest.class)))
                    .thenThrow(new AppException(ErrorCode.USER_EXISTED));

            // Act & Assert
            AppException exception = assertThrows(AppException.class,
                    () -> authController.register(registerRequest));
            assertEquals(ErrorCode.USER_EXISTED, exception.getErrorCode());
            assertEquals("Email is already in use!", exception.getMessage());
        }

        @Test
        @DisplayName("Should register with minimal fields (no institution, no role)")
        void register_WithMinimalFields_ReturnsAuthResponse() {
            // Arrange
            RegisterRequest minimalRequest = RegisterRequest.builder()
                    .fullName("Min User")
                    .email("min@example.com")
                    .password("pass")
                    .build();
            when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse);

            // Act
            ResponseEntity<AppResponse<AuthResponse>> result = authController.register(minimalRequest);

            // Assert
            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(authService).register(minimalRequest);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  LOGIN
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/auth/login")
    class LoginTests {

        @Test
        @DisplayName("Should login successfully and return AuthResponse")
        void login_WithValidCredentials_ReturnsAuthResponse() {
            // Arrange
            when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

            // Act
            ResponseEntity<AppResponse<AuthResponse>> result = authController.login(loginRequest);

            // Assert
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertEquals("Login successful", result.getBody().getMessage());
            assertEquals("jwt-token-abc", result.getBody().getData().getAccessToken());
            verify(authService).login(loginRequest);
        }

        @Test
        @DisplayName("Should throw AppException when credentials are invalid")
        void login_WithInvalidCredentials_ThrowsAppException() {
            // Arrange
            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new AppException(ErrorCode.INVALID_CREDENTIALS));

            // Act & Assert
            AppException exception = assertThrows(AppException.class,
                    () -> authController.login(loginRequest));
            assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
        }

        @Test
        @DisplayName("Should throw AppException when user is not active")
        void login_WhenUserNotActive_ThrowsAppException() {
            // Arrange
            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new AppException(ErrorCode.USER_NOT_ACTIVE));

            // Act & Assert
            AppException exception = assertThrows(AppException.class,
                    () -> authController.login(loginRequest));
            assertEquals(ErrorCode.USER_NOT_ACTIVE, exception.getErrorCode());
        }

        @Test
        @DisplayName("Should throw AppException when user not found")
        void login_WhenUserNotFound_ThrowsAppException() {
            // Arrange
            LoginRequest unknownRequest = LoginRequest.builder()
                    .email("unknown@example.com")
                    .password("pass")
                    .build();
            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new AppException(ErrorCode.USER_NOT_FOUND));

            // Act & Assert
            AppException exception = assertThrows(AppException.class,
                    () -> authController.login(unknownRequest));
            assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  GOOGLE LOGIN
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/auth/google")
    class GoogleLoginTests {

        @Test
        @DisplayName("Should authenticate via Google successfully")
        void googleLogin_WithValidCredential_ReturnsAuthResponse() {
            // Arrange
            when(authService.googleLogin(any(GoogleLoginRequest.class))).thenReturn(authResponse);

            // Act
            ResponseEntity<AppResponse<AuthResponse>> result = authController.googleLogin(googleLoginRequest);

            // Assert
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals("Google login successful", result.getBody().getMessage());
            verify(authService).googleLogin(googleLoginRequest);
        }

        @Test
        @DisplayName("Should throw AppException when Google token is invalid")
        void googleLogin_WithInvalidToken_ThrowsAppException() {
            // Arrange
            when(authService.googleLogin(any(GoogleLoginRequest.class)))
                    .thenThrow(new AppException(ErrorCode.GOOGLE_TOKEN_INVALID));

            // Act & Assert
            AppException exception = assertThrows(AppException.class,
                    () -> authController.googleLogin(googleLoginRequest));
            assertEquals(ErrorCode.GOOGLE_TOKEN_INVALID, exception.getErrorCode());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  LOGOUT
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/auth/logout")
    class LogoutTests {

        @Test
        @DisplayName("Should logout successfully with Bearer token")
        void logout_WithValidBearerToken_ReturnsSuccess() {
            // Arrange
            String bearerToken = "Bearer jwt-token-abc";
            doNothing().when(authService).logout("jwt-token-abc");

            // Act
            ResponseEntity<AppResponse<Void>> result = authController.logout(bearerToken);

            // Assert
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals("Logged out successfully", result.getBody().getMessage());
            verify(authService).logout("jwt-token-abc");
        }

        @Test
        @DisplayName("Should logout successfully with raw token (no Bearer prefix)")
        void logout_WithRawToken_ReturnsSuccess() {
            // Arrange
            String rawToken = "raw-jwt-token";
            doNothing().when(authService).logout("raw-jwt-token");

            // Act
            ResponseEntity<AppResponse<Void>> result = authController.logout(rawToken);

            // Assert
            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(authService).logout("raw-jwt-token");
        }

        @Test
        @DisplayName("Should handle null token gracefully")
        void logout_WithNullToken_ReturnsSuccess() {
            // Arrange
            doNothing().when(authService).logout(null);

            // Act
            ResponseEntity<AppResponse<Void>> result = authController.logout(null);

            // Assert
            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(authService).logout(null);
        }

        @Test
        @DisplayName("Should handle empty Bearer prefix")
        void logout_WithEmptyBearerPrefix_ReturnsSuccess() {
            // Arrange
            String bearerOnly = "Bearer ";
            doNothing().when(authService).logout("");

            // Act
            ResponseEntity<AppResponse<Void>> result = authController.logout(bearerOnly);

            // Assert
            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(authService).logout("");
        }

        @Test
        @DisplayName("Should strip Bearer prefix and pass token to service")
        void logout_ShouldStripBearerPrefixCorrectly() {
            // Arrange
            String token = "Bearer actual-token-value-xyz";
            doNothing().when(authService).logout("actual-token-value-xyz");

            // Act
            authController.logout(token);

            // Assert
            verify(authService).logout("actual-token-value-xyz");
        }

        @Test
        @DisplayName("Should handle lowercase bearer prefix")
        void logout_WithLowercaseBearer_DoesNotStrip() {
            // Arrange - the controller only checks for "Bearer " (capital B)
            String token = "bearer some-token";
            doNothing().when(authService).logout("bearer some-token");

            // Act
            authController.logout(token);

            // Assert
            verify(authService).logout("bearer some-token");
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  VERIFY EMAIL
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/auth/verify-email")
    class VerifyEmailTests {

        @Test
        @DisplayName("Should verify email successfully")
        void verifyEmail_WithValidToken_ReturnsSuccess() {
            // Arrange
            String token = "verification-token-123";
            doNothing().when(authService).verifyEmail(token);

            // Act
            ResponseEntity<AppResponse<Void>> result = authController.verifyEmail(token);

            // Assert
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals("Email verified successfully! You can now login.", result.getBody().getMessage());
            verify(authService).verifyEmail("verification-token-123");
        }

        @Test
        @DisplayName("Should throw AppException when token is invalid")
        void verifyEmail_WithInvalidToken_ThrowsAppException() {
            // Arrange
            String token = "invalid-token";
            doThrow(new AppException(ErrorCode.VERIFICATION_TOKEN_INVALID))
                    .when(authService).verifyEmail(token);

            // Act & Assert
            AppException exception = assertThrows(AppException.class,
                    () -> authController.verifyEmail(token));
            assertEquals(ErrorCode.VERIFICATION_TOKEN_INVALID, exception.getErrorCode());
        }

        @Test
        @DisplayName("Should throw AppException when token is expired")
        void verifyEmail_WithExpiredToken_ThrowsAppException() {
            // Arrange
            String token = "expired-token";
            doThrow(new AppException(ErrorCode.VERIFICATION_TOKEN_EXPIRED))
                    .when(authService).verifyEmail(token);

            // Act & Assert
            AppException exception = assertThrows(AppException.class,
                    () -> authController.verifyEmail(token));
            assertEquals(ErrorCode.VERIFICATION_TOKEN_EXPIRED, exception.getErrorCode());
        }

        @Test
        @DisplayName("Should throw AppException when email already verified")
        void verifyEmail_WhenAlreadyVerified_ThrowsAppException() {
            // Arrange
            String token = "token-already-used";
            doThrow(new AppException(ErrorCode.EMAIL_ALREADY_VERIFIED))
                    .when(authService).verifyEmail(token);

            // Act & Assert
            AppException exception = assertThrows(AppException.class,
                    () -> authController.verifyEmail(token));
            assertEquals(ErrorCode.EMAIL_ALREADY_VERIFIED, exception.getErrorCode());
        }

        @Test
        @DisplayName("Should handle empty token string")
        void verifyEmail_WithEmptyToken_InvokesService() {
            // Arrange
            doNothing().when(authService).verifyEmail("");

            // Act
            ResponseEntity<AppResponse<Void>> result = authController.verifyEmail("");

            // Assert
            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(authService).verifyEmail("");
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  FORGOT PASSWORD
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/auth/forgot-password")
    class ForgotPasswordTests {

        @Test
        @DisplayName("Should send password reset link successfully")
        void forgotPassword_WithValidEmail_ReturnsSuccess() {
            // Arrange
            doNothing().when(authService).forgotPassword(forgotPasswordRequest.getEmail());

            // Act
            ResponseEntity<AppResponse<Void>> result = authController.forgotPassword(forgotPasswordRequest);

            // Assert
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals("If the email exists, a password reset link has been sent.",
                    result.getBody().getMessage());
            verify(authService).forgotPassword("test@example.com");
        }

        @Test
        @DisplayName("Should invoke service even with empty email (service handles validation)")
        void forgotPassword_WithEmptyEmail_InvokesService() {
            // Arrange
            ForgotPasswordRequest emptyRequest = ForgotPasswordRequest.builder().email("").build();
            doNothing().when(authService).forgotPassword("");

            // Act
            ResponseEntity<AppResponse<Void>> result = authController.forgotPassword(emptyRequest);

            // Assert
            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(authService).forgotPassword("");
        }

        @Test
        @DisplayName("Should invoke service with null email (service handles validation)")
        void forgotPassword_WithNullEmail_InvokesService() {
            // Arrange
            ForgotPasswordRequest nullEmailRequest = ForgotPasswordRequest.builder().email(null).build();
            doNothing().when(authService).forgotPassword(null);

            // Act
            ResponseEntity<AppResponse<Void>> result = authController.forgotPassword(nullEmailRequest);

            // Assert
            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(authService).forgotPassword(null);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  RESET PASSWORD
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/auth/reset-password")
    class ResetPasswordTests {

        @Test
        @DisplayName("Should reset password successfully")
        void resetPassword_WithValidRequest_ReturnsSuccess() {
            // Arrange
            doNothing().when(authService).resetPassword(resetPasswordRequest.getToken(),
                    resetPasswordRequest.getNewPassword());

            // Act
            ResponseEntity<AppResponse<Void>> result = authController.resetPassword(resetPasswordRequest);

            // Assert
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals("Password reset successfully! You can now login with your new password.",
                    result.getBody().getMessage());
            verify(authService).resetPassword("reset-token-123", "newPassword456");
        }

        @Test
        @DisplayName("Should throw AppException with invalid reset token")
        void resetPassword_WithInvalidToken_ThrowsAppException() {
            // Arrange
            ResetPasswordRequest badTokenRequest = ResetPasswordRequest.builder()
                    .token("bad-token")
                    .newPassword("newPass")
                    .build();
            doThrow(new AppException(ErrorCode.VERIFICATION_TOKEN_INVALID))
                    .when(authService).resetPassword("bad-token", "newPass");

            // Act & Assert
            AppException exception = assertThrows(AppException.class,
                    () -> authController.resetPassword(badTokenRequest));
            assertEquals(ErrorCode.VERIFICATION_TOKEN_INVALID, exception.getErrorCode());
        }

        @Test
        @DisplayName("Should reset password with empty token (service handles validation)")
        void resetPassword_WithEmptyToken_InvokesService() {
            // Arrange
            ResetPasswordRequest emptyTokenRequest = ResetPasswordRequest.builder()
                    .token("")
                    .newPassword("password")
                    .build();
            doNothing().when(authService).resetPassword("", "password");

            // Act
            ResponseEntity<AppResponse<Void>> result = authController.resetPassword(emptyTokenRequest);

            // Assert
            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(authService).resetPassword("", "password");
        }

        @Test
        @DisplayName("Should reset password with empty new password (service handles validation)")
        void resetPassword_WithEmptyNewPassword_InvokesService() {
            // Arrange
            ResetPasswordRequest emptyPassRequest = ResetPasswordRequest.builder()
                    .token("token")
                    .newPassword("")
                    .build();
            doNothing().when(authService).resetPassword("token", "");

            // Act
            ResponseEntity<AppResponse<Void>> result = authController.resetPassword(emptyPassRequest);

            // Assert
            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(authService).resetPassword("token", "");
        }
    }
}
