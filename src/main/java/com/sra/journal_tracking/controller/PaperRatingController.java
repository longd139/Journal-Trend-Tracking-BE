package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.rating.RatingResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.PaperRatingService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/papers")
@RequiredArgsConstructor
public class PaperRatingController {

    private final PaperRatingService paperRatingService;

    @PostMapping("/{paperId}/ratings")
    public ResponseEntity<AppResponse<RatingResponse>> ratePaper(
            @PathVariable UUID paperId,
            @RequestParam @Min(1) @Max(5) int score,
            Authentication authentication) {
        RatingResponse response = paperRatingService.ratePaper(
                paperId, authentication.getName(), score);
        return ResponseEntity.ok(AppResponse.success("Rating submitted", response));
    }

    @GetMapping("/{paperId}/ratings")
    public ResponseEntity<AppResponse<RatingResponse>> getRating(
            @PathVariable UUID paperId,
            Authentication authentication) {
        RatingResponse response = paperRatingService.getRating(
                paperId, authentication.getName());
        return ResponseEntity.ok(AppResponse.success("Rating retrieved", response));
    }
}
