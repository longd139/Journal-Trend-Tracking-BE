package com.sra.journal_tracking.repository.jpa;

import com.sra.journal_tracking.entity.jpa.UserSearchHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserSearchHistoryRepository extends JpaRepository<UserSearchHistory, UUID> {

    /**
     * Find the most recent searches for a user, ordered by searchedAt descending.
     * Deduplication is handled in the service layer (same searchText + searchType → keep newest).
     */
    List<UserSearchHistory> findByUser_UserIdOrderBySearchedAtDesc(UUID userId, Pageable pageable);

    /**
     * Find distinct user IDs who searched for any of the given search texts.
     * Used by collaborative filtering to find users with similar interests.
     */
    @Query("SELECT DISTINCT h.user.userId FROM UserSearchHistory h "
         + "WHERE LOWER(h.searchText) IN :normalizedTerms "
         + "AND h.user.userId <> :excludeUserId")
    List<UUID> findUsersBySearchTerms(
            @Param("normalizedTerms") List<String> normalizedTerms,
            @Param("excludeUserId") UUID excludeUserId);
}
