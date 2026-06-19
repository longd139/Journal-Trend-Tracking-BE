package com.sra.journal_tracking.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Register request")
public class RegisterRequest {
    @Schema(description = "fullName", example = "Long dinh")
    @NotBlank(message = "Fullname is required")
    private String fullName;

    @Schema(description = "email", example = "admin@gmail.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;

    @Schema(description = "password", example = "123456")
    @NotBlank(message = "Password is required")
    private String password;

    @Schema(description = "institution", example = "FPT University")
    private String institution;
}
