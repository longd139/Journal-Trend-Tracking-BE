package com.sra.journal_tracking.dto.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentSearchResponse {

    /** The search text as entered by the user (keyword, author name, or journal name) */
    private String searchText;

    /** Type of search: KEYWORD, AUTHOR, or JOURNAL */
    private String searchType;

    /** When this search was performed */
    private LocalDateTime searchedAt;
}
