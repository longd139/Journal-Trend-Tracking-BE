package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.constants.KeywordConstants;
import com.sra.journal_tracking.dto.author.AuthorQuickStatsResponse;
import com.sra.journal_tracking.dto.author.AuthorResearchFocusResponse;
import com.sra.journal_tracking.dto.author.AuthorTimelineResponse;
import com.sra.journal_tracking.dto.author.CoAuthorResponse;
import com.sra.journal_tracking.dto.paper.KeywordQuickStatsResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.AuthorQuickStatsService;
import com.sra.journal_tracking.service.KeywordQuickStatsService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final AuthorQuickStatsService authorQuickStatsService;
    private final KeywordQuickStatsService keywordQuickStatsService;

    @Operation(
            summary = "Quick author statistics lookup",
            description = "Look up an author's academic profile from OpenAlex: total papers, citations, h-index, affiliation, and more. "
                        + "Useful for quickly evaluating an author's research impact before exploring their papers."
    )
    @GetMapping("/author/quick-stats")
    public ResponseEntity<AppResponse<AuthorQuickStatsResponse>> quickStats(
            @RequestParam String keyword) {

        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("Keyword cannot be empty");
        }

        log.info("Author quick stats lookup: keyword={}", keyword);

        AuthorQuickStatsResponse result = authorQuickStatsService.searchAuthor(keyword.trim());

        return ResponseEntity.ok(AppResponse.success("Author stats retrieved", result));
    }

    @Operation(
            summary = "Author productivity & impact timeline",
            description = "Get year-by-year breakdown of an author's publications (bar chart) and "
                        + "citations received (line chart). Useful for rendering a combined Bar + Line "
                        + "chart that shows research trajectory over time."
    )
    @GetMapping("/author/quick-stats/timeline")
    public ResponseEntity<AppResponse<AuthorTimelineResponse>> timeline(
            @RequestParam String keyword) {

        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("Keyword cannot be empty");
        }

        log.info("Timeline lookup: keyword={}", keyword);

        AuthorTimelineResponse result = authorQuickStatsService.getTimeline(keyword.trim());

        return ResponseEntity.ok(AppResponse.success("Timeline retrieved", result));
    }

    @Operation(
            summary = "Author research focus (topics distribution)",
            description = "Get an author's top research topics with paper counts and percentages. "
                        + "Suitable for rendering a Pie Chart or Treemap showing the author's "
                        + "research focus areas. Topics are sorted by paper count descending."
    )
    @GetMapping("/author/quick-stats/research-focus")
    public ResponseEntity<AppResponse<AuthorResearchFocusResponse>> researchFocus(
            @RequestParam String keyword) {

        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("Keyword cannot be empty");
        }

        log.info("Research focus lookup: keyword={}", keyword);

        AuthorResearchFocusResponse result = authorQuickStatsService.getResearchFocus(keyword.trim());

        return ResponseEntity.ok(AppResponse.success("Research focus retrieved", result));
    }

    @Operation(
            summary = "Co-author collaboration network",
            description = "Discover an author's most frequent co-authors by analyzing "
                        + "their top 200 most-cited papers. Returns top 10 collaborators "
                        + "with collaboration counts and institutions. Useful for exploring "
                        + "research labs and finding key people in a research group."
    )
    @GetMapping("/author/quick-stats/co-authors")
    public ResponseEntity<AppResponse<CoAuthorResponse>> coAuthors(
            @RequestParam String keyword) {

        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("Keyword cannot be empty");
        }

        log.info("Co-authors lookup: keyword={}", keyword);

        CoAuthorResponse result = authorQuickStatsService.getCoAuthors(keyword.trim());

        return ResponseEntity.ok(AppResponse.success("Co-authors retrieved", result));
    }

    @Operation(
            summary = "Get quick stats for a keyword",
            description = "Returns 4 stat cards for a keyword: total papers, total citations, "
                        + "YoY growth rate (this year vs last year with direction arrow), "
                        + "and average citations per paper (quality indicator). "
                        + "Uses Neo4j for paper discovery and SQL for aggregation."
    )
    @GetMapping("/keyword/quick-stats")
    public ResponseEntity<AppResponse<KeywordQuickStatsResponse>> keywordQuickStats(
            @RequestParam("keyword") String keyword) {

        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("Keyword cannot be empty");
        }

        // Truncate keyword if too long (defense in depth)
        if (keyword.trim().length() > KeywordConstants.MAX_KEYWORD_LENGTH) {
            keyword = keyword.trim().substring(0, KeywordConstants.MAX_KEYWORD_LENGTH);
        }

        log.info("Keyword quick stats lookup: keyword={}", keyword.trim());

        KeywordQuickStatsResponse stats = keywordQuickStatsService.getStats(keyword.trim());
        return ResponseEntity.ok(AppResponse.success("Quick stats retrieved", stats));
    }

}
