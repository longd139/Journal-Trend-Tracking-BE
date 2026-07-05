package com.sra.journal_tracking.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for the admin overview dashboard (5 stat cards).
 * All values are derived from real system metrics via Micrometer MeterRegistry.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminOverviewResponse {

    /** Card 1: Number of currently active (enabled) user accounts. */
    private Long activeUsers;

    /** Card 2: Total HTTP requests processed since application startup. */
    private Long totalRequests;

    /** Card 3: Average request latency in milliseconds. */
    private Double avgLatencyMs;

    /** Card 4: Error rate as percentage (4xx + 5xx / total * 100). */
    private Double errorRate;

    /** Card 5: SQL Server database size in megabytes. */
    private Long dbSizeMb;

    // ── Auxiliary (for the banner pills at the top of the page) ──

    /** Current system uptime in human-readable format (e.g. "3 days 5 hours"). */
    private String uptime;

    /** Total storage used (DB + estimated indexes) in megabytes. */
    private Long totalStorageMb;

    /** Number of errors (4xx + 5xx) since startup. */
    private Long errorCount;

    /** Number of requests in the last hour (approximation). */
    private Long requestsLastHour;
}
