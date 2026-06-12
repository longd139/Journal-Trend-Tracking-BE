package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.keyword.HotKeywordResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.SearchKeywordService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public/keywords")
@RequiredArgsConstructor
public class KeywordController {

    private final SearchKeywordService searchKeywordService;

    @Operation(summary = "Get hot keywords", description = "Returns the most searched keywords ordered by search frequency. Public endpoint, no auth required.")
    @GetMapping("/hot")
    public ResponseEntity<AppResponse<List<HotKeywordResponse>>> getHotKeywords(
            @RequestParam(defaultValue = "20") int limit) {
        if (limit < 1) limit = 1;
        if (limit > 50) limit = 50;
        List<HotKeywordResponse> hotKeywords = searchKeywordService.getHotKeywords(limit);
        return ResponseEntity.ok(AppResponse.success("Hot keywords retrieved", hotKeywords));
    }
}
