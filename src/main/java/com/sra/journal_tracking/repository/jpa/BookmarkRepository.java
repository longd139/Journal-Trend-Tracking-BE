package com.sra.journal_tracking.repository.jpa;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
