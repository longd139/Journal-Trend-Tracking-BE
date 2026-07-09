package com.sra.journal_tracking.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sra.journal_tracking.dto.follow.FollowResponse;
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
        description = "Returns dashboard cards tailored to the current user's role. "
                    + "Pass ?authorId= to get researcher-specific fields "
                    + "(h-index, citationHistory, researchFields, recentPublications) for a followed author."
    )
    @GetMapping("/user")
    public ResponseEntity<AppResponse<UserOverviewResponse>> getUserOverview(
            Authentication authentication,
            @RequestParam(required = false) UUID authorId) {
        UserOverviewResponse stats = userOverviewService.getUserOverview(authentication.getName(), authorId);
        return ResponseEntity.ok(AppResponse.success("User overview statistics retrieved", stats));
    }

    @Operation(
        summary = "Get followed authors",
        description = "Returns the list of authors the current user is following. "
                    + "Use the returned authorId with GET /user?authorId= to see that author's research stats."
    )
    @GetMapping("/followed-authors")
    public ResponseEntity<AppResponse<List<FollowResponse>>> getFollowedAuthors(
            Authentication authentication) {
        List<FollowResponse> authors = userOverviewService.getFollowedAuthors(authentication.getName());
        return ResponseEntity.ok(AppResponse.success("Followed authors retrieved", authors));
    }
}
