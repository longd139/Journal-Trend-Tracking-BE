package com.sra.journal_tracking.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sra.journal_tracking.dto.overview.UserOverviewResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.UserOverviewService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/overview")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "User Overview", description = "Role-specific overview statistics for the dashboard")
public class UserOverviewController {

    private final UserOverviewService userOverviewService;

    @Operation(
        summary = "Get user overview statistics",
        description = "Returns 4 dashboard cards tailored to the current user's role. "
                    + "Academic User sees: Saved Papers, Papers Viewed, Searches Remaining, Trending Keywords. "
                    + "Researcher/Admin sees the same structure with unlimited usage."
    )
    @GetMapping("/user")
    public ResponseEntity<AppResponse<UserOverviewResponse>> getUserOverview(Authentication authentication) {
        UserOverviewResponse stats = userOverviewService.getUserOverview(authentication.getName());
        return ResponseEntity.ok(AppResponse.success("User overview statistics retrieved", stats));
    }
}
