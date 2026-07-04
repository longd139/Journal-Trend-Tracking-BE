package com.sra.journal_tracking.dto.common;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Reusable DTO for bulk delete operations.
 * Accepts a list of entity IDs to delete in a single request.
 * Used by bookmarks, notifications, and follows.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkDeleteRequest {

    @NotEmpty(message = "At least one ID is required")
    private List<UUID> ids;
}
