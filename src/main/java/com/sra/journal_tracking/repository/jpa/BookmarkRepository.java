package com.sra.journal_tracking.repository.jpa;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sra.journal_tracking.entity.jpa.Bookmark;

@Repository
public interface BookmarkRepository extends JpaRepository<Bookmark, UUID> {

    Page<Bookmark> findByUser_UserId(UUID userId, Pageable pageable);

    Page<Bookmark> findByUser_UserIdAndCollection_CollectionId(UUID userId, UUID collectionId, Pageable pageable);

    Optional<Bookmark> findByUser_UserIdAndPaper_PaperId(UUID userId, UUID paperId);

    Optional<Bookmark> findByUser_UserIdAndKeyword_KeywordId(UUID userId, UUID keywordId);

    long countByUser_UserId(UUID userId);

    long countByCollection_CollectionId(UUID collectionId);

    void deleteByUser_UserIdAndPaper_PaperId(UUID userId, UUID paperId);

    void deleteByUser_UserIdAndKeyword_KeywordId(UUID userId, UUID keywordId);

    /**
     * Batch delete bookmarks by their IDs, scoped to a specific user (ownership check).
     * Returns the number of deleted rows.
     */
    @Modifying
    @Query("DELETE FROM Bookmark b WHERE b.bookmarkId IN :ids AND b.user.userId = :userId")
    int deleteByBookmarkIdInAndUser_UserId(@Param("ids") List<UUID> ids, @Param("userId") UUID userId);

    /** Count bookmarks created by a user within a date range. */
    @Query("SELECT COUNT(b) FROM Bookmark b WHERE b.user.userId = :userId AND b.createdAt >= :start AND b.createdAt < :end")
    long countByUserAndCreatedBetween(@Param("userId") UUID userId,
                                      @Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);
}
