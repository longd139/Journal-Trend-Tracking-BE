package com.sra.journal_tracking.entity.jpa;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "BOOKMARK")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "BookmarkID")
    private UUID bookmarkId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserID", nullable = false)
    private User user;

    @Column(name = "Type", nullable = false)
    private String type;

    @Column(name = "TargetID", nullable = false)
    private String targetId;

    @Column(name = "Note")
    private String note;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}