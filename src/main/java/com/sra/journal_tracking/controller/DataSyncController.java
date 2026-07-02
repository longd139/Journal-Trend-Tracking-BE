package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.dto.dashboard.DatabaseStatsResponse;
import com.sra.journal_tracking.dto.sync.BulkSyncProgress;
import com.sra.journal_tracking.entity.jpa.SyncLog;
import com.sra.journal_tracking.service.BulkSyncProgressTracker;
import com.sra.journal_tracking.service.DataSyncService;
import com.sra.journal_tracking.service.GraphService;
import com.sra.journal_tracking.service.ScheduledDataSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/sync")
@RequiredArgsConstructor
@Tag(name = "Data Sync (Admin)", description = "APIs for manual data synchronization")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('ADMIN')")
public class DataSyncController {

    private final DataSyncService dataSyncService;
    private final ScheduledDataSyncService scheduledDataSyncService;
    private final BulkSyncProgressTracker bulkSyncProgressTracker;
    private final GraphService graphService;

    @Operation(summary = "Manual trigger OpenAlex Sync", description = "Fetch papers from OpenAlex based on keyword and year range")
    @PostMapping("/openalex")
    public ResponseEntity<AppResponse<Map<String, Object>>> triggerOpenAlexSync(
            @RequestParam(defaultValue = "machine learning") String query,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) Integer yearFrom,
            @RequestParam(required = false) Integer yearTo) {
        SyncLog syncLog = dataSyncService.syncFromOpenAlex(query, limit, yearFrom, yearTo);
        return buildSyncResponse("OpenAlex", query, syncLog);
    }

    @Operation(summary = "Deep sync keywords from OpenAlex", description = "Fetch up to 40k papers per keyword using cursor pagination. Supports multiple keywords separated by comma or newline. Each team member can provide their own API key from https://openalex.org/settings/api")
    @PostMapping("/openalex/deep")
    public ResponseEntity<AppResponse<Map<String, Object>>> triggerDeepSync(
            @RequestParam String query,
            @RequestParam(defaultValue = "500") int limit,
            @RequestParam(required = false) Integer yearFrom,
            @RequestParam(required = false) Integer yearTo,
            @RequestParam(required = false) String mailto,
            @RequestParam(required = false) String apiKey) {

        // Split by comma, newline, or semicolon — support paste multiple keywords
        List<String> keywords = java.util.Arrays.stream(query.split("[,;\n]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();

        if (keywords.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(AppResponse.of(400, "No valid keywords found in query", null));
        }

        Map<String, Object> result = dataSyncService.bulkSyncFromOpenAlex(
                keywords, limit, yearFrom, yearTo, mailto, apiKey);
        return ResponseEntity.ok(AppResponse.success(
                "Deep sync completed for " + keywords.size() + " keyword(s)", result));
    }

    @Operation(summary = "Manual trigger Semantic Scholar Sync", description = "Fetch papers from Semantic Scholar based on keyword and year range")
    @PostMapping("/semantic-scholar")
    public ResponseEntity<AppResponse<Map<String, Object>>> triggerSemanticScholarSync(
            @RequestParam(defaultValue = "machine learning") String query,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) Integer yearFrom,
            @RequestParam(required = false) Integer yearTo) {
        SyncLog syncLog = dataSyncService.syncFromSemanticScholar(query, limit, yearFrom, yearTo);
        return buildSyncResponse("Semantic Scholar", query, syncLog);
    }

    @Operation(summary = "Manual trigger arXiv Sync", description = "Fetch papers from arXiv based on keyword and year range. Rate limited (1 req/3s).")
    @PostMapping("/arxiv")
    public ResponseEntity<AppResponse<Map<String, Object>>> triggerArxivSync(
            @RequestParam(defaultValue = "machine learning") String query,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) Integer yearFrom,
            @RequestParam(required = false) Integer yearTo) {
        SyncLog syncLog = dataSyncService.syncFromArxiv(query, limit, yearFrom, yearTo);
        return buildSyncResponse("arXiv", query, syncLog);
    }

    @Operation(summary = "Manual trigger CORE Sync", description = "Fetch papers from CORE API based on keyword and year range. Requires CORE_API_KEY.")
    @PostMapping("/core")
    public ResponseEntity<AppResponse<Map<String, Object>>> triggerCoreSync(
            @RequestParam(defaultValue = "machine learning") String query,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) Integer yearFrom,
            @RequestParam(required = false) Integer yearTo) {
        SyncLog syncLog = dataSyncService.syncFromCore(query, limit, yearFrom, yearTo);
        return buildSyncResponse("CORE", query, syncLog);
    }

    @Operation(summary = "Clear all papers", description = "Delete ALL papers from SQL Server and Neo4j. WARNING: Irreversible!")
    @DeleteMapping("/clear-all")
    public ResponseEntity<AppResponse<Map<String, Object>>> clearAllPapers() {
        Map<String, Object> result = dataSyncService.clearAllPapers();
        return ResponseEntity.ok(AppResponse.success("All papers and related data deleted successfully", result));
    }

    @Operation(summary = "Clear Neo4j only", description = "Delete ALL nodes and relationships from Neo4j. SQL Server data is preserved.")
    @DeleteMapping("/clear-neo4j")
    public ResponseEntity<AppResponse<Map<String, Object>>> clearNeo4j() {
        try {
            graphService.clearAll();
            return ResponseEntity.ok(AppResponse.success("Neo4j data cleared successfully",
                    Map.of("neo4jCleared", true)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(AppResponse.of(500, "Failed to clear Neo4j: " + e.getMessage(),
                            Map.of("neo4jCleared", false)));
        }
    }

    @Operation(summary = "Get database statistics", description = "Comprehensive overview of all data in SQL and Neo4j")
    @GetMapping("/stats")
    public ResponseEntity<AppResponse<DatabaseStatsResponse>> getDatabaseStats() {
        DatabaseStatsResponse stats = dataSyncService.getDatabaseStats();
        return ResponseEntity.ok(AppResponse.success("Database statistics retrieved", stats));
    }

    // ═══════════════════════════════════════════════════
    //  Auto-sync toggle & notification
    // ═══════════════════════════════════════════════════

    @Operation(summary = "Bulk sync from OpenAlex", description = "Start async bulk sync. Returns a taskId immediately — use GET /bulk/{taskId}/progress to track progress percentage.")
    @PostMapping("/bulk")
    public ResponseEntity<AppResponse<Map<String, Object>>> bulkSync(
            @RequestBody(required = false) Map<String, Object> request) {
        List<String> keywords;
        int papersPerKeyword;
        Integer yearFrom, yearTo;

        if (request != null && request.containsKey("keywords")) {
            @SuppressWarnings("unchecked")
            List<String> kw = (List<String>) request.get("keywords");
            keywords = kw;
            papersPerKeyword = request.containsKey("papersPerKeyword")
                    ? ((Number) request.get("papersPerKeyword")).intValue() : 50;
            yearFrom = request.containsKey("yearFrom")
                    ? ((Number) request.get("yearFrom")).intValue() : null;
            yearTo = request.containsKey("yearTo")
                    ? ((Number) request.get("yearTo")).intValue() : null;
        } else {
            // Default: trending keywords from 2023+
            keywords = List.of("artificial intelligence", "machine learning", "deep learning",
                    "data science", "computer vision", "natural language processing",
                    "large language models", "neural networks", "reinforcement learning",
                    "generative AI", "healthcare AI", "robotics", "quantum computing",
                    "climate change", "cybersecurity", "bioinformatics", "edge computing",
                    "internet of things", "blockchain", "augmented reality");
            papersPerKeyword = 100;
            yearFrom = 2023;
            yearTo = null;
        }

        String taskId = UUID.randomUUID().toString();

        // Register the task BEFORE spawning async so GET /progress works immediately
        bulkSyncProgressTracker.createTask(taskId, keywords.size());

        dataSyncService.bulkSyncFromOpenAlexAsync(taskId, keywords, papersPerKeyword, yearFrom, yearTo);

        Map<String, Object> response = Map.of(
                "taskId", taskId,
                "totalKeywords", keywords.size(),
                "message", "Bulk sync started. Poll GET /bulk/" + taskId + "/progress for status."
        );
        return ResponseEntity.accepted().body(AppResponse.success("Bulk sync started", response));
    }

    @Operation(summary = "Get bulk sync progress", description = "Returns the current progress percentage and status of a bulk sync task.")
    @GetMapping("/bulk/{taskId}/progress")
    public ResponseEntity<AppResponse<BulkSyncProgress>> getBulkSyncProgress(
            @PathVariable String taskId) {
        BulkSyncProgress progress = bulkSyncProgressTracker.getProgress(taskId);
        if (progress == null) {
            return ResponseEntity.ok(AppResponse.of(404,
                    "Task not found: " + taskId + ". The task may have expired or never existed.", null));
        }
        return ResponseEntity.ok(AppResponse.success("Bulk sync progress retrieved", progress));
    }

    @Operation(summary = "Re-extract keywords for all papers", description = "Run keyword extraction on all existing papers. Skips papers that already have OpenAlex keywords.")
    @PostMapping("/re-extract-keywords")
    public ResponseEntity<AppResponse<Map<String, Object>>> reExtractKeywords() {
        Map<String, Object> result = dataSyncService.reExtractKeywords();
        return ResponseEntity.ok(AppResponse.success("Keyword re-extraction completed", result));
    }

    @Operation(summary = "Get auto-sync status", description = "Check if auto-sync is enabled, last sync time and papers count")
    @GetMapping("/auto/status")
    public ResponseEntity<AppResponse<Map<String, Object>>> getAutoSyncStatus() {
        Map<String, Object> status = scheduledDataSyncService.getAutoSyncStatus();
        return ResponseEntity.ok(AppResponse.success("Auto-sync status retrieved", status));
    }

    @Operation(summary = "Toggle auto-sync", description = "Enable or disable automatic daily sync at 2 AM")
    @PutMapping("/auto/toggle")
    public ResponseEntity<AppResponse<Map<String, Object>>> toggleAutoSync(
            @RequestParam(defaultValue = "true") boolean enabled) {
        boolean newStatus = scheduledDataSyncService.toggleAutoSync(enabled);
        Map<String, Object> result = Map.of("autoSyncEnabled", newStatus);
        return ResponseEntity.ok(AppResponse.success(
                "Auto-sync " + (newStatus ? "enabled" : "disabled"), result));
    }

    @Operation(summary = "Get admin notification", description = "Returns count of new papers synced by auto-sync. Check this on admin dashboard load.")
    @GetMapping("/notification")
    public ResponseEntity<AppResponse<Map<String, Object>>> getNotification() {
        Map<String, Object> status = scheduledDataSyncService.getAutoSyncStatus();
        Map<String, Object> data = Map.of(
                "newPapers", status.getOrDefault("lastPapersCount", 0),
                "lastSyncTime", status.getOrDefault("lastSyncTime", "Never"),
                "autoSyncEnabled", status.getOrDefault("enabled", true)
        );
        return ResponseEntity.ok(AppResponse.success("Notification data retrieved", data));
    }

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
