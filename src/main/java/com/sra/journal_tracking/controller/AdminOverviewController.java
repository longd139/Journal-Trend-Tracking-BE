package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.admin.AdminChartResponse;
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

    // ── Chart endpoints ──

    @Operation(summary = "Get request volume time-series for the last 24 hours")
    @GetMapping("/overview/charts/request-volume")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AppResponse<AdminChartResponse.RequestVolumeResponse>> getRequestVolume() {
        AdminChartResponse.RequestVolumeResponse data = adminOverviewService.getRequestVolume();
        return ResponseEntity.ok(AppResponse.success("Request volume data retrieved", data));
    }

    @Operation(summary = "Get current system resource usage (CPU, memory, disk)")
    @GetMapping("/overview/charts/resource-usage")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AppResponse<AdminChartResponse.ResourceUsageResponse>> getResourceUsage() {
        AdminChartResponse.ResourceUsageResponse data = adminOverviewService.getResourceUsage();
        return ResponseEntity.ok(AppResponse.success("Resource usage data retrieved", data));
    }

    @Operation(summary = "Get hourly visitor traffic, today vs yesterday")
    @GetMapping("/overview/charts/visitor-traffic")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AppResponse<AdminChartResponse.VisitorTrafficResponse>> getVisitorTraffic() {
        AdminChartResponse.VisitorTrafficResponse data = adminOverviewService.getVisitorTraffic();
        return ResponseEntity.ok(AppResponse.success("Visitor traffic data retrieved", data));
    }

    @Operation(summary = "Get recent system events (audit logs + sync logs)")
    @GetMapping("/overview/charts/recent-events")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AppResponse<AdminChartResponse.RecentEventsResponse>> getRecentEvents() {
        AdminChartResponse.RecentEventsResponse data = adminOverviewService.getRecentEvents();
        return ResponseEntity.ok(AppResponse.success("Recent events retrieved", data));
    }
}
