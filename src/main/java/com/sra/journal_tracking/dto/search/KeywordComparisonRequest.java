package com.sra.journal_tracking.dto.search;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for comparing multiple keywords side-by-side.
 * FE sends a list of keywords; BE returns aggregated stats for each.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeywordComparisonRequest {

    @NotEmpty(message = "Keywords list cannot be empty")
    @Size(min = 1, max = 10, message = "Must compare between 1 and 10 keywords")
    private List<String> keywords;
}
