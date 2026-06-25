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

    @Operation(summary = "Get overview statistics", description = "Returns 4 stat cards: New Papers (papers created this month vs last month), New Citations (citations from new papers this month vs last month), Trending Topics (currently trending topic count), and Top Growth Topic (topic with highest trend score).")
    @GetMapping("/statistics")
    public ResponseEntity<AppResponse<OverviewStatisticsResponse>> getStatistics() {
        OverviewStatisticsResponse response = overviewStatisticsService.getStatistics();
        return ResponseEntity.ok(AppResponse.success("Overview statistics retrieved successfully", response));
    }
}
