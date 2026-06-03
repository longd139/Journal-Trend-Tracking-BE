package com.sra.journal_tracking.entity.jpa;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

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