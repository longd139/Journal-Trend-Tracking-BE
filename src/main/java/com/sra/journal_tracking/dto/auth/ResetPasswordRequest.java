package com.sra.journal_tracking.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Reset password request")
public class ResetPasswordRequest {

    @Schema(description = "JWT reset token received via email", example = "eyJhbGciOiJIUzI1NiJ9...")
    @NotBlank(message = "Reset token is required")
    private String token;

    @Schema(description = "New password to set for the account", example = "newSecurePassword123")
    @NotBlank(message = "New password is required")
    private String newPassword;
}