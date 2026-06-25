package com.sra.journal_tracking.dto.collection;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookmarkCollectionRequest {

    @NotBlank(message = "Collection name is required")
    @Size(max = 200, message = "Collection name must not exceed 200 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
}
