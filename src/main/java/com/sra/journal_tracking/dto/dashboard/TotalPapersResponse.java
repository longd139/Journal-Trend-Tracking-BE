package com.sra.journal_tracking.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TotalPapersResponse {

    /**
     * Total number of research papers currently stored in the system.
     */
    private Long totalPapers;
}
