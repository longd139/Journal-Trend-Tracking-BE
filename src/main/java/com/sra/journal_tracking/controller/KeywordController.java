package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.keyword.HotKeywordResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.dto.search.TrendingKeywordResponse;
import com.sra.journal_tracking.repository.jpa.KeywordRepository;
import com.sra.journal_tracking.service.SearchKeywordService;
import com.sra.journal_tracking.service.TrendingKeywordService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public/keywords")
@RequiredArgsConstructor
public class KeywordController {

    private final SearchKeywordService searchKeywordService;
    private final TrendingKeywordService trendingKeywordService;
    private final KeywordRepository keywordRepository;

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

    @Operation(summary = "Keyword autocomplete", description = "Returns keyword suggestions matching the given prefix (min 2 chars), ordered by paper count. Used for typeahead in the search box. Public endpoint, no auth required.")
    @GetMapping("/suggest")
    public ResponseEntity<AppResponse<List<String>>> suggestKeywords(
            @RequestParam String q,
            @RequestParam(defaultValue = "8") int limit) {
        if (q == null || q.trim().length() < 2) {
            return ResponseEntity.ok(AppResponse.success("Suggestions", List.of()));
        }
        if (limit < 1) limit = 1;
        if (limit > 15) limit = 15;
        List<String> suggestions = keywordRepository.findKeywordSuggestions(q.trim(), PageRequest.of(0, limit));
        return ResponseEntity.ok(AppResponse.success("Suggestions", suggestions));
    }
}
