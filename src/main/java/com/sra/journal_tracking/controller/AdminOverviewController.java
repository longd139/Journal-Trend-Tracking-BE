package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.admin.AdminOverviewResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.AdminOverviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class AdminOverviewController {

    private final AdminOverviewService adminOverviewService;

    @Operation(
            summary = "Get admin overview dashboard stats",
            description = "Returns 5 stat cards: Active Users, Total Requests, Avg Latency, "
                        + "Error Rate, and DB Size — plus auxiliary banner stats (uptime, storage, "
                        + "request rate). Metrics are sourced from Micrometer MeterRegistry "
                        + "(spring-boot-starter-actuator). Admin-only."
    )
    @GetMapping("/overview")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AppResponse<AdminOverviewResponse>> getOverview() {
        AdminOverviewResponse stats = adminOverviewService.getOverview();
        return ResponseEntity.ok(AppResponse.success("Admin overview statistics retrieved", stats));
    }
}
