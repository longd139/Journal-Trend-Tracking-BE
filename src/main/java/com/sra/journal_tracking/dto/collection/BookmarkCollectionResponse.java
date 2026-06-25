package com.sra.journal_tracking.dto.collection;

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
public class BookmarkCollectionResponse {

    private UUID collectionId;
    private String name;
    private String description;
    private long bookmarkCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
