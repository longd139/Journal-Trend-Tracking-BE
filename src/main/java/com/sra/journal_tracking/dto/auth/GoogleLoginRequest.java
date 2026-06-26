package com.sra.journal_tracking.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleLoginRequest {

    /** Google ID token (credential) from @react-oauth/google on the frontend */
    @NotBlank(message = "Google credential is required")
    private String credential;
}
