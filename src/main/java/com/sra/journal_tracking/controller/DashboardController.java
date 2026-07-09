package com.sra.journal_tracking.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sra.journal_tracking.dto.dashboard.OverviewStatsResponse;
import com.sra.journal_tracking.dto.dashboard.TotalPapersResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.DashboardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/public/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Public dashboard overview statistics")
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(
        summary = "Get overview statistics",
        description = "Without ?authorId=: returns 4 system-wide dashboard cards. "
                    + "With ?authorId=: returns 4 author-specific cards "
                    + "(Total Papers, Total Citations, h-index, Co-authors). "
                    + "Public endpoint — no auth required."
    )
    @ApiResponse(responseCode = "200", description = "Overview statistics retrieved successfully")
    @GetMapping("/overview")
    public ResponseEntity<AppResponse<OverviewStatsResponse>> getOverviewStats(
            @RequestParam(required = false) UUID authorId) {
        OverviewStatsResponse stats = dashboardService.getOverviewStats(authorId);
        String msg = authorId != null ? "Author overview retrieved" : "Overview statistics retrieved";
        return ResponseEntity.ok(AppResponse.success(msg, stats));
    }

    @Operation(
        summary = "Get total papers count",
        description = "Returns the total number of research papers currently stored in the system. "
                    + "Public endpoint — no auth required."
    )
    @ApiResponse(responseCode = "200", description = "Total papers count retrieved successfully")
    @GetMapping("/papers")
    public ResponseEntity<AppResponse<TotalPapersResponse>> getTotalPapers() {
        TotalPapersResponse result = dashboardService.getTotalPapers();
        return ResponseEntity.ok(AppResponse.success("Total papers retrieved", result));
    }
}
