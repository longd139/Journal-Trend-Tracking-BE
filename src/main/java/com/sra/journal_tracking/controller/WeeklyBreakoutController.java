package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.dto.trends.WeeklyBreakoutResponse;
import com.sra.journal_tracking.service.WeeklyBreakoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public endpoints for research trend discovery.
 * No authentication required — these are used on the landing / zero-state pages.
 */
@RestController
@RequestMapping("/api/public/trends")
@RequiredArgsConstructor
@Tag(name = "Trends", description = "Public research trend discovery — weekly breakout topics")
public class WeeklyBreakoutController {

    private final WeeklyBreakoutService weeklyBreakoutService;

    @Operation(
        summary = "Get weekly breakout topics",
        description = "Returns the top 5 breakout research topics with 6-month citation sparkline data, "
                    + "growth classification labels (Strong Growth / Newly Emerging / Leading), "
                    + "total paper counts, and growth rates. "
                    + "Used for the 'Xu hướng bứt phá tuần này' section on the landing page. "
                    + "Public endpoint — no auth required."
    )
    @ApiResponse(responseCode = "200", description = "Weekly breakout topics retrieved successfully")
    @GetMapping("/weekly-breakout")
    public ResponseEntity<AppResponse<List<WeeklyBreakoutResponse>>> getWeeklyBreakoutTopics() {
        List<WeeklyBreakoutResponse> topics = weeklyBreakoutService.getWeeklyBreakoutTopics();
        return ResponseEntity.ok(AppResponse.success("Weekly breakout topics retrieved", topics));
    }
}
