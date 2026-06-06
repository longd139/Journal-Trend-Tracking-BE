package com.sra.journal_tracking.dto.paper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageLimitResponseDTO {
    
    /**
     * Remaining searches for current month
     * Example: 10 (out of 30 total allowed)
     * For RESEARCHER: null (no limit)
     */
    private Integer remainingSearches;
    
    /**
     * Remaining views for current month
     * Example: 5 (out of 20 total allowed)
     * For RESEARCHER: null (no limit)
     */
    private Integer remainingViews;
    
    /**
     * Total monthly limit (from SYSTEM_CONFIG)
     * Example: 30 searches, 20 views
     * For RESEARCHER: null (no limit)
     */
    private Integer monthlyLimit;
    
    /**
     * When does the limit reset?
     * Format: 'YYYY-MM-DD'
     * Example: "2026-06-01" (first day of next month)
     */
    private String resetDate;
    
    /**
     * Current month in 'YYYY-MM' format
     * Example: "2026-05"
     */
    private String currentMonth;
    
    /**
     * User's role name
     * "ACADEMIC_USER" or "RESEARCHER"
     */
    private String userRole;
}