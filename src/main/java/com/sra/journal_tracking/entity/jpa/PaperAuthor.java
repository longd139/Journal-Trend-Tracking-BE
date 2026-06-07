package com.sra.journal_tracking.entity.jpa;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "PAPER_AUTHOR")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"paper", "author"})
@ToString(exclude = {"paper", "author"})
public class PaperAuthor {

    @EmbeddedId
    private PaperAuthorId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("paperId")
    @JoinColumn(name = "PaperID", nullable = false)
    private ResearchPaper paper;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("authorId")
    @JoinColumn(name = "AuthorID", nullable = false)
    private Author author;

    @Column(name = "AuthorOrder")
    @Builder.Default
    private Integer authorOrder = 1;

    @Column(name = "IsCorresponding")
    @Builder.Default
    private Boolean isCorresponding = false;
}
