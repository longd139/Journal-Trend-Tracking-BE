package com.sra.journal_tracking.dto.bookmark;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookmarkResponse {

    private UUID bookmarkId;
    private UUID paperId;
    private String paperTitle;
    private UUID keywordId;
    private String keywordText;
    private String notes;
    private LocalDateTime createdAt;
}
