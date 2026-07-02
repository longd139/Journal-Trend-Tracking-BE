package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.sync.BulkSyncProgress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory progress tracker for bulk sync operations.
 * Stores progress in a ConcurrentHashMap — progress is lost on app restart.
 */
@Slf4j
@Service
public class BulkSyncProgressTracker {

    private final ConcurrentHashMap<String, BulkSyncProgress> tasks = new ConcurrentHashMap<>();

    /**
     * Register a new bulk sync task.
     */
    public BulkSyncProgress createTask(String taskId, int totalKeywords) {
        BulkSyncProgress progress = BulkSyncProgress.builder()
                .taskId(taskId)
                .totalKeywords(totalKeywords)
                .status("RUNNING")
                .startedAt(LocalDateTime.now())
                .build();
        tasks.put(taskId, progress);
        log.info("Bulk sync task registered: {} ({} keywords)", taskId, totalKeywords);
        return progress;
    }

    /**
     * Update progress after completing one keyword.
     */
    public void updateKeywordProgress(String taskId, String keyword, int keywordFetched,
                                       int keywordInserted, int totalFetched, int totalInserted) {
        BulkSyncProgress p = tasks.get(taskId);
        if (p == null) return;

        p.setCompletedKeywords(p.getCompletedKeywords() + 1);
        p.setCurrentKeyword(keyword);
        p.setTotalFetched(totalFetched);
        p.setTotalInserted(totalInserted);

        // Calculate percentage based on completed keywords vs total
        if (p.getTotalKeywords() > 0) {
            p.setPercent((p.getCompletedKeywords() * 100) / p.getTotalKeywords());
        }

        // Store per-keyword stats
        p.getKeywordStats().put(keyword, Map.of("scanned", keywordFetched, "inserted", keywordInserted));
    }

    /**
     * Record an error for a specific keyword.
     */
    public void addKeywordError(String taskId, String keyword, String error) {
        BulkSyncProgress p = tasks.get(taskId);
        if (p == null) return;
        p.getKeywordErrors().put(keyword, error);
    }

    /**
     * Mark the task as completed with final result.
     */
    public void markCompleted(String taskId, Map<String, Object> result) {
        BulkSyncProgress p = tasks.get(taskId);
        if (p == null) return;

        p.setStatus("COMPLETED");
        p.setPercent(100);
        p.setCompletedAt(LocalDateTime.now());
        p.setResult(result);
        log.info("Bulk sync task {} completed: {} papers inserted", taskId,
                result != null ? result.getOrDefault("totalInserted", 0) : 0);
    }

    /**
     * Mark the task as failed.
     */
    public void markFailed(String taskId, String errorMessage) {
        BulkSyncProgress p = tasks.get(taskId);
        if (p == null) return;

        p.setStatus("FAILED");
        p.setErrorMessage(errorMessage);
        p.setCompletedAt(LocalDateTime.now());
        log.warn("Bulk sync task {} failed: {}", taskId, errorMessage);
    }

    /**
     * Get current progress for a task.
     * Returns null if the task ID is unknown.
     */
    public BulkSyncProgress getProgress(String taskId) {
        return tasks.get(taskId);
    }

    /**
     * Clean up completed/failed tasks older than the given minutes.
     * Can be called periodically or on-demand.
     */
    public void cleanOldTasks(int olderThanMinutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(olderThanMinutes);
        tasks.entrySet().removeIf(entry -> {
            BulkSyncProgress p = entry.getValue();
            boolean isDone = "COMPLETED".equals(p.getStatus()) || "FAILED".equals(p.getStatus());
            boolean isOld = p.getCompletedAt() != null && p.getCompletedAt().isBefore(cutoff);
            return isDone && isOld;
        });
    }

    /**
     * Get all currently RUNNING tasks (for admin monitoring).
     */
    public java.util.List<BulkSyncProgress> getRunningTasks() {
        return tasks.values().stream()
                .filter(p -> "RUNNING".equals(p.getStatus()))
                .toList();
    }

    /**
     * Get all tasks (running + recently completed/failed).
     */
    public java.util.List<BulkSyncProgress> getAllTasks() {
        return tasks.values().stream()
                .sorted((a, b) -> b.getStartedAt().compareTo(a.getStartedAt()))
                .toList();
    }
}
