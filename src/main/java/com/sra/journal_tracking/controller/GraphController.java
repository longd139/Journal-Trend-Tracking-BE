package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.GraphResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/graphs")
public class GraphController {

    private static final Logger log = LoggerFactory.getLogger(GraphController.class);
    private static final int DEFAULT_DEPTH = 3;
    private static final int MAX_DEPTH = 4;

    private final GraphService graphService;
    private final GraphSearchTaskTracker taskTracker;
    private final GraphSearchProcessor searchProcessor;

    public GraphController(GraphService graphService,
                           GraphSearchTaskTracker taskTracker,
                           GraphSearchProcessor searchProcessor) {
        this.graphService = graphService;
        this.taskTracker = taskTracker;
        this.searchProcessor = searchProcessor;
    }

    @GetMapping("/paper/{paperId}")
    public ResponseEntity<GraphResponse> getGraphForPaper(@PathVariable String paperId) {
        GraphResponse response = graphService.getPaperKeywordGraph(paperId);
        return ResponseEntity.ok(response);
    }

    // ═══════════════════════════════════════════════════════════
    //  Sync endpoint (quick: uses cached Neo4j data only)
    // ═══════════════════════════════════════════════════════════

    /**
     * Quick paper-keyword graph from Neo4j (no expansion, no sync).
     * Use when you just need existing paper-keyword relationships.
     */
    @GetMapping("/keyword/quick")
    public ResponseEntity<AppResponse<GraphResponse>> getQuickKeywordGraph(
            @RequestParam("keyword") String keyword) {
        GraphResponse response = graphService.getKeywordGraph(keyword);
        boolean isNewTerm = response.getNodes().isEmpty();
        return ResponseEntity.ok(AppResponse.success(
                isNewTerm ? "No cached data for this term. Use POST /search for full exploration."
                        : "Keyword graph retrieved",
                response));
    }

    // ═══════════════════════════════════════════════════════════
    //  Async search endpoint (recommended: handles new terms)
    // ═══════════════════════════════════════════════════════════

    /**
     * Start an async keyword graph search with depth-based expansion.
     * Returns immediately with a taskId. Poll GET /status/{taskId} for results.
     *
     * Default depth = 3 for rich keyword exploration.
     * For new terms (not in Neo4j), papers are synced from OpenAlex + Semantic Scholar first.
     */
    @PostMapping("/keyword/search")
    public ResponseEntity<AppResponse<Map<String, Object>>> searchKeywordGraph(
            @RequestParam("keyword") String keyword,
            @RequestParam(name = "depth", defaultValue = "" + DEFAULT_DEPTH) int depth) {

        int safeDepth = Math.max(0, Math.min(depth, MAX_DEPTH));

        // Quick check: is this term already in Neo4j? (lightweight — no graph build)
        boolean isNewTerm = !graphService.keywordExists(keyword);

        // Create async task
        String taskId = taskTracker.createTask(keyword, safeDepth);
        if (isNewTerm) {
            taskTracker.updateProgress(taskId, "New term! Syncing from academic databases (30-60s)...");
        }

        // Start background processing (pass isNewTerm to avoid re-checking)
        searchProcessor.processAsync(taskId, keyword, safeDepth, isNewTerm);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskId", taskId);
        response.put("keyword", keyword);
        response.put("depth", safeDepth);
        response.put("isNewTerm", isNewTerm);
        response.put("status", "PROCESSING");

        String message = isNewTerm
                ? "This is a new research term. We're searching academic databases — it may take up to 60 seconds."
                : "Building keyword graph (depth=" + safeDepth + "). Please wait...";
        response.put("message", message);

        return ResponseEntity.accepted().body(AppResponse.success(message, response));
    }

    // ═══════════════════════════════════════════════════════════
    //  Status polling endpoint
    // ═══════════════════════════════════════════════════════════

    /**
     * Poll for async search progress.
     * Returns PROCESSING with a progress message, or COMPLETED with the graph result.
     */
    @GetMapping("/keyword/status/{taskId}")
    public ResponseEntity<AppResponse<Map<String, Object>>> getSearchStatus(
            @PathVariable String taskId) {

        GraphSearchTaskTracker.GraphSearchTask task = taskTracker.getTask(taskId);

        if (task == null) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", "NOT_FOUND");
            error.put("message", "Task not found or expired");
            return ResponseEntity.ok(AppResponse.success("Task not found", error));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskId", task.taskId);
        response.put("keyword", task.keyword);
        response.put("depth", task.depth);
        response.put("status", task.status);
        response.put("progress", task.progress);

        if ("COMPLETED".equals(task.status) && task.result != null) {
            response.put("result", task.result);
        }

        return ResponseEntity.ok(AppResponse.success(
                "COMPLETED".equals(task.status) ? "Search completed" : "Search in progress",
                response));
    }
}
