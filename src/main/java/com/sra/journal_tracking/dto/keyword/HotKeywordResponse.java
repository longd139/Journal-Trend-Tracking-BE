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

    /** The keyword text */
    private String keywordText;

    /** Number of times this keyword has been searched (only populated for Internal source) */
    @Builder.Default
    private Integer searchCount = 0;

    /** Source of the keyword: "AI" for Groq/Gemini generated, "Internal" for DB fallback */
    @Builder.Default
    private String source = "AI";
}
