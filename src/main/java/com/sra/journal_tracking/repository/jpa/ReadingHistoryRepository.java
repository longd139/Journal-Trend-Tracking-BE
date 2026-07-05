package com.sra.journal_tracking.repository.jpa;

import com.sra.journal_tracking.entity.jpa.ReadingHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
