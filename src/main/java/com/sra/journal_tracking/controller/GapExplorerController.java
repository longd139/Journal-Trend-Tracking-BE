package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.GraphResponse;
import com.sra.journal_tracking.dto.gap.KeywordMatchResult;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.dto.search.RecentSearchResponse;
import com.sra.journal_tracking.service.GapDataCrawlService;
import com.sra.journal_tracking.service.GraphExplorerService;
import com.sra.journal_tracking.service.KeywordMatchingService;
import com.sra.journal_tracking.service.McpOrchestrator;
import com.sra.journal_tracking.service.UserSearchHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Year;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints for the Research Gap Explorer.
 * Provides hierarchy graph, focused gap view, MCP chatbot, and gap analysis.
 */
@RestController
@RequestMapping("/api/v1/gap")
public class GapExplorerController {

    private static final Logger log = LoggerFactory.getLogger(GapExplorerController.class);

    private final GraphExplorerService graphExplorer;
    private final McpOrchestrator mcpOrchestrator;
    private final GapDataCrawlService crawlService;
    private final KeywordMatchingService keywordMatchingService;
    private final UserSearchHistoryService historyService;

    public GapExplorerController(GraphExplorerService graphExplorer,
                                  McpOrchestrator mcpOrchestrator,
                                  GapDataCrawlService crawlService,
                                  KeywordMatchingService keywordMatchingService,
                                  UserSearchHistoryService historyService) {
        this.graphExplorer = graphExplorer;
        this.mcpOrchestrator = mcpOrchestrator;
        this.crawlService = crawlService;
        this.keywordMatchingService = keywordMatchingService;
        this.historyService = historyService;
    }

    /**
     * Stage 1: Get the hierarchy graph (Year → Field → Topic → Keywords).
     * Default: last 3 years.
     */
    @GetMapping("/hierarchy")
    public ResponseEntity<AppResponse<GraphResponse>> getHierarchyGraph(
            @RequestParam(defaultValue = "2024,2025,2026") String years) {
        List<Integer> yearList = parseYears(years);
        GraphResponse graph = graphExplorer.getHierarchyGraph(yearList);
        return ResponseEntity.ok(AppResponse.success("Hierarchy graph loaded", graph));
    }

    /**
     * Stage 3: Get the focused graph for 2 keywords.
     */
    @GetMapping("/focused")
    public ResponseEntity<AppResponse<GraphResponse>> getFocusedGraph(
            @RequestParam String kw1,
            @RequestParam String kw2,
            @RequestParam(defaultValue = "2024,2025,2026") String years) {
        List<Integer> yearList = parseYears(years);
        GraphResponse graph = graphExplorer.getFocusedGraph(
                kw1.toLowerCase().trim(), kw2.toLowerCase().trim(), yearList);
        return ResponseEntity.ok(AppResponse.success("Focused graph loaded", graph));
    }

    /**
     * Get gap analysis data for 2 keywords (stats + dimensions + authors).
     */
    @GetMapping("/analysis")
    public ResponseEntity<AppResponse<Map<String, Object>>> getGapAnalysis(
            @RequestParam String kw1,
            @RequestParam String kw2,
            @RequestParam(defaultValue = "2024,2025,2026") String years) {
        List<Integer> yearList = parseYears(years);
        Map<String, Object> analysis = graphExplorer.getGapAnalysis(
                kw1.toLowerCase().trim(), kw2.toLowerCase().trim(), yearList);
        return ResponseEntity.ok(AppResponse.success("Gap analysis complete", analysis));
    }

    /**
     * MCP Chatbot: Suggest keyword pairs based on user's idea description.
     * AI calls Neo4j tools to explore data before making suggestions.
     */
    @PostMapping("/suggest")
    public ResponseEntity<AppResponse<McpOrchestrator.McpResponse>> suggestKeywords(
            @RequestBody Map<String, String> request) {
        String idea = request.getOrDefault("idea", "");
        if (idea.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(AppResponse.of(400, "Please describe your research idea", null));
        }
        log.info("MCP Chatbot: processing idea '{}'...", idea.length() > 50 ? idea.substring(0, 50) + "..." : idea);
        McpOrchestrator.McpResponse response = mcpOrchestrator.suggestKeywordPairs(idea);
        log.info("MCP Chatbot: {} suggestions", response.suggestions.size());
        return ResponseEntity.ok(AppResponse.success("Suggestions ready", response));
    }

    /**
     * List all available keywords in the system.
     */
    @GetMapping("/keywords")
    public ResponseEntity<AppResponse<List<Map<String, Object>>>> listKeywords() {
        List<Map<String, Object>> keywords = graphExplorer.listAllKeywords();
        return ResponseEntity.ok(AppResponse.success("Keywords listed", keywords));
    }

    /**
     * Get co-occurring keywords for a given keyword.
     */
    @GetMapping("/cooccurring")
    public ResponseEntity<AppResponse<List<Map<String, Object>>>> getCooccurring(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "3") int yearsBack) {
        List<Map<String, Object>> results = graphExplorer.getCooccurringKeywords(
                keyword.toLowerCase().trim(), yearsBack);
        return ResponseEntity.ok(AppResponse.success("Co-occurring keywords", results));
    }

    /**
     * Get gap score between two keywords.
     */
    @GetMapping("/gap-score")
    public ResponseEntity<AppResponse<Map<String, Object>>> getGapScore(
            @RequestParam String kw1,
            @RequestParam String kw2) {
        Map<String, Object> score = graphExplorer.getGapScore(
                kw1.toLowerCase().trim(), kw2.toLowerCase().trim());
        return ResponseEntity.ok(AppResponse.success("Gap score calculated", score));
    }

    /**
     * Start a focused crawl for specific keywords.
     * POST body: { "keywords": ["deep learning", "neural network", ...] }
     * Returns taskId for progress tracking.
     */
    @PostMapping("/crawl")
    public ResponseEntity<AppResponse<Map<String, String>>> startCrawl(
            @RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> keywords = (List<String>) request.getOrDefault("keywords", List.of());
        if (keywords.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(AppResponse.of(400, "Please provide keywords to crawl", null));
        }
        String taskId = crawlService.startCrawl(keywords);
        return ResponseEntity.ok(AppResponse.success("Crawl started",
                Map.of("taskId", taskId, "keywords", keywords.toString())));
    }

    /**
     * Get crawl progress by taskId.
     */
    @GetMapping("/crawl/progress/{taskId}")
    public ResponseEntity<AppResponse<GapDataCrawlService.CrawlProgress>> getCrawlProgress(
            @PathVariable String taskId) {
        GapDataCrawlService.CrawlProgress progress = crawlService.getProgress(taskId);
        if (progress == null) {
            return ResponseEntity.ok(AppResponse.of(404, "Task not found", null));
        }
        return ResponseEntity.ok(AppResponse.success("Progress", progress));
    }

    /**
     * Match user's research idea against available Neo4j keywords.
     * Returns exact matches, fuzzy suggestions, unmatched terms, and available landscape.
     * Useful for the FE to show immediate feedback before invoking the full MCP chatbot.
     */
    @PostMapping("/match-keywords")
    public ResponseEntity<AppResponse<KeywordMatchResult>> matchKeywords(
            @RequestBody Map<String, String> body) {
        String ideaText = body.get("ideaText");
        if (ideaText == null || ideaText.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(AppResponse.of(400, "Please provide ideaText", null));
        }
        KeywordMatchResult result = keywordMatchingService.matchIdea(ideaText);
        return ResponseEntity.ok(AppResponse.success("Keyword matching complete", result));
    }

    /**
     * Get gap explorer idea history for the current user.
     */
    @GetMapping("/idea-history")
    public ResponseEntity<AppResponse<List<RecentSearchResponse>>> getIdeaHistory(
            @RequestParam(defaultValue = "10") int limit,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.ok(AppResponse.success("Idea history", List.of()));
        }
        List<RecentSearchResponse> history = historyService.getGapIdeaHistory(authentication.getName(), limit);
        return ResponseEntity.ok(AppResponse.success("Idea history", history));
    }

    /**
     * Save a gap explorer idea to history.
     */
    @PostMapping("/idea-history")
    public ResponseEntity<AppResponse<Void>> saveIdeaHistory(
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.ok(AppResponse.success("Idea saved", null));
        }
        String ideaText = body.get("ideaText");
        if (ideaText == null || ideaText.isBlank()) {
            return ResponseEntity.badRequest().body(AppResponse.of(400, "ideaText is required", null));
        }
        historyService.recordSearch(authentication.getName(), ideaText, "GAP_IDEA");
        return ResponseEntity.ok(AppResponse.success("Idea saved", null));
    }

    /**
     * Delete gap explorer idea history entries.
     * Body: {"ideaText": "..."} to delete one, or {} to clear all.
     */
    @DeleteMapping("/idea-history")
    public ResponseEntity<AppResponse<Void>> deleteIdeaHistory(
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.ok(AppResponse.success("History updated", null));
        }
        String ideaText = body != null ? body.get("ideaText") : null;
        if (ideaText != null && !ideaText.isBlank()) {
            historyService.deleteGapIdea(authentication.getName(), ideaText);
        } else {
            historyService.clearGapIdeas(authentication.getName());
        }
        return ResponseEntity.ok(AppResponse.success("History updated", null));
    }

    private List<Integer> parseYears(String years) {
        try {
            return java.util.Arrays.stream(years.split(","))
                    .map(String::trim)
                    .map(Integer::parseInt)
                    .toList();
        } catch (NumberFormatException e) {
            int current = Year.now().getValue();
            return List.of(current - 2, current - 1, current);
        }
    }
}
