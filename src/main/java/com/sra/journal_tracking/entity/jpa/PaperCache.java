package com.sra.journal_tracking.entity.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lightweight cache for papers fetched from OpenAlex API.
 * No foreign keys — just stores paper data for fast retrieval.
 * Used to survive DB switches and restarts without losing paper visibility.
 */
@Entity
@Table(name = "PAPER_CACHE")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaperCache {

    @Id
    @Column(name = "PaperID", nullable = false, updatable = false)
    private UUID paperId;

    @Column(name = "Title", length = 1000)
    private String title;

    @Column(name = "Abstract", columnDefinition = "NVARCHAR(MAX)")
    private String abstractText;

    @Column(name = "Doi", length = 200)
    private String doi;

    @Column(name = "PubYear")
    private Short pubYear;

    @Column(name = "CitationCount")
    private Integer citationCount;

    @Column(name = "JournalName", length = 500)
    private String journalName;

    @Column(name = "SourceUrl", length = 500)
    private String sourceUrl;

    /** OpenAlex work URL (e.g. https://openalex.org/W123456) — key for refresh */
    @Column(name = "OpenAlexWorkId", length = 500)
    private String openAlexWorkId;

    /** Full JSON from OpenAlex for fields we don't store explicitly */
    @Column(name = "DataJson", columnDefinition = "NVARCHAR(MAX)")
    private String dataJson;

    @Column(name = "UpdatedAt", nullable = false)
    private LocalDateTime updatedAt;
}
