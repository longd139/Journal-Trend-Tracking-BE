package com.sra.journal_tracking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "KEYWORD")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"field"})
@ToString(exclude = {"field"})
public class Keyword {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "KeywordID", updatable = false, nullable = false)
    private UUID keywordId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "FieldID")
    private ResearchField field;

    @Column(name = "KeywordText", nullable = false, length = 300)
    private String keywordText;

    @Column(name = "NormalizedText", nullable = false, length = 300, unique = true)
    private String normalizedText;

    @Column(name = "PaperCount", nullable = false)
    @Builder.Default
    private Integer paperCount = 0;
}