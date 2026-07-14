package com.sra.journal_tracking.repository.jpa;

import com.sra.journal_tracking.entity.jpa.ReadingHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReadingHistoryRepository extends JpaRepository<ReadingHistory, UUID> {

    /**
     * Get the most recent N viewed papers for a user, newest first.
     * Deduplication: same paper viewed multiple times → keeps the most recent.
     */
    @Query("""
            SELECT rh FROM ReadingHistory rh
            JOIN FETCH rh.paper p
            LEFT JOIN FETCH p.journal
            WHERE rh.user.userId = :userId
            ORDER BY rh.viewedAt DESC
            """)
    List<ReadingHistory> findByUser_UserIdOrderByViewedAtDesc(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Count how many distinct papers this user has viewed.
     */
    @Query("SELECT COUNT(DISTINCT rh.paper.paperId) FROM ReadingHistory rh WHERE rh.user.userId = :userId")
    long countDistinctPapersByUser(@Param("userId") UUID userId);

    /**
     * Delete old reading history entries beyond the keep limit for a user.
     * Returns number of deleted rows.
     */
    @Modifying
    @Query(value = """
            DELETE FROM USER_READING_HISTORY
            WHERE UserID = :userId
            AND ReadingHistoryID NOT IN (
                SELECT TOP (:keep) ReadingHistoryID
                FROM USER_READING_HISTORY
                WHERE UserID = :userId
                ORDER BY ViewedAt DESC
            )
            """, nativeQuery = true)
    int deleteOldEntries(@Param("userId") UUID userId, @Param("keep") int keep);

    /**
     * Count how many paper views a user had within a date range (this month).
     */
    @Query("SELECT COUNT(rh) FROM ReadingHistory rh "
         + "WHERE rh.user.userId = :userId "
         + "AND rh.viewedAt >= :start AND rh.viewedAt < :end")
    long countByUserAndViewedBetween(@Param("userId") UUID userId,
                                     @Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    /** Count total views for a specific paper. */
    @Query("SELECT COUNT(rh) FROM ReadingHistory rh WHERE rh.paper.paperId = :paperId")
    long countByPaper_PaperId(@Param("paperId") UUID paperId);

    /** Check if a user has already viewed a specific paper. */
    boolean existsByUser_UserIdAndPaper_PaperId(UUID userId, UUID paperId);

    /** Count keyword occurrences in papers the user has viewed (top research interests). */
    @Query(value = """
            SELECT TOP 8 k.KeywordText, COUNT(DISTINCT rh.PaperID) AS cnt
            FROM USER_READING_HISTORY rh
            JOIN PAPER_KEYWORD pk ON rh.PaperID = pk.PaperID
            JOIN KEYWORD k ON pk.KeywordID = k.KeywordID
            WHERE rh.UserID = :userId
            GROUP BY k.KeywordText
            ORDER BY cnt DESC
            """, nativeQuery = true)
    List<Object[]> countKeywordsInViewedPapers(@Param("userId") UUID userId);

    /** Count views per research field for a user (top fields they read). */
    @Query("""
            SELECT rf.fieldName, COUNT(rh) FROM ReadingHistory rh
            JOIN rh.paper p
            JOIN p.field rf
            WHERE rh.user.userId = :userId AND rf.fieldName IS NOT NULL
            GROUP BY rf.fieldName
            ORDER BY COUNT(rh) DESC
            """)
    List<Object[]> countViewsByFieldForUser(@Param("userId") UUID userId, Pageable pageable);
}
