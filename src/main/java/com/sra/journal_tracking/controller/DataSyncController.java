package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.entity.jpa.AutoSyncKeyword;
import com.sra.journal_tracking.entity.jpa.SyncLog;
import com.sra.journal_tracking.service.DataSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/sync")
@RequiredArgsConstructor
@Tag(name = "Data Sync (Admin)", description = "APIs for manual data synchronization")
@SecurityRequirement(name = "Bearer Authentication")
public class DataSyncController {

    private final DataSyncService dataSyncService;

    @Operation(summary = "Manual trigger OpenAlex Sync", description = "Fetch papers from OpenAlex based on keyword")
    @PostMapping("/openalex")
    public ResponseEntity<AppResponse<Map<String, Object>>> triggerOpenAlexSync(
            @RequestParam(defaultValue = "machine learning") String query,
            @RequestParam(defaultValue = "10") int limit) {
        SyncLog syncLog = dataSyncService.syncFromOpenAlex(query, limit);
        return buildSyncResponse("OpenAlex", query, syncLog);
    }

    @Operation(summary = "Backfill keywords to Neo4j", description = "Sync all existing keywords from SQL PAPER_KEYWORD table to Neo4j graph")
    @PostMapping("/backfill-keywords")
    public ResponseEntity<AppResponse<Map<String, Object>>> backfillKeywords(
            @RequestParam(defaultValue = "50") int batchSize) {
        int count = dataSyncService.backfillKeywordsToNeo4j(batchSize);
        Map<String, Object> data = Map.of(
                "syncedPapers", count,
                "message", "Successfully synced " + count + " papers' keywords from SQL to Neo4j"
        );
        return ResponseEntity.ok(AppResponse.success("Keyword backfill completed", data));
    }

    // ============================================
    //  AUTO-SYNC KEYWORD MANAGEMENT
    // ============================================

    @Operation(summary = "Add auto-sync keyword", description = "Register a keyword for automatic periodic sync from OpenAlex")
    @PostMapping("/auto-keywords")
    public ResponseEntity<AppResponse<AutoSyncKeyword>> addAutoSyncKeyword(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "60") int intervalMinutes) {
        AutoSyncKeyword saved = dataSyncService.addAutoSyncKeyword(keyword, intervalMinutes);
        return ResponseEntity.ok(AppResponse.success("Auto-sync keyword added", saved));
    }

    @Operation(summary = "List auto-sync keywords", description = "Get all registered auto-sync keywords")
    @GetMapping("/auto-keywords")
    public ResponseEntity<AppResponse<List<AutoSyncKeyword>>> getAutoSyncKeywords() {
        List<AutoSyncKeyword> keywords = dataSyncService.getAutoSyncKeywords();
        return ResponseEntity.ok(AppResponse.success("Auto-sync keywords retrieved", keywords));
    }

    @Operation(summary = "Update auto-sync keyword", description = "Update keyword text, interval, or enable/disable")
    @PutMapping("/auto-keywords/{id}")
    public ResponseEntity<AppResponse<AutoSyncKeyword>> updateAutoSyncKeyword(
            @PathVariable UUID id,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer intervalMinutes,
            @RequestParam(required = false) Boolean enabled) {
        AutoSyncKeyword updated = dataSyncService.updateAutoSyncKeyword(id, keyword, intervalMinutes, enabled);
        return ResponseEntity.ok(AppResponse.success("Auto-sync keyword updated", updated));
    }

    @Operation(summary = "Delete auto-sync keyword", description = "Remove a keyword from auto-sync schedule")
    @DeleteMapping("/auto-keywords/{id}")
    public ResponseEntity<AppResponse<Void>> deleteAutoSyncKeyword(@PathVariable UUID id) {
        dataSyncService.deleteAutoSyncKeyword(id);
        return ResponseEntity.ok(AppResponse.success("Auto-sync keyword deleted", null));
    }

    // ============================================

    private ResponseEntity<AppResponse<Map<String, Object>>> buildSyncResponse(String sourceName, String query, SyncLog syncLog) {
        Map<String, Object> data = Map.of(
                "source", sourceName,
                "query", query,
                "status", syncLog.getStatus(),
                "papersFetched", syncLog.getPapersFetched() != null ? syncLog.getPapersFetched() : 0,
                "papersInserted", syncLog.getPapersInserted() != null ? syncLog.getPapersInserted() : 0,
                "errorMessage", syncLog.getErrorMessage() != null ? syncLog.getErrorMessage() : ""
        );

        if ("failed".equalsIgnoreCase(syncLog.getStatus())) {
            return ResponseEntity.internalServerError()
                    .body(AppResponse.of(500, sourceName + " sync failed for query: " + query, data));
        }

        return ResponseEntity.ok(AppResponse.success(sourceName + " sync completed for query: " + query, data));
    }
}
