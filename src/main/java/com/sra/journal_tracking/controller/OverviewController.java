package com.sra.journal_tracking.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sra.journal_tracking.dto.overview.OverviewStatisticsResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.OverviewStatisticsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/overview")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class OverviewController {

    private final OverviewStatisticsService overviewStatisticsService;

    @Operation(summary = "Get overview statistics", description = "Returns 4 stats cards: papers tracked, total citations, top trending now, and top growth topic with growth percentages.")
    @GetMapping("/statistics")
    public ResponseEntity<AppResponse<OverviewStatisticsResponse>> getStatistics() {
        OverviewStatisticsResponse response = overviewStatisticsService.getStatistics();
        return ResponseEntity.ok(AppResponse.success("Overview statistics retrieved successfully", response));
    }
}
