package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.journal.JournalCategoryResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.JournalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/journals")
@RequiredArgsConstructor
@Tag(name = "Journal Categories", description = "Journal categorization by research field")
public class JournalController {

    private final JournalService journalService;

    @Operation(summary = "Get all journal categories", description = "Returns all top-level research fields with their top-tier journals. Use this when the journal search page loads without a keyword.")
    @ApiResponse(responseCode = "200", description = "Journal categories retrieved successfully")
    @GetMapping("/categories")
    public ResponseEntity<AppResponse<List<JournalCategoryResponse>>> getCategories() {
        List<JournalCategoryResponse> categories = journalService.getJournalCategories();
        return ResponseEntity.ok(AppResponse.success("Journal categories retrieved successfully", categories));
    }

    @Operation(summary = "Get top journals by field", description = "Returns top-tier journals for a specific research field.")
    @ApiResponse(responseCode = "200", description = "Top journals retrieved successfully")
    @GetMapping("/fields/{fieldId}")
    public ResponseEntity<AppResponse<JournalCategoryResponse>> getTopJournalsByField(
            @PathVariable UUID fieldId) {
        JournalCategoryResponse category = journalService.getTopJournalsByField(fieldId);
        return ResponseEntity.ok(AppResponse.success("Top journals retrieved successfully", category));
    }
}
