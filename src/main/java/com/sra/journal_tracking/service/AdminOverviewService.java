package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.admin.AdminChartResponse;
import com.sra.journal_tracking.dto.admin.AdminOverviewResponse;

/**
 * Service for the admin overview dashboard — reads live system metrics
 * from Micrometer MeterRegistry and combines them with database queries.
 */
public interface AdminOverviewService {

    /**
     * Gather all 5 admin overview cards + auxiliary banner stats.
     *
     * @return fully populated AdminOverviewResponse
     */
    AdminOverviewResponse getOverview();

    // ── Chart endpoints ──

    /** Minute-by-minute request/error counts for the last 24 hours. */
    AdminChartResponse.RequestVolumeResponse getRequestVolume();

    /** Current JVM resource usage: CPU %, heap memory, disk. */
    AdminChartResponse.ResourceUsageResponse getResourceUsage();

    /** Hourly unique visitor counts, today vs yesterday. */
    AdminChartResponse.VisitorTrafficResponse getVisitorTraffic();

    /** Latest system events from audit log and sync log. */
    AdminChartResponse.RecentEventsResponse getRecentEvents();
}
