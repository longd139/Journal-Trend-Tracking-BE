package com.sra.journal_tracking.dto.journal;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JournalCategoryResponse {
    private String fieldId;
    private String fieldName;
    private String description;
    private long journalCount;
    private List<TopJournalDTO> topJournals;
}
