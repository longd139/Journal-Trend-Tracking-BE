package com.sra.journal_tracking.entity.jpa;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "USER_SEARCH_HISTORY")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSearchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "SearchHistoryID", updatable = false, nullable = false)
    private UUID searchHistoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserID", nullable = false)
    private User user;

    @Column(name = "SearchText", nullable = false, length = 500)
    private String searchText;

    /**
     * Type of search: KEYWORD, AUTHOR, or JOURNAL.
     */
    @Column(name = "SearchType", nullable = false, length = 20)
    private String searchType;

    @Column(name = "SearchedAt", nullable = false)
    private LocalDateTime searchedAt;

    @PrePersist
    protected void onCreate() {
        if (searchedAt == null) {
            searchedAt = LocalDateTime.now();
        }
    }
}
