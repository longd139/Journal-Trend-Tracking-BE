package com.sra.journal_tracking.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageSummaryResponse {
    private String usageMonth;
    private Integer searchCount;
    private Integer viewCount;
    private Integer chartViewCount;
}
