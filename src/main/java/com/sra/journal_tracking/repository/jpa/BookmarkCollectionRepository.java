package com.sra.journal_tracking.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sra.journal_tracking.entity.jpa.BookmarkCollection;

@Repository
public interface BookmarkCollectionRepository extends JpaRepository<BookmarkCollection, UUID> {

    Page<BookmarkCollection> findByUser_UserId(UUID userId, Pageable pageable);

    List<BookmarkCollection> findByUser_UserId(UUID userId);

    Optional<BookmarkCollection> findByUser_UserIdAndNameIgnoreCase(UUID userId, String name);

    long countByUser_UserId(UUID userId);
}
