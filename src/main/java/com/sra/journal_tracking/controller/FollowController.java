package com.sra.journal_tracking.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sra.journal_tracking.dto.follow.FollowRequest;
import com.sra.journal_tracking.dto.follow.FollowResponse;
import com.sra.journal_tracking.dto.follow.QuickInsightResponse;
import com.sra.journal_tracking.dto.follow.WatchlistItemResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.FollowService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/follows")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class FollowController {

    private final FollowService followService;

    @Operation(summary = "Add follow", description = "Follow a journal, topic, or keyword. Only one target allowed per request.")
    @PostMapping
    public ResponseEntity<AppResponse<FollowResponse>> addFollow(
            Authentication authentication,
            @Valid @RequestBody FollowRequest request) {
        FollowResponse response = followService.addFollow(authentication.getName(), request);
        return ResponseEntity.ok(AppResponse.success("Follow added successfully", response));
    }

    @Operation(summary = "Get my follows", description = "List all follows for the current user.")
    @GetMapping
    public ResponseEntity<AppResponse<List<FollowResponse>>> getMyFollows(
            Authentication authentication) {
        List<FollowResponse> follows = followService.getMyFollows(authentication.getName());
        return ResponseEntity.ok(AppResponse.success("Follows retrieved", follows));
    }

    @Operation(summary = "Toggle follow notification", description = "Enable or disable notification for a follow.")
    @PutMapping("/{followId}")
    public ResponseEntity<AppResponse<FollowResponse>> updateFollow(
            Authentication authentication,
            @PathVariable UUID followId,
            @RequestParam boolean notifyEnabled) {
        FollowResponse response = followService.updateFollow(authentication.getName(), followId, notifyEnabled);
        return ResponseEntity.ok(AppResponse.success("Follow updated successfully", response));
    }

    @Operation(summary = "Unfollow", description = "Delete a follow by its ID. Only the owner can delete.")
    @DeleteMapping("/{followId}")
    public ResponseEntity<AppResponse<Void>> deleteFollow(
            Authentication authentication,
            @PathVariable UUID followId) {
        followService.deleteFollow(authentication.getName(), followId);
        return ResponseEntity.ok(AppResponse.success("Unfollowed successfully"));
    }

    @Operation(
            summary = "Get my watchlist",
            description = "Returns the current user's followed keywords, journals, and topics "
                    + "enriched with total paper counts and new-papers-this-week counts. "
                    + "Sorted by new papers this week descending."
    )
    @GetMapping("/watchlist")
    public ResponseEntity<AppResponse<List<WatchlistItemResponse>>> getMyWatchlist(
            Authentication authentication) {
        List<WatchlistItemResponse> watchlist = followService.getMyWatchlist(authentication.getName());
        return ResponseEntity.ok(AppResponse.success("Watchlist retrieved successfully", watchlist));
    }

    @Operation(
            summary = "Get quick analytical insight",
            description = "Generates a personalized Vietnamese natural-language summary "
                    + "analyzing the user's followed keywords and journals. "
                    + "Includes growth trends, journal rankings, and actionable suggestions."
    )
    @GetMapping("/insight")
    public ResponseEntity<AppResponse<QuickInsightResponse>> getQuickInsight(
            Authentication authentication) {
        QuickInsightResponse insight = followService.getQuickInsight(authentication.getName());
        return ResponseEntity.ok(AppResponse.success("Insight generated successfully", insight));
    }
}
