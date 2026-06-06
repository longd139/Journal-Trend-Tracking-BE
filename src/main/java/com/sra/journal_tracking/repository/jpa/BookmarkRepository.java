package com.sra.journal_tracking.repository.jpa;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sra.journal_tracking.entity.jpa.Bookmark;
import com.sra.journal_tracking.entity.jpa.User;

public interface BookmarkRepository
        extends JpaRepository<Bookmark, UUID> {

    List<Bookmark> findByUser(User user);

    long countByUser(User user);
}