package com.sra.journal_tracking.entity.jpa;

import java.math.BigDecimal;
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
@Table(name = "JOURNAL")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"source", "field"})
@ToString(exclude = {"source", "field"})
public class Journal {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "JournalID", updatable = false, nullable = false)
    private UUID journalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SourceID", nullable = false)
    private ApiSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "FieldID")
    private ResearchField field;

    @Column(name = "JournalName", nullable = false, length = 500)
    private String journalName;

    @Column(name = "ISSN", length = 20, unique = true)
    private String issn;

    @Column(name = "Publisher", length = 300)
    private String publisher;

    @Column(name = "ImpactFactor", precision = 8, scale = 3)
    private BigDecimal impactFactor;

    @Column(name = "Quartile", length = 2)
    private String quartile;

    @Column(name = "IsActive", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}