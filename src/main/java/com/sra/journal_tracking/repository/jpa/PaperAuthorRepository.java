package com.sra.journal_tracking.repository.jpa;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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

    /** Count papers by author ID. */
    long countByAuthor_AuthorId(UUID authorId);

    /** Get an author's top research field (most papers in that field), ordered by paper count. */
    @Query("SELECT pa.paper.field.fieldId FROM PaperAuthor pa " +
           "WHERE pa.author.authorId = :authorId AND pa.paper.field IS NOT NULL " +
           "GROUP BY pa.paper.field.fieldId, pa.paper.field.fieldName " +
           "ORDER BY COUNT(pa) DESC")
    List<UUID> findTopFieldIdsByAuthorId(@Param("authorId") UUID authorId);
}
