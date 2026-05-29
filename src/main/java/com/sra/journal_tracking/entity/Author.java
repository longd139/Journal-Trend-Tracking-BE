package com.sra.journal_tracking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "AUTHOR", uniqueConstraints = {
    @UniqueConstraint(name = "UK_AUTHOR_External", columnNames = {"SourceID", "ExternalAuthorID"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"source"})
@ToString(exclude = {"source"})
public class Author {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "AuthorID", updatable = false, nullable = false)
    private UUID authorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SourceID", nullable = false)
    private ApiSource source;

    @Column(name = "ExternalAuthorID", length = 200)
    private String externalAuthorId;

    @Column(name = "FullName", nullable = false, length = 300)
    private String fullName;

    @Column(name = "Affiliation", length = 500)
    private String affiliation;

    @Column(name = "HIndex")
    @Builder.Default
    private Integer hIndex = 0;

    @Column(name = "TotalCitations")
    @Builder.Default
    private Integer totalCitations = 0;
}