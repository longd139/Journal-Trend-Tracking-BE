package com.sra.journal_tracking.repository.jpa;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
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

    /**
     * Find co-authors for a given author — other authors who appear on the same papers.
     * Returns [fullName, affiliation, collaborationCount] ordered by collaboration count DESC.
     */
    @Query("SELECT a.fullName, a.affiliation, COUNT(DISTINCT pa2.paper) "
         + "FROM PaperAuthor pa1 "
         + "JOIN PaperAuthor pa2 ON pa1.paper = pa2.paper "
         + "JOIN pa2.author a "
         + "WHERE pa1.author.authorId = :authorId AND pa2.author.authorId <> :authorId "
         + "GROUP BY a.authorId, a.fullName, a.affiliation "
         + "ORDER BY COUNT(DISTINCT pa2.paper) DESC")
    List<Object[]> findCoAuthorsByAuthorId(@Param("authorId") UUID authorId, Pageable pageable);
}
