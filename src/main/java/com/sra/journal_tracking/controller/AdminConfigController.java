package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.admin.SystemConfigResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/configs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminConfigController {

    private final AdminService adminService;

    @GetMapping
    public ResponseEntity<AppResponse<List<SystemConfigResponse>>> getConfigs() {
        return ResponseEntity.ok(AppResponse.success(
                "System configs retrieved",
                adminService.getConfigs()));
    }

    @PutMapping
    public ResponseEntity<AppResponse<List<SystemConfigResponse>>> updateConfigs(
            @RequestBody Map<String, Object> payload,
            Authentication authentication) {
        return ResponseEntity.ok(AppResponse.success(
                "System configs updated",
                adminService.updateConfigs(payload, authentication.getName())));
    }
}
