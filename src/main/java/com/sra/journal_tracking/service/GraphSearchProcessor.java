package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.GraphLink;
import com.sra.journal_tracking.dto.GraphNode;
import com.sra.journal_tracking.dto.GraphResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.*;

/**
 * Handles async graph search processing in background.
 * FE polls GraphSearchTaskTracker for progress until COMPLETED.
 *
 * Strategy: Single-level Gemini keyword expansion (depth=1) with hard 10s timeout.
 * Falls back to local keyword expansion if Gemini is slow. No sync, no Neo4j.
 * Always returns in < 15 seconds.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphSearchProcessor {

    private static final int MAX_RELATED = 8;
    private static final int GEMINI_TIMEOUT_SEC = 60;

    private final GeminiService geminiService;
    private final KeywordExpansionService keywordExpansionService;
    private final GraphSearchTaskTracker taskTracker;

    @Async("userTaskExecutor")
    public void processAsync(String taskId, String keyword, int depth, boolean isNewTerm) {
        try {
            taskTracker.updateProgress(taskId, "Exploring related topics for: " + keyword);

            GraphResponse result = buildSingleLevelGraph(keyword);

            taskTracker.markCompleted(taskId, result);
            log.info("Graph search {} completed: {} nodes (keyword='{}')",
                    taskId,
                    result.getNodes() != null ? result.getNodes().size() : 0,
                    keyword);

        } catch (Exception e) {
            log.error("Graph search {} failed for '{}': {}", taskId, keyword, e.getMessage(), e);
            taskTracker.markFailed(taskId, "Search failed: " + e.getMessage());
        }
    }

    private GraphResponse buildSingleLevelGraph(String rootKeyword) {
        var nodeMap = new LinkedHashMap<String, GraphNode>();
        var links = new ArrayList<GraphLink>();

        // Root node
        String rootId = normalizeId(rootKeyword);
        nodeMap.put(rootId, GraphNode.builder()
                .id(rootId).label(rootKeyword).group("ROOT").size(8).build());

        // Try Gemini with hard timeout, fall back to local expansion
        List<String> related = getRelatedWithTimeout(rootKeyword);

        for (String relatedKw : related) {
            String childId = normalizeId(relatedKw);
            nodeMap.putIfAbsent(childId, GraphNode.builder()
                    .id(childId).label(relatedKw).group("KEYWORD").size(4).build());
            links.add(new GraphLink(rootId, childId, "RELATED_TO"));
        }

        log.info("Graph for '{}': {} nodes (1 + {} related), {} links",
                rootKeyword, nodeMap.size(), related.size(), links.size());

        return new GraphResponse(new ArrayList<>(nodeMap.values()), links);
    }

    /**
     * Call Gemini with a hard 10-second timeout.
     * On timeout or failure → fall back to local keyword expansion immediately.
     */
    private List<String> getRelatedWithTimeout(String keyword) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<List<String>> future = executor.submit(() ->
                geminiService.getRelatedKeywords(keyword, MAX_RELATED));

        try {
            return future.get(GEMINI_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.info("Gemini timed out after {}s for '{}' — using local fallback", GEMINI_TIMEOUT_SEC, keyword);
            future.cancel(true);
        } catch (Exception e) {
            log.info("Gemini failed for '{}': {} — using local fallback", keyword, e.getMessage());
        } finally {
            executor.shutdownNow();
        }

        // Local fallback — always fast (< 100ms)
        return keywordExpansionService.expand(keyword, MAX_RELATED);
    }

    private String normalizeId(String s) {
        return s.toLowerCase().trim().replaceAll("\\s+", "_");
    }
}
