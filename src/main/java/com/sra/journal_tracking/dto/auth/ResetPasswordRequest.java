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

    @Schema(description = "Reset token from email link", example = "a1b2c3d4-...")
    @NotBlank(message = "Token is required")
    private String token;

    @Schema(description = "New password", example = "newPassword123")
    @NotBlank(message = "New password is required")
    private String newPassword;
}
