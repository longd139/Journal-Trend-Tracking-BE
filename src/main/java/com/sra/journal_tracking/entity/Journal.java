package com.sra.journal_tracking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

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