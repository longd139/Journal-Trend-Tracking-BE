package com.sra.journal_tracking.service;

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
}
