package com.sra.journal_tracking.entity.jpa;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "PAPER_KEYWORD")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"paper", "keyword"})
@ToString(exclude = {"paper", "keyword"})
public class PaperKeyword {

    @EmbeddedId
    private PaperKeywordId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("paperId")
    @JoinColumn(name = "PaperID", nullable = false)
    private ResearchPaper paper;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("keywordId")
    @JoinColumn(name = "KeywordID", nullable = false)
    private Keyword keyword;

    @Column(name = "RelevanceScore")
    private Double relevanceScore;
}
