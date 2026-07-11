package com.sra.journal_tracking.entity.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "PAPER_RATING", uniqueConstraints = {
    @UniqueConstraint(name = "UK_PAPER_RATING_UserPaper", columnNames = {"UserID", "PaperID"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"user", "paper"})
@ToString(exclude = {"user", "paper"})
public class PaperRating {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "RatingID", updatable = false, nullable = false)
    private UUID ratingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserID", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PaperID", nullable = false)
    private ResearchPaper paper;

    @Column(name = "Score", nullable = false)
    private Integer score;

    @Column(name = "RatedAt", nullable = false, updatable = false)
    private LocalDateTime ratedAt;

    @PrePersist
    protected void onCreate() {
        if (ratedAt == null) {
            ratedAt = LocalDateTime.now();
        }
    }
}
