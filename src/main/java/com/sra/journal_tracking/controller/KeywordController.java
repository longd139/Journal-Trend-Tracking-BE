package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.keyword.HotKeywordResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.dto.search.TrendingKeywordResponse;
import com.sra.journal_tracking.repository.jpa.KeywordRepository;
import com.sra.journal_tracking.service.SearchKeywordService;
import com.sra.journal_tracking.service.TrendingKeywordService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/public/keywords")
@RequiredArgsConstructor
public class KeywordController {

    private final SearchKeywordService searchKeywordService;
    private final TrendingKeywordService trendingKeywordService;
    private final KeywordRepository keywordRepository;

    @Value("${app.openalex-email:}")
    private String openalexEmail;

    @Operation(summary = "Get hot keywords", description = "Returns the most searched keywords ordered by search frequency. Public endpoint, no auth required.")
    @GetMapping("/hot")
    public ResponseEntity<AppResponse<List<HotKeywordResponse>>> getHotKeywords(
            @RequestParam(defaultValue = "20") int limit) {
        if (limit < 1) limit = 1;
        if (limit > 50) limit = 50;
        List<HotKeywordResponse> hotKeywords = searchKeywordService.getHotKeywords(limit);
        return ResponseEntity.ok(AppResponse.success("Hot keywords retrieved", hotKeywords));
    }

    @Operation(summary = "Get trending keywords", description = "Returns curated trending keywords from the config file (Google Trends / academic reports). Used for the 'Popular Searches / Trending Now' zero-state on the search page. Public endpoint, no auth required.")
    @GetMapping("/trending")
    public ResponseEntity<AppResponse<List<TrendingKeywordResponse>>> getTrendingKeywords(
            @RequestParam(defaultValue = "10") int limit) {
        if (limit < 1) limit = 1;
        if (limit > 10) limit = 10;
        List<TrendingKeywordResponse> trendingKeywords = trendingKeywordService.getTrendingKeywords(limit);
        return ResponseEntity.ok(AppResponse.success("Trending keywords retrieved", trendingKeywords));
    }

    @Operation(summary = "Keyword autocomplete via OpenAlex", description = "Fetches keyword suggestions from OpenAlex concepts autocomplete API. Falls back to local SQL keyword table if external API is unavailable. Public endpoint, no auth required.")
    @GetMapping("/suggest")
    public ResponseEntity<AppResponse<List<String>>> suggestKeywords(
            @RequestParam String q,
            @RequestParam(defaultValue = "8") int limit) {
        if (q == null || q.trim().length() < 2) {
            return ResponseEntity.ok(AppResponse.success("Suggestions", List.of()));
        }
        if (limit < 1) limit = 1;
        if (limit > 15) limit = 15;

        // Try OpenAlex concepts autocomplete first
        List<String> suggestions = fetchFromOpenAlexAutocomplete(q.trim(), limit);

        // Fallback to local SQL keyword table if OpenAlex fails or returns empty
        if (suggestions.isEmpty()) {
            suggestions = keywordRepository.findKeywordSuggestions(q.trim(), PageRequest.of(0, limit));
        }

        return ResponseEntity.ok(AppResponse.success("Suggestions", suggestions));
    }

    /**
     * Calls OpenAlex concepts autocomplete API.
     * GET https://api.openalex.org/autocomplete/concepts?q=...&mailto=...
     * Returns concept display names as keyword suggestions.
     */
    @SuppressWarnings("unchecked")
    private List<String> fetchFromOpenAlexAutocomplete(String query, int limit) {
        try {
            String url = "https://api.openalex.org/autocomplete/concepts"
                    + "?q=" + query
                    + "&mailto=" + (openalexEmail != null ? openalexEmail : "scitrack@example.com");
            RestTemplate rest = new RestTemplate();
            Map<String, Object> response = rest.getForObject(url, Map.class);
            if (response == null || !response.containsKey("results")) return List.of();

            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
            List<String> suggestions = new ArrayList<>();
            for (Map<String, Object> r : results) {
                String name = (String) r.get("display_name");
                if (name != null && !name.isBlank()) {
                    suggestions.add(name);
                    if (suggestions.size() >= limit) break;
                }
            }
            return suggestions;
        } catch (Exception e) {
            log.warn("OpenAlex autocomplete failed for '{}': {}", query, e.getMessage());
            return List.of();
        }
    }
}
