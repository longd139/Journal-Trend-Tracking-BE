package com.sra.journal_tracking.dto.keyword;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotKeywordResponse {

    /** The keyword text as originally entered */
    private String keywordText;

    /** Number of times this keyword has been searched */
    private Integer searchCount;
}
