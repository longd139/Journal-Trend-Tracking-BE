package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.GraphResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.GraphService;
import com.sra.journal_tracking.service.GroqService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/graphs")
public class GraphController {

    private final GraphService graphService;
    private final GroqService groqService;

    public GraphController(GraphService graphService, GroqService groqService) {
        this.graphService = graphService;
        this.groqService = groqService;
    }

    @GetMapping("/paper/{paperId}")
    public ResponseEntity<GraphResponse> getGraphForPaper(@PathVariable String paperId) {
        GraphResponse response = graphService.getPaperKeywordGraph(paperId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/keyword")
    public ResponseEntity<AppResponse<GraphResponse>> getKeywordGraph(
            @RequestParam("keyword") String keyword) {
        GraphResponse response = graphService.getKeywordGraph(keyword);
        return ResponseEntity.ok(AppResponse.success("Keyword graph retrieved", response));
    }

    /**
     * Co-occurrence graph: tìm keyword liên quan thông qua paper chung.
     * VD: search "car" → hiển thị "vehicle", "engine", "tire"
     *     vì chúng cùng xuất hiện trong nhiều paper với "car".
     */
    @GetMapping("/keyword/related")
    public ResponseEntity<AppResponse<GraphResponse>> getRelatedKeywordsGraph(
            @RequestParam("keyword") String keyword) {
        GraphResponse response = graphService.getKeywordCooccurrenceGraph(keyword);
        return ResponseEntity.ok(AppResponse.success("Related keywords graph retrieved", response));
    }

    /**
     * Enhanced keyword graph using AI (Groq LLM).
     * Groq expands the user's keyword into 15 related keywords,
     * then Neo4j is queried for each to build a richer graph.
     *
     * Example: search "bike" →
     *   Groq suggests: "bicycle", "cycling", "mountain bike", "motorcycle", ...
     *   Neo4j finds which of these exist in the database with paper connections
     */
    @GetMapping("/keyword/enhanced")
    public ResponseEntity<AppResponse<GraphResponse>> getEnhancedKeywordGraph(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "depth", defaultValue = "1") int depth) {

        if (keyword == null || keyword.isBlank()) {
            return ResponseEntity.badRequest().body(
                    AppResponse.of(400, "Keyword is required", null));
        }

        if (depth < 1) depth = 1;
        if (depth > 5) depth = 5; // giới hạn depth tối đa để tránh query quá nặng

        String trimmedKeyword = keyword.trim();

        // Step 1: Expand keyword using Groq AI
        List<String> expandedKeywords = groqService.expandKeyword(trimmedKeyword);

        // Step 2: Check if Groq actually expanded (or just fell back to original)
        boolean groqAvailable = expandedKeywords.size() > 1
                || (expandedKeywords.size() == 1
                    && !expandedKeywords.get(0).equalsIgnoreCase(trimmedKeyword));

        GraphResponse response;
        String message;

        if (groqAvailable) {
            // Groq worked → build multi-level keyword-only graph with depth
            List<String> allKeywords = new ArrayList<>();
            allKeywords.add(trimmedKeyword);
            allKeywords.addAll(expandedKeywords);

            response = graphService.getKeywordOnlyGraph(trimmedKeyword, allKeywords, depth);
            message = String.format("Enhanced keyword graph retrieved (depth=%d, %d AI suggestions, %d nodes)",
                    depth, expandedKeywords.size(), response.getNodes().size());
        } else {
            // Groq unavailable → fallback to Neo4j keyword graph (single level)
            response = graphService.getKeywordGraph(trimmedKeyword);
            message = String.format("Keyword graph retrieved via Neo4j (AI unavailable, %d nodes)",
                    response.getNodes().size());
        }

        return ResponseEntity.ok(AppResponse.success(message, response));
    }
}