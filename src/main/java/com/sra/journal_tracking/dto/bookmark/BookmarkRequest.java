package com.sra.journal_tracking.dto.bookmark;

import java.util.UUID;

import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookmarkRequest {

    private UUID paperId;
    private UUID keywordId;
    private String notes;

    @AssertTrue(message = "Exactly one of paperId or keywordId must be provided")
    public boolean isExactlyOneTarget() {
        return (paperId != null && keywordId == null) || (paperId == null && keywordId != null);
    }
}
