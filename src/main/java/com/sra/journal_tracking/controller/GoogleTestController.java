package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.auth.AuthResponse;
import com.sra.journal_tracking.dto.auth.GoogleLoginRequest;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class GoogleTestController {

    private final AuthService authService;

    @Operation(summary = "Google login test — NEW FILE", description = "Bypasses old cached code.")
    @PostMapping("/google-v2")
    public ResponseEntity<AppResponse<AuthResponse>> googleLoginV2(
            @Valid @RequestBody GoogleLoginRequest request) {
        log.info(">>> GOOGLE-V2 endpoint hit! credential length={}",
                request.getCredential() != null ? request.getCredential().length() : 0);

        // Log first 20 chars of token for debugging
        if (request.getCredential() != null && request.getCredential().length() > 20) {
            log.info(">>> Token preview: {}...", request.getCredential().substring(0, 20));
        }

        AuthResponse authResponse = authService.googleLogin(request);
        return ResponseEntity.ok(AppResponse.success("Google login successful", authResponse));
    }
}
