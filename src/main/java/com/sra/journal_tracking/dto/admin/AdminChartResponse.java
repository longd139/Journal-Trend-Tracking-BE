package com.sra.journal_tracking.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTOs for admin overview chart endpoints.
 */
public class AdminChartResponse {

    // ── Request Volume ──

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RequestVolumeResponse {
        private List<RequestVolumePoint> points;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestVolumePoint {
        private String time;      // "HH:mm"
        private int requests;
        private int errors;
    }

    // ── Resource Usage ──

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResourceUsageResponse {
        private double cpuPercent;
        private long heapUsedMb;
        private long heapMaxMb;
        private long diskUsedGb;
        private long diskTotalGb;
    }

    // ── Visitor Traffic ──

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VisitorTrafficResponse {
        private List<VisitorTrafficPoint> points;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VisitorTrafficPoint {
        private String hour;          // "HH:00"
        private int todayVisitors;
        private int yesterdayVisitors;
    }

    // ── Recent Events ──

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RecentEventsResponse {
        private List<RecentEventEntry> events;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentEventEntry {
        private String type;          // "audit", "sync", "system"
        private String title;
        private String description;
        private String timestamp;     // ISO-8601 string
    }
}
