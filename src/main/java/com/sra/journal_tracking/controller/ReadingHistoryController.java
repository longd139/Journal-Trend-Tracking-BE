package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.history.ReadingHistoryResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.ReadingHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class ReadingHistoryController {

    private final ReadingHistoryService readingHistoryService;

    @Operation(
            summary = "Get user reading history",
            description = "Returns the most recently viewed papers for the current user. "
                        + "Deduplicated by paper ID — only the latest view per paper is returned."
    )
    @GetMapping("/reading-history")
    public ResponseEntity<AppResponse<List<ReadingHistoryResponse>>> getReadingHistory(
            Authentication authentication,
            @RequestParam(defaultValue = "20") int limit) {

        if (limit < 1) limit = 1;
        if (limit > 50) limit = 50;

        List<ReadingHistoryResponse> history = readingHistoryService
                .getRecentViews(authentication.getName(), limit);
        return ResponseEntity.ok(AppResponse.success("Reading history retrieved", history));
    }
}
