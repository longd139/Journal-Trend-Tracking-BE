package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.author.AuthorQuickStatsResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.AuthorQuickStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class SearchController {

    private final AuthorQuickStatsService authorQuickStatsService;

    @Operation(
            summary = "Quick author statistics lookup",
            description = "Look up an author's academic profile from OpenAlex: total papers, citations, h-index, affiliation, and more. "
                        + "Useful for quickly evaluating an author's research impact before exploring their papers."
    )
    @GetMapping("/quick-stats")
    public ResponseEntity<AppResponse<AuthorQuickStatsResponse>> quickStats(
            @RequestParam(defaultValue = "author") String type,
            @RequestParam String keyword) {

        if (!"author".equalsIgnoreCase(type)) {
            throw new IllegalArgumentException("Only type=author is currently supported");
        }

        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("Keyword cannot be empty");
        }

        log.info("Quick stats lookup: type={}, keyword={}", type, keyword);

        AuthorQuickStatsResponse result = authorQuickStatsService.searchAuthor(keyword.trim());

        return ResponseEntity.ok(AppResponse.success("Author stats retrieved", result));
    }
}
