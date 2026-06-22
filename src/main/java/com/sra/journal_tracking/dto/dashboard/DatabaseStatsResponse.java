package com.sra.journal_tracking.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseStatsResponse {

    private PaperStats papers;
    private AuthorStats authors;
    private KeywordStats keywords;
    private CountStat journals;
    private CountStat researchFields;
    private TopicStats researchTopics;
    private Neo4jStats neo4j;
    private SyncStats syncLogs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaperStats {
        private Long total;
        private Map<String, Long> bySource;
        private Map<Integer, Long> byYear;
        private Long openAccess;
        private Long hasPdfUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthorStats {
        private Long total;
        private Long orphaned;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeywordStats {
        private Long total;
        private Long orphaned;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CountStat {
        private Long total;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopicStats {
        private Long total;
        private Long trending;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Neo4jStats {
        private Long paperNodes;
        private Long keywordNodes;
        private Long relationships;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncStats {
        private Long total;
        private String lastSync;
    }
}
