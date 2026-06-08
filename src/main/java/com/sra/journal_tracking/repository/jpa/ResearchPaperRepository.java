package com.sra.journal_tracking.repository.jpa;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sra.journal_tracking.entity.jpa.ResearchPaper;

@Repository
public interface ResearchPaperRepository extends JpaRepository<ResearchPaper, UUID> {

    @Query("SELECT DISTINCT p FROM ResearchPaper p " +
           "LEFT JOIN p.keywords k " +
           "LEFT JOIN k.keyword kw " +
           "WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(p.abstractText) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(kw.keywordText) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<ResearchPaper> searchByTitleOrAbstractOrKeywords(@Param("query") String query, Pageable pageable);

    @Query("SELECT DISTINCT p FROM ResearchPaper p " +
           "LEFT JOIN p.keywords k " +
           "LEFT JOIN k.keyword kw " +
           "LEFT JOIN p.authors pa " +
           "LEFT JOIN pa.author a " +
           "LEFT JOIN p.journal j " +
           "WHERE (LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(p.abstractText) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(p.doi) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(kw.keywordText) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(a.fullName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(j.journalName) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "  AND (:authorName IS NULL OR :authorName = '' OR LOWER(a.fullName) LIKE LOWER(CONCAT('%', :authorName, '%'))) " +
           "  AND (:journalId IS NULL OR p.journal.journalId = :journalId)")
    Page<ResearchPaper> searchPapersWithFilters(
            @Param("query") String query,
            @Param("authorName") String authorName,
            @Param("journalId") UUID journalId,
            Pageable pageable
    );

    @Query("SELECT DISTINCT p FROM ResearchPaper p " +
           "JOIN p.authors pa " +
           "WHERE LOWER(pa.author.fullName) LIKE LOWER(CONCAT('%', :authorName, '%'))")
    Page<ResearchPaper> searchByAuthorName(@Param("authorName") String authorName, Pageable pageable);

    Page<ResearchPaper> findByJournal_JournalId(UUID journalId, Pageable pageable);

    Page<ResearchPaper> findByField_FieldId(UUID fieldId, Pageable pageable);

    @Query("SELECT p FROM ResearchPaper p " +
           "WHERE (:pubYearFrom IS NULL OR p.pubYear >= :pubYearFrom) " +
           "  AND (:pubYearTo IS NULL OR p.pubYear <= :pubYearTo) " +
           "  AND (:fieldId IS NULL OR p.field.fieldId = :fieldId) " +
           "  AND (:journalId IS NULL OR p.journal.journalId = :journalId) " +
           "  AND (:isOpenAccess IS NULL OR p.isOpenAccess = :isOpenAccess) " +
           "  AND (:minCitations IS NULL OR p.citationCount >= :minCitations)")
    Page<ResearchPaper> advancedFilter(
            @Param("pubYearFrom") Short pubYearFrom,
            @Param("pubYearTo") Short pubYearTo,
            @Param("fieldId") UUID fieldId,
            @Param("journalId") UUID journalId,
            @Param("isOpenAccess") Boolean isOpenAccess,
            @Param("minCitations") Integer minCitations,
            Pageable pageable
    );

    @Query("SELECT p FROM ResearchPaper p " +
           "LEFT JOIN FETCH p.journal " +
           "LEFT JOIN FETCH p.field " +
           "WHERE p.paperId = :paperId")
    Optional<ResearchPaper> findByIdWithDetails(@Param("paperId") UUID paperId);

    Optional<ResearchPaper> findByDoi(String doi);

    /**
     * Sum all citation counts across all research papers.
     * Returns 0 if no papers exist (COALESCE).
     */
    @Query("SELECT COALESCE(SUM(p.citationCount), 0) FROM ResearchPaper p")
    Long sumTotalCitations();
}
