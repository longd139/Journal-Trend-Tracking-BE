package com.sra.journal_tracking.dto.paper;

// import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KeywordDTO {
    
    /**
     * Keyword text (e.g., "Machine Learning", "Deep Learning")
     */
    private String keywordText;
    
    /**
     * Relevance score (0.0000 to 1.0000)
     * Higher = more relevant to this paper
     */
    private Double relevanceScore; // Keeping Double because PaperKeyword uses Double
}