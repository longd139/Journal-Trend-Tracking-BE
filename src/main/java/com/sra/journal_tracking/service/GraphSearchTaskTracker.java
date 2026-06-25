package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.GraphResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory tracker for async graph search tasks.
 * FE polls status until COMPLETED, then renders the graph.
 */
@Slf4j
@Service
public class GraphSearchTaskTracker {

    private final ConcurrentHashMap<String, GraphSearchTask> tasks = new ConcurrentHashMap<>();

    /**
     * Create a new task and return its ID.
     */
    public String createTask(String keyword, int depth) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        GraphSearchTask task = new GraphSearchTask();
        task.taskId = taskId;
        task.keyword = keyword;
        task.depth = depth;
        task.status = "PROCESSING";
        task.progress = "Initializing search...";
        task.createdAt = LocalDateTime.now();
        tasks.put(taskId, task);
        log.info("Graph search task {} created for '{}' (depth={})", taskId, keyword, depth);
        return taskId;
    }

    public void updateProgress(String taskId, String progress) {
        GraphSearchTask task = tasks.get(taskId);
        if (task != null) {
            task.progress = progress;
        }
    }

    public void markCompleted(String taskId, GraphResponse result) {
        GraphSearchTask task = tasks.get(taskId);
        if (task != null) {
            task.status = "COMPLETED";
            task.progress = "Done";
            task.result = result;
            task.completedAt = LocalDateTime.now();
            log.info("Graph search task {} completed: {} nodes, {} links",
                    taskId,
                    result != null && result.getNodes() != null ? result.getNodes().size() : 0,
                    result != null && result.getLinks() != null ? result.getLinks().size() : 0);
        }
    }

    public void markFailed(String taskId, String error) {
        GraphSearchTask task = tasks.get(taskId);
        if (task != null) {
            task.status = "FAILED";
            task.progress = error;
            task.completedAt = LocalDateTime.now();
            log.warn("Graph search task {} failed: {}", taskId, error);
        }
    }

    public GraphSearchTask getTask(String taskId) {
        return tasks.get(taskId);
    }

    /**
     * Clean up old tasks (older than 30 minutes) to prevent memory leaks.
     */
    public void cleanup() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
        tasks.entrySet().removeIf(entry ->
                entry.getValue().completedAt != null && entry.getValue().completedAt.isBefore(cutoff));
    }

    // ── Task state ──

    public static class GraphSearchTask {
        public String taskId;
        public String keyword;
        public int depth;
        public String status;       // PROCESSING | COMPLETED | FAILED
        public String progress;     // Human-readable progress message
        public GraphResponse result;
        public LocalDateTime createdAt;
        public LocalDateTime completedAt;
    }
}
