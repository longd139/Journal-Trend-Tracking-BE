package com.sra.journal_tracking.dto.history;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A single entry in the user's reading history — lightweight paper info.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReadingHistoryResponse {

    private UUID readingHistoryId;
    private UUID paperId;
    private String paperTitle;
    private Integer pubYear;
    private String journalName;
    private String doi;
    private Integer citationCount;
    private Boolean isOpenAccess;
    private LocalDateTime viewedAt;
}
