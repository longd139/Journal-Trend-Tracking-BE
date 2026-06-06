package com.sra.journal_tracking.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Authentication response")
public class AuthResponse {

    @Schema(description = "JWT access token", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String accessToken;

    @Builder.Default
    @Schema(description = "Token type", example = "Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "Authenticated user information")
    private UserAuthInfo user;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Authenticated user details")
    public static class UserAuthInfo {

        @Schema(description = "User ID", example = "1")
        private String id;

        @Schema(description = "Full name", example = "khang")
        private String fullName;

        @Schema(description = "Email address", example = "khang@gmail.com")
        private String email;

        @Schema(description = "Role name", example = "USER")
        private String roleName;
    }
}