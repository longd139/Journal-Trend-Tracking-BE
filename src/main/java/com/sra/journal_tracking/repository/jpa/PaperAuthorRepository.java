package com.sra.journal_tracking.repository.jpa;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sra.journal_tracking.entity.jpa.PaperAuthor;
import com.sra.journal_tracking.entity.jpa.PaperAuthorId;

@Repository
public interface PaperAuthorRepository extends JpaRepository<PaperAuthor, PaperAuthorId> {

    @Query("SELECT COUNT(DISTINCT pa.author) FROM PaperAuthor pa WHERE pa.paper.createdAt >= :start AND pa.paper.createdAt < :end")
    long countDistinctAuthorsByPaperCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
