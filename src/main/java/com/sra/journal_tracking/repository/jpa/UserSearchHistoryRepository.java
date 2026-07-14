package com.sra.journal_tracking.repository.jpa;

import com.sra.journal_tracking.entity.jpa.UserSearchHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    /**
     * Count how many searches a user performed within a date range (this month).
     */
    @Query("SELECT COUNT(h) FROM UserSearchHistory h "
         + "WHERE h.user.userId = :userId "
         + "AND h.searchedAt >= :start AND h.searchedAt < :end")
    long countByUserAndSearchedBetween(@Param("userId") UUID userId,
                                        @Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);

    /**
     * Count keyword searches that match actual keywords in the KEYWORD table.
     * Filters out noise terms like "improving", "preferred" that aren't real topics.
     */
    @Query(value = """
            SELECT TOP 8 k.KeywordText, COUNT(h.SearchHistoryID) AS cnt
            FROM USER_SEARCH_HISTORY h
            JOIN KEYWORD k ON LOWER(h.SearchText) = LOWER(k.KeywordText)
            WHERE h.UserID = :userId AND h.SearchType = 'KEYWORD'
            GROUP BY k.KeywordText
            ORDER BY cnt DESC
            """, nativeQuery = true)
    List<Object[]> countKeywordSearchesMatchingDb(@Param("userId") UUID userId);

    /**
     * Count keyword searches directly from search history (no JOIN to KEYWORD table).
     * Falls back to this when the KEYWORD table is empty (new DB, no sync yet).
     */
    @Query(value = """
            SELECT TOP 8 h.SearchText, COUNT(h.SearchHistoryID) AS cnt
            FROM USER_SEARCH_HISTORY h
            WHERE h.UserID = :userId AND h.SearchType = 'KEYWORD'
            GROUP BY h.SearchText
            ORDER BY cnt DESC
            """, nativeQuery = true)
    List<Object[]> countKeywordSearchesRaw(@Param("userId") UUID userId);
}
