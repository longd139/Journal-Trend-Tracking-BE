package com.sra.journal_tracking.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
     * Find the most recent paper bookmarks for a user (paper bookmarks only, no keyword bookmarks).
     * Fetches paper and journal eagerly to avoid N+1 queries.
     */
    @Query("SELECT b FROM Bookmark b "
         + "JOIN FETCH b.paper p "
         + "LEFT JOIN FETCH p.journal "
         + "WHERE b.user.userId = :userId AND b.paper IS NOT NULL "
         + "ORDER BY b.createdAt DESC")
    List<Bookmark> findRecentPaperBookmarks(@Param("userId") UUID userId, Pageable pageable);
}
