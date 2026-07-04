package com.sra.journal_tracking.dto.bookmark;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for bulk-bookmarking multiple papers at once.
 * All papers are optionally added to the same collection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkBookmarkRequest {

    @NotEmpty(message = "At least one paper ID is required")
    private List<UUID> paperIds;

    /** Optional — if provided, all bookmarks are added to this collection. */
    private UUID collectionId;
}
