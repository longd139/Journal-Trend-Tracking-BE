package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.analytics.CountryBreakdownResponse;
import com.sra.journal_tracking.dto.analytics.CountryTrendResponse;
import com.sra.journal_tracking.dto.analytics.InstitutionBreakdownResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Research analytics by country and institution")
@SecurityRequirement(name = "Bearer Authentication")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @Operation(summary = "Get paper and citation breakdown by country")
    @GetMapping("/by-country")
    public ResponseEntity<AppResponse<CountryBreakdownResponse>> getByCountry(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer year) {
        CountryBreakdownResponse response = analyticsService.getCountryBreakdown(keyword, year);
        return ResponseEntity.ok(AppResponse.success("Country breakdown retrieved successfully", response));
    }

    @Operation(summary = "Get top institutions by paper and citation counts")
    @GetMapping("/by-institution")
    public ResponseEntity<AppResponse<InstitutionBreakdownResponse>> getByInstitution(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        InstitutionBreakdownResponse response = analyticsService.getInstitutionBreakdown(keyword, year, limit);
        return ResponseEntity.ok(AppResponse.success("Institution breakdown retrieved successfully", response));
    }

    @Operation(summary = "Get yearly research trend grouped by country")
    @GetMapping("/trend-by-country")
    public ResponseEntity<AppResponse<CountryTrendResponse>> getTrendByCountry(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer startYear,
            @RequestParam(required = false) Integer endYear) {
        CountryTrendResponse response = analyticsService.getTrendByCountry(keyword, startYear, endYear);
        return ResponseEntity.ok(AppResponse.success("Country trend retrieved successfully", response));
    }
}
