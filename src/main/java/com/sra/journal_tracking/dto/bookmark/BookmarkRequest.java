package com.sra.journal_tracking.dto.bookmark;

import java.util.UUID;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
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

    /**
     * Optional — if null, the bookmark is "uncategorized" (not in any collection).
     */
    private UUID collectionId;

    private String notes;

    @AssertTrue(message = "Exactly one of paperId or keywordId must be provided")
    public boolean isExactlyOneTarget() {
        return (paperId != null && keywordId == null) || (paperId == null && keywordId != null);
    }
}
