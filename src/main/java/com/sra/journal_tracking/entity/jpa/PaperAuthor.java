package com.sra.journal_tracking.entity.jpa;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "PAPER_AUTHOR")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"paper", "author"})
@ToString(exclude = {"paper", "author"})
public class PaperAuthor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "PaperAuthorID", updatable = false, nullable = false)
    private UUID paperAuthorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PaperID", nullable = false)
    private ResearchPaper paper;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "AuthorID", nullable = false)
    private Author author;

    @Column(name = "AuthorOrder")
    private Integer authorOrder;
}
