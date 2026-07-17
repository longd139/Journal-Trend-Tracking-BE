package com.sra.journal_tracking.dto.idea;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriterionResult {
    private String criterionName;
    private Boolean value;
    private String evidenceQuote;
}
