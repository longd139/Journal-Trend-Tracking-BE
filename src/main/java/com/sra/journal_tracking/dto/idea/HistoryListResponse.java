package com.sra.journal_tracking.dto.idea;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryListResponse {
    private List<HistoryItem> items;
    private long totalItems;
    private int totalPages;
    private int currentPage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryItem {
        private String analysisId;
        private String ideaText;
        private List<String> keywords;
        private Integer paperCount;
        private Integer noveltyScore;
        private String createdAt;
    }
}
