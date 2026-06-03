package com.sra.journal_tracking.entity.jpa;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "PAPER_KEYWORD")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"paper", "keyword"})
@ToString(exclude = {"paper", "keyword"})
public class PaperKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "PaperKeywordID", updatable = false, nullable = false)
    private UUID paperKeywordId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PaperID", nullable = false)
    private ResearchPaper paper;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "KeywordID", nullable = false)
    private Keyword keyword;

    @Column(name = "RelevanceScore")
    private Double relevanceScore;
}
