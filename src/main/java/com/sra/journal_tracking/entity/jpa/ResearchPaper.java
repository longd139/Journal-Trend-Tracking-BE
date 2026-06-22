package com.sra.journal_tracking.entity.jpa;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

// import org.springframework.data.neo4j.core.schema.Id;
import jakarta.persistence.Id;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType; // ĐÚNG - ĐÂY LÀ ID CỦA NEO4J
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "RESEARCH_PAPER")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"source", "journal", "field"})
@ToString(exclude = {"source", "journal", "field"})
public class ResearchPaper {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "PaperID", updatable = false, nullable = false)
    private UUID paperId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SourceID", nullable = false)
    private ApiSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "JournalID")
    private Journal journal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "FieldID")
    private ResearchField field;

    @Column(name = "Title", nullable = false, length = 1000)
    private String title;

    @Column(name = "Abstract", columnDefinition = "NVARCHAR(MAX)")
    private String abstractText;

    @Column(name = "DOI", length = 200, unique = true)
    private String doi;

    @Column(name = "PubDate")
    private LocalDate pubDate;

    @Column(name = "PubYear")
    private Short pubYear;

    @Column(name = "CitationCount", nullable = false)
    @Builder.Default
    private Integer citationCount = 0;

    @Column(name = "IsOpenAccess", nullable = false)
    @Builder.Default
    private Boolean isOpenAccess = false;

    @Column(name = "PdfUrl", length = 500)
    private String pdfUrl;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "paper", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private java.util.List<PaperAuthor> authors;

    @OneToMany(mappedBy = "paper", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private java.util.List<PaperKeyword> keywords;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}