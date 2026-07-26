package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.constants.KeywordConstants;
import com.sra.journal_tracking.dto.author.AuthorQuickStatsResponse;
import com.sra.journal_tracking.dto.author.AuthorResearchFocusResponse;
import com.sra.journal_tracking.dto.author.AuthorSuggestionResponse;
import com.sra.journal_tracking.dto.author.AuthorTimelineResponse;
import com.sra.journal_tracking.dto.author.CoAuthorResponse;
import com.sra.journal_tracking.dto.author.SuggestedAuthorResponse;
import com.sra.journal_tracking.dto.journal.JournalAuthorResponse;
import com.sra.journal_tracking.dto.journal.JournalQuickStatsResponse;
import com.sra.journal_tracking.dto.journal.JournalSuggestionResponse;
import com.sra.journal_tracking.dto.journal.JournalTimelineResponse;
import com.sra.journal_tracking.dto.paper.KeywordQuickStatsResponse;
import com.sra.journal_tracking.dto.paper.PaperDetailResponseDTO;
import com.sra.journal_tracking.dto.paper.RelatedKeywordResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.dto.search.CategoryResponse;
import com.sra.journal_tracking.dto.search.KeywordComparisonRequest;
import com.sra.journal_tracking.dto.search.KeywordComparisonResponse;
import com.sra.journal_tracking.dto.search.NicheTopicResponse;
import com.sra.journal_tracking.dto.search.CategoryResponse;
import com.sra.journal_tracking.dto.search.RecentSearchResponse;
import com.sra.journal_tracking.repository.jpa.ResearchFieldRepository;
import com.sra.journal_tracking.service.AuthorQuickStatsService;
import com.sra.journal_tracking.service.AuthorSuggestionService;
import com.sra.journal_tracking.service.GraphService;
import com.sra.journal_tracking.service.JournalQuickStatsService;
import com.sra.journal_tracking.service.KeywordQuickStatsService;
import com.sra.journal_tracking.service.UserSearchHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import jakarta.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final AuthorQuickStatsService authorQuickStatsService;
    private final AuthorSuggestionService authorSuggestionService;
    private final KeywordQuickStatsService keywordQuickStatsService;
    private final JournalQuickStatsService journalQuickStatsService;
    private final UserSearchHistoryService userSearchHistoryService;
    private final GraphService graphService;
    private final ResearchFieldRepository researchFieldRepository;

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
            @RequestParam("keyword") String keyword,
            @Parameter(description = "Optional: filter by publication year from (inclusive)")
            @RequestParam(required = false) Integer pubYearFrom,
            @Parameter(description = "Optional: filter by publication year to (inclusive)")
            @RequestParam(required = false) Integer pubYearTo,
            @Parameter(description = "Optional: filter by open access (true = OA only)")
            @RequestParam(required = false) Boolean isOpenAccess) {

        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("Keyword cannot be empty");
        }

        // Truncate keyword if too long (defense in depth)
        if (keyword.trim().length() > KeywordConstants.MAX_KEYWORD_LENGTH) {
            keyword = keyword.trim().substring(0, KeywordConstants.MAX_KEYWORD_LENGTH);
        }

        log.info("Keyword quick stats lookup: keyword={}, from={}, to={}, oa={}",
                keyword.trim(), pubYearFrom, pubYearTo, isOpenAccess);

        KeywordQuickStatsResponse stats = keywordQuickStatsService.getStats(
                keyword.trim(), pubYearFrom, pubYearTo, isOpenAccess);
        return ResponseEntity.ok(AppResponse.success("Quick stats retrieved", stats));
    }

    @Operation(
            summary = "Keyword co-occurrence & related trends",
            description = "Discover 'satellite keywords' that frequently appear together with "
                        + "the searched keyword in recent papers (last 2 years). Uses Neo4j graph "
                        + "traversal to find research niches — e.g., searching 'IoT' reveals "
                        + "'Edge Computing', 'Cybersecurity', '5G' as rising co-occurring topics. "
                        + "Returns top 10 related keywords ranked by co-occurrence count, "
                        + "with year-over-year growth rates."
    )
    @GetMapping("/keyword/related-trends")
    public ResponseEntity<AppResponse<List<RelatedKeywordResponse>>> relatedTrends(
            @RequestParam("keyword") String keyword,
            @Parameter(description = "Optional: start year for co-occurrence window (default: currentYear - 2)")
            @RequestParam(required = false) Integer pubYearFrom,
            @Parameter(description = "Optional: end year for co-occurrence window (default: currentYear)")
            @RequestParam(required = false) Integer pubYearTo) {

        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("Keyword cannot be empty");
        }

        log.info("Related trends lookup: keyword={}, from={}, to={}", keyword, pubYearFrom, pubYearTo);

        List<RelatedKeywordResponse> trends = keywordQuickStatsService.getRelatedTrends(
                keyword.trim(), pubYearFrom, pubYearTo);
        return ResponseEntity.ok(AppResponse.success("Related trends retrieved", trends));
    }

    @Operation(
            summary = "Top 5 most influential papers for a keyword",
            description = "Returns the 5 most-cited 'foundation papers' for a keyword, "
                        + "ranked by citation count descending. Anyone starting research "
                        + "on a new topic should read these papers first. "
                        + "Uses Neo4j for paper discovery + SQL for citation ranking."
    )
    @GetMapping("/keyword/top-papers")
    public ResponseEntity<AppResponse<List<PaperDetailResponseDTO>>> topPapers(
            @RequestParam("keyword") String keyword,
            @Parameter(description = "Optional: filter by publication year from (inclusive)")
            @RequestParam(required = false) Integer startYear,
            @Parameter(description = "Optional: filter by publication year to (inclusive)")
            @RequestParam(required = false) Integer endYear) {

        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("Keyword cannot be empty");
        }

        log.info("Top papers lookup: keyword={}, startYear={}, endYear={}", keyword, startYear, endYear);

        List<PaperDetailResponseDTO> papers = keywordQuickStatsService.getTopInfluentialPapers(
                keyword.trim(), startYear, endYear);
        return ResponseEntity.ok(AppResponse.success("Top papers retrieved", papers));
    }

    @Operation(
            summary = "Compare multiple keywords side-by-side",
            description = "Given a list of up to 10 keywords, returns aggregated stats "
                        + "(paper count, citation count, YoY growth rate, peak year) for each. "
                        + "Used by the FE BarChart component on the Analytics page for "
                        + "side-by-side keyword trend comparison. Cached for 30 minutes."
    )
    @PostMapping("/keywords/compare")
    public ResponseEntity<AppResponse<KeywordComparisonResponse>> compareKeywords(
            @Valid @RequestBody KeywordComparisonRequest request) {

        log.info("Keyword comparison request: {} keywords", request.getKeywords().size());

        KeywordComparisonResponse response = keywordQuickStatsService.compareKeywords(request);
        return ResponseEntity.ok(AppResponse.success("Keyword comparison completed", response));
    }

    @Operation(
            summary = "Journal prestige KPIs",
            description = "Search for a journal by name and return its prestige metrics: "
                        + "Impact Factor, Quartile (Q1–Q4), total publications, total citations, "
                        + "calculated CiteScore, average citations per paper, and top keywords. "
                        + "Quartile is color-coded by frontend: green=Q1, yellow=Q2, orange=Q3, red=Q4."
    )
    @GetMapping("/journal/quick-stats")
    public ResponseEntity<AppResponse<JournalQuickStatsResponse>> journalQuickStats(
            @RequestParam("keyword") String keyword) {

        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("Journal name cannot be empty");
        }

        log.info("Journal quick stats lookup: journal={}", keyword);

        JournalQuickStatsResponse stats = journalQuickStatsService.getStats(keyword.trim());
        return ResponseEntity.ok(AppResponse.success("Journal stats retrieved", stats));
    }

    @Operation(
            summary = "Journal impact & volume timeline",
            description = "Year-by-year trend of a journal's prestige: paper count (bar chart) "
                        + "and average citations per paper (line chart) over the last 10 years. "
                        + "Shows whether the journal is rising, stable, or declining — helping "
                        + "researchers decide where to submit their work."
    )
    @GetMapping("/journal/quick-stats/timeline")
    public ResponseEntity<AppResponse<JournalTimelineResponse>> journalTimeline(
            @RequestParam("keyword") String keyword) {

        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("Journal name cannot be empty");
        }

        log.info("Journal timeline lookup: journal={}", keyword);

        JournalTimelineResponse timeline = journalQuickStatsService.getTimeline(keyword.trim());
        return ResponseEntity.ok(AppResponse.success("Journal timeline retrieved", timeline));
    }

    @Operation(
            summary = "Top 5 most-cited papers in a journal",
            description = "Returns the 5 papers that have accumulated the most citations "
                        + "in a specific journal — these are the papers that define the "
                        + "journal's reputation and impact."
    )
    @GetMapping("/journal/top-papers")
    public ResponseEntity<AppResponse<List<PaperDetailResponseDTO>>> journalTopPapers(
            @RequestParam("keyword") String keyword) {

        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("Journal name cannot be empty");
        }

        log.info("Journal top papers lookup: journal={}", keyword);

        List<PaperDetailResponseDTO> papers = journalQuickStatsService.getTopPapers(keyword.trim());
        return ResponseEntity.ok(AppResponse.success("Top papers retrieved", papers));
    }

    @Operation(
            summary = "Top contributing authors in a journal",
            description = "Returns the authors who publish most frequently in a specific "
                        + "journal — 'Ai đang gánh uy tín cho tạp chí này?'. "
                        + "Clicking an author name navigates to Search by Author page."
    )
    @GetMapping("/journal/top-authors")
    public ResponseEntity<AppResponse<List<JournalAuthorResponse>>> journalTopAuthors(
            @RequestParam("keyword") String keyword) {

        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("Journal name cannot be empty");
        }

        log.info("Journal top authors lookup: journal={}", keyword);

        List<JournalAuthorResponse> authors = journalQuickStatsService.getTopAuthors(keyword.trim());
        return ResponseEntity.ok(AppResponse.success("Top authors retrieved", authors));
    }

    @Operation(
            summary = "Top cited papers by an author",
            description = "Returns the top 5 most-cited papers (all time, no year filter) "
                        + "for a specific author. Lightweight — only queries OpenAlex works API "
                        + "by author ID, sorted by citation count descending."
    )
    @GetMapping("/author/top-papers")
    public ResponseEntity<AppResponse<List<PaperDetailResponseDTO>>> authorTopPapers(
            @RequestParam("keyword") String keyword) {

        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("Author name cannot be empty");
        }

        log.info("Author top papers lookup: author={}", keyword);

        List<PaperDetailResponseDTO> papers = authorQuickStatsService.getTopPapers(keyword.trim());
        return ResponseEntity.ok(AppResponse.success("Top papers retrieved", papers));
    }

    @Operation(
            summary = "Get recent search history for current user",
            description = "Returns the 3-5 most recent distinct searches (keywords, authors, or journals) "
                        + "performed by the currently authenticated user. Used for the search page zero state "
                        + "before the user types anything — enables one-click re-search for repetitive research workflows."
    )
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/history/recent")
    public ResponseEntity<AppResponse<List<RecentSearchResponse>>> getRecentSearches(
            @RequestParam(defaultValue = "5") int limit,
            Authentication authentication) {

        if (limit < 1) limit = 1;
        if (limit > 10) limit = 10;

        String userEmail = authentication.getName();
        log.info("Fetching recent searches for user: {}, limit={}", userEmail, limit);

        List<RecentSearchResponse> recentSearches = userSearchHistoryService.getRecentSearches(userEmail, limit);
        return ResponseEntity.ok(AppResponse.success("Recent searches retrieved", recentSearches));
    }

    @Operation(
            summary = "Get broad research categories for discovery",
            description = "Returns top-level research fields from the database (parentField IS NULL, isTracked = true) "
                        + "with paper counts. Used as pill buttons in the search zero state so students "
                        + "can narrow down their research scope by clicking a field. "
                        + "Data source: SQL RESEARCH_FIELD table."
    )
    @GetMapping("/categories")
    public ResponseEntity<AppResponse<List<CategoryResponse>>> getCategories(
            @RequestParam(defaultValue = "8") int limit) {

        if (limit < 1) limit = 1;
        if (limit > 12) limit = 12;

        log.info("Fetching research categories from SQL, limit={}", limit);

        List<CategoryResponse> categories = researchFieldRepository
                .findByParentFieldIsNullAndIsTrackedTrue()
                .stream()
                .map(field -> {
                    // Try direct field count first; fall back to keyword-based count
                    // since most papers imported from OpenAlex are linked via keywords
                    long count = researchFieldRepository.countPapersByFieldId(field.getFieldId());
                    if (count == 0) {
                        count = researchFieldRepository.countPapersByFieldIdViaKeywords(field.getFieldId());
                    }
                    return CategoryResponse.builder()
                            .keywordText(field.getFieldName())
                            .normalizedText(field.getFieldName().toLowerCase().trim())
                            .paperCount(count)
                            .build();
                })
                .sorted((a, b) -> Long.compare(b.getPaperCount(), a.getPaperCount()))
                .limit(limit)
                .toList();

        return ResponseEntity.ok(AppResponse.success("Categories retrieved", categories));
    }

    @Operation(
            summary = "Get niche topics within a research category",
            description = "Given a broad category keyword, finds co-occurring keywords in recent papers "
                        + "(last 3 years) using Neo4j graph traversal. These 'ngách' topics represent "
                        + "specific subtopics trending within the field — shown when user clicks a category pill."
    )
    @GetMapping("/categories/niches")
    public ResponseEntity<AppResponse<List<NicheTopicResponse>>> getNiches(
            @RequestParam("keyword") String keyword,
            @RequestParam(defaultValue = "6") int limit) {

        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("Category keyword cannot be empty");
        }
        if (limit < 1) limit = 1;
        if (limit > 15) limit = 15;

        String normalizedKeyword = keyword.toLowerCase().trim();
        short thisYear = (short) java.time.Year.now().getValue();
        short lastYear = (short) (thisYear - 1);
        int startYear = thisYear - 2; // Last 3 years

        log.info("Fetching niche topics for category: '{}', limit={}", normalizedKeyword, limit);

        List<NicheTopicResponse> niches = graphService.getNicheKeywords(
                        normalizedKeyword, startYear, thisYear, lastYear, limit)
                .stream()
                .map(row -> NicheTopicResponse.builder()
                        .keywordText((String) row.get("keywordText"))
                        .normalizedText((String) row.get("normalizedText"))
                        .paperCount((Long) row.get("paperCount"))
                        .thisYearCount((Long) row.get("thisYearCount"))
                        .lastYearCount((Long) row.get("lastYearCount"))
                        .build())
                .toList();

        return ResponseEntity.ok(AppResponse.success("Niche topics retrieved", niches));
    }

    @Operation(
            summary = "Suggested authors for zero-state",
            description = "Returns top authors by citation count with their primary research field. "
                        + "Used when the author search page loads without a keyword — helps users "
                        + "discover influential researchers across different fields. Cached per day."
    )
    @GetMapping("/author/suggested")
    public ResponseEntity<AppResponse<List<SuggestedAuthorResponse>>> getSuggestedAuthors() {
        log.info("Fetching suggested authors (zero-state)");
        List<SuggestedAuthorResponse> authors = authorSuggestionService.getSuggestedAuthors();
        return ResponseEntity.ok(AppResponse.success("Suggested authors retrieved", authors));
    }

    @Operation(
            summary = "Research field distribution",
            description = "Returns all tracked research fields (top-level only) with their paper counts. "
                        + "Used for field distribution visualizations (pie chart / bar chart) on the "
                        + "overview dashboard. Data source: SQL RESEARCH_FIELD + RESEARCH_PAPER."
    )
    @GetMapping("/fields/distribution")
    public ResponseEntity<AppResponse<List<CategoryResponse>>> getFieldDistribution() {
        log.info("Fetching field distribution");

        List<CategoryResponse> fields = researchFieldRepository
                .findByParentFieldIsNullAndIsTrackedTrue()
                .stream()
                .map(field -> {
                    long count = researchFieldRepository.countPapersByFieldId(field.getFieldId());
                    return CategoryResponse.builder()
                            .keywordText(field.getFieldName())
                            .normalizedText(field.getFieldName().toLowerCase().trim())
                            .paperCount(count)
                            .build();
                })
                .toList();

        return ResponseEntity.ok(AppResponse.success("Field distribution retrieved", fields));
    }

    @Operation(
            summary = "Journal name autocomplete from OpenAlex",
            description = "As the user types a journal name, returns up to 10 matching journals "
                        + "from OpenAlex sources API. Used by the FE search box for autocomplete "
                        + "so the user doesn't need to type the exact journal name."
    )
    @GetMapping("/journal/suggest")
    public ResponseEntity<AppResponse<List<JournalSuggestionResponse>>> suggestJournals(
            @Parameter(description = "Partial journal name to search", required = true)
            @RequestParam("query") String query) {

        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.ok(AppResponse.success("Empty query", List.of()));
        }

        log.info("Journal suggestion lookup: query={}", query.trim());
        List<JournalSuggestionResponse> suggestions = journalQuickStatsService.suggestJournals(query.trim());
        return ResponseEntity.ok(AppResponse.success("Journal suggestions retrieved", suggestions));
    }

    @Operation(
            summary = "Author name autocomplete from OpenAlex",
            description = "As the user types an author name, returns up to 10 matching authors "
                        + "from OpenAlex /authors search API. Used by the FE search box for "
                        + "autocomplete so the user doesn't need to type the exact author name."
    )
    @GetMapping("/author/suggest")
    public ResponseEntity<AppResponse<java.util.Map<String, Object>>> suggestAuthors(
            @Parameter(description = "Partial author name to search", required = true)
            @RequestParam("query") String query,
            @Parameter(description = "Page number (1-based, default 1)")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Results per page (default 20, max 50)")
            @RequestParam(defaultValue = "20") int size) {

        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.ok(AppResponse.success("Empty query",
                    java.util.Map.of("data", List.of(), "total", 0, "page", page, "hasMore", false)));
        }
        if (size > 50) size = 50;

        log.info("Author suggestion lookup: query={} page={} size={}", query.trim(), page, size);
        java.util.Map<String, Object> result = authorQuickStatsService.suggestAuthors(query.trim(), page, size);
        return ResponseEntity.ok(AppResponse.success("Author suggestions retrieved", result));
    }

}
