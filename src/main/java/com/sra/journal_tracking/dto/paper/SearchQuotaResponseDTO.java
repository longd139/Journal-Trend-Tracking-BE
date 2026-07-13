package com.sra.journal_tracking.dto.paper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchQuotaResponseDTO {

    /** Whether quota was consumed for this keyword. */
    private boolean quotaConsumed;

    /** Whether the keyword was found in the 6-hour cache. */
    private boolean fromCache;

    /** The keyword that was checked. */
    private String keyword;
}
