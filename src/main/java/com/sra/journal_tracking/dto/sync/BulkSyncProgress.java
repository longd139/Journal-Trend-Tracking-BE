package com.sra.journal_tracking.dto.sync;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BulkSyncProgress {

    private String taskId;

    @Builder.Default
    private String status = "RUNNING";

    private int totalKeywords;
    private int completedKeywords;
    private int totalFetched;
    private int totalInserted;
    private String currentKeyword;
    private int percent;
    private String errorMessage;

    @Builder.Default
    private LocalDateTime startedAt = LocalDateTime.now();

    private LocalDateTime completedAt;

    @Builder.Default
    private Map<String, Map<String, Integer>> keywordStats = new LinkedHashMap<>();

    /** Per-keyword error messages — only populated for keywords that failed */
    @Builder.Default
    private Map<String, String> keywordErrors = new LinkedHashMap<>();

    /** Final result — only populated when status is COMPLETED */
    private Map<String, Object> result;
}
