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

@Schema(description = "Login request")
public class LoginRequest {
    @Schema(description = "Email", example = "admin@gmail.com")
    @NotBlank(message = "Email is required")
    private String email;

    @Schema(description = "Password", example = "123456")
    @NotBlank(message = "Password is required")
    private String password;
}
