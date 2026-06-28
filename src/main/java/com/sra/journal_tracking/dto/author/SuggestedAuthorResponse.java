package com.sra.journal_tracking.dto.author;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SuggestedAuthorResponse {
    private String authorId;
    private String fullName;
    private String affiliation;
    private Integer hIndex;
    private Integer totalCitations;
    private String topField;
    private String topFieldId;
    private Long paperCount;
}
