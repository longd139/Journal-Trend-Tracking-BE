package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.GraphResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Handles async graph search processing in background.
 * FE polls GraphSearchTaskTracker for progress until COMPLETED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphSearchProcessor {

    private static final int GRAPH_SYNC_LIMIT = 50;

    private final DataSyncService dataSyncService;
    private final GraphService graphService;
    private final GeminiService geminiService;
    private final KeywordExpansionService keywordExpansionService;
    private final GraphSearchTaskTracker taskTracker;

    /**
     * Process a graph search asynchronously.
     * 1. If new term, sync from OpenAlex + Semantic Scholar
     * 2. If depth > 0, use Gemini (with local fallback) for keyword expansion
     * 3. Build and store the final graph response
     *
     * @param isNewTerm pre-checked by controller (avoids redundant Neo4j query)
     */
    @Async("taskExecutor")
    public void processAsync(String taskId, String keyword, int depth, boolean isNewTerm) {
        try {
            // ── Phase 1: Sync if needed (isNewTerm already checked by controller) ──
            if (isNewTerm) {
                taskTracker.updateProgress(taskId, "New term detected! Syncing papers from academic databases. This may take 30-60 seconds...");
                syncPapers(taskId, keyword);
            }

            // ── Phase 2: Build graph ──
            GraphResponse result;
            if (depth > 0) {
                taskTracker.updateProgress(taskId, "Expanding keywords (depth=" + depth + ")...");
                result = buildKeywordExpansionGraph(taskId, keyword, depth);
            } else {
                taskTracker.updateProgress(taskId, "Building paper-keyword graph...");
                result = graphService.getKeywordGraph(keyword);
            }

            // ── Phase 3: Done ──
            taskTracker.markCompleted(taskId, result);

        } catch (Exception e) {
            log.error("Async graph search {} failed for '{}': {}", taskId, keyword, e.getMessage(), e);
            taskTracker.markFailed(taskId, "Search failed: " + e.getMessage());
        }
    }

    // ── Paper sync ──

    private void syncPapers(String taskId, String keyword) {
        int limit = GRAPH_SYNC_LIMIT;

        taskTracker.updateProgress(taskId, "Fetching from OpenAlex...");
        try {
            dataSyncService.syncFromOpenAlex(keyword, limit);
        } catch (Exception e) {
            log.warn("OpenAlex sync failed for '{}': {}", keyword, e.getMessage());
        }

        taskTracker.updateProgress(taskId, "Fetching from Semantic Scholar...");
        try {
            dataSyncService.syncFromSemanticScholar(keyword, limit);
        } catch (Exception e) {
            log.warn("Semantic Scholar sync failed for '{}': {}", keyword, e.getMessage());
        }

        // Background enrichment
        try {
            keywordExpansionService.expand(keyword, 3)
                    .stream()
                    .filter(term -> !term.equalsIgnoreCase(keyword.trim()))
                    .forEach(term -> dataSyncService.syncFromOpenAlexAsync(term, limit));
        } catch (Exception ignored) {}
    }

    // ── Keyword expansion graph (same logic as GraphController) ──

    private GraphResponse buildKeywordExpansionGraph(String taskId, String rootKeyword, int maxDepth) {
        var nodeMap = new java.util.LinkedHashMap<String, com.sra.journal_tracking.dto.GraphNode>();
        var links = new java.util.ArrayList<com.sra.journal_tracking.dto.GraphLink>();
        var seen = new java.util.HashSet<String>();
        var edgesSeen = new java.util.HashSet<String>();

        String rootId = normalizeId(rootKeyword);
        nodeMap.put(rootId, com.sra.journal_tracking.dto.GraphNode.builder()
                .id(rootId).label(rootKeyword).group("ROOT").size(maxDepth + 2).build());
        seen.add(rootKeyword.toLowerCase());

        var queue = new java.util.ArrayDeque<KeywordTask>();
        queue.add(new KeywordTask(rootKeyword, 1));

        int totalExpanded = 0;
        while (!queue.isEmpty()) {
            KeywordTask task = queue.poll();
            if (task.depth > maxDepth) continue;

            totalExpanded++;
            taskTracker.updateProgress(taskId,
                    "Expanding '" + task.keyword + "' (depth " + task.depth + "/" + maxDepth + ")...");

            java.util.List<String> related = geminiService.getRelatedKeywords(task.keyword, 4);
            int addedThisLevel = 0;

            for (String relatedKw : related) {
                if (addedThisLevel >= 5) break;

                String childId = normalizeId(relatedKw);
                String parentId = normalizeId(task.keyword);
                String edgeKey = parentId + "→" + childId;

                if (edgesSeen.contains(edgeKey)) continue;
                edgesSeen.add(edgeKey);

                nodeMap.putIfAbsent(childId, com.sra.journal_tracking.dto.GraphNode.builder()
                        .id(childId).label(relatedKw).group("KEYWORD")
                        .size(Math.max(1, maxDepth - task.depth + 1))
                        .build());

                links.add(new com.sra.journal_tracking.dto.GraphLink(parentId, childId, "RELATED_TO"));

                if (!seen.contains(relatedKw.toLowerCase())) {
                    seen.add(relatedKw.toLowerCase());
                    queue.add(new KeywordTask(relatedKw, task.depth + 1));
                }
                addedThisLevel++;
            }
        }

        log.info("Async keyword expansion: {} nodes, {} links (depth={}, root='{}', expanded={})",
                nodeMap.size(), links.size(), maxDepth, rootKeyword, totalExpanded);

        return new GraphResponse(new java.util.ArrayList<>(nodeMap.values()), links);
    }

    private String normalizeId(String s) {
        return s.toLowerCase().trim().replaceAll("\\s+", "_");
    }

    private record KeywordTask(String keyword, int depth) {}
}
