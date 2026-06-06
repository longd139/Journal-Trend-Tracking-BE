package com.sra.journal_tracking.dto.response;

public class DashboardResponse {

    private long totalPapers;
    private long totalCitations;
    private int totalTrendingTopics;
    private String topKeyword;

    public DashboardResponse(
            long totalPapers,
            long totalCitations,
            int totalTrendingTopics,
            String topKeyword) {

        this.totalPapers = totalPapers;
        this.totalCitations = totalCitations;
        this.totalTrendingTopics = totalTrendingTopics;
        this.topKeyword = topKeyword;
    }

    public long getTotalPapers() {
        return totalPapers;
    }

    public long getTotalCitations() {
        return totalCitations;
    }

    public int getTotalTrendingTopics() {
        return totalTrendingTopics;
    }

    public String getTopKeyword() {
        return topKeyword;
    }
}