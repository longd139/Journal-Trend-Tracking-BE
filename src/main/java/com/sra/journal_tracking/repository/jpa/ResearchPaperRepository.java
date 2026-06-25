package com.sra.journal_tracking.repository.jpa;

import java.time.LocalDateTime;
import java.util.List;
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
           "WHERE p.pubYear BETWEEN :startYear AND :endYear " +
           "  AND (LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(p.abstractText) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(kw.keywordText) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<ResearchPaper> searchByTitleOrAbstractOrKeywords(
            @Param("query") String query,
            @Param("startYear") Short startYear,
            @Param("endYear") Short endYear,
            Pageable pageable
    );

    @Query("SELECT DISTINCT p FROM ResearchPaper p " +
           "LEFT JOIN p.keywords k " +
           "LEFT JOIN k.keyword kw " +
           "LEFT JOIN p.authors pa " +
           "LEFT JOIN pa.author a " +
           "LEFT JOIN p.journal j " +
           "WHERE p.pubYear BETWEEN :startYear AND :endYear " +
           "  AND (LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(p.abstractText) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(p.doi) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(kw.keywordText) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(a.fullName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(j.journalName) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "  AND (:authorName IS NULL OR :authorName = '' OR LOWER(a.fullName) LIKE LOWER(CONCAT('%', :authorName, '%'))) " +
           "  AND (:journalId IS NULL OR j.journalId = :journalId)")
    Page<ResearchPaper> searchPapersWithFilters(
            @Param("query") String query,
            @Param("authorName") String authorName,
            @Param("journalId") UUID journalId,
            @Param("startYear") Short startYear,
            @Param("endYear") Short endYear,
            Pageable pageable
    );

    @Query("SELECT DISTINCT p FROM ResearchPaper p " +
           "LEFT JOIN p.keywords k " +
           "LEFT JOIN k.keyword kw " +
           "LEFT JOIN p.journal j " +
           "LEFT JOIN p.field f " +
           "LEFT JOIN p.authors pa " +
           "LEFT JOIN pa.author a " +
           "WHERE p.pubYear BETWEEN :startYear AND :endYear " +
           "  AND (LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(kw.keywordText) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(f.fieldName) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "  AND (:authorName IS NULL OR :authorName = '' OR LOWER(a.fullName) LIKE LOWER(CONCAT('%', :authorName, '%'))) " +
           "  AND (:journalId IS NULL OR j.journalId = :journalId)")
    Page<ResearchPaper> searchPrimaryCandidates(
            @Param("query") String query,
            @Param("authorName") String authorName,
            @Param("journalId") UUID journalId,
            @Param("startYear") Short startYear,
            @Param("endYear") Short endYear,
            Pageable pageable
    );

    @Query("SELECT DISTINCT p FROM ResearchPaper p " +
           "LEFT JOIN p.keywords k " +
           "LEFT JOIN k.keyword kw " +
           "LEFT JOIN p.journal j " +
           "LEFT JOIN p.field f " +
           "LEFT JOIN p.authors pa " +
           "LEFT JOIN pa.author a " +
           "WHERE p.pubYear BETWEEN :startYear AND :endYear " +
           "  AND (LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(kw.keywordText) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(f.fieldName) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "  AND (:authorName IS NULL OR :authorName = '' OR LOWER(a.fullName) LIKE LOWER(CONCAT('%', :authorName, '%'))) " +
           "  AND (:journalId IS NULL OR j.journalId = :journalId)")
    List<ResearchPaper> findPrimaryCandidates(
            @Param("query") String query,
            @Param("authorName") String authorName,
            @Param("journalId") UUID journalId,
            @Param("startYear") Short startYear,
            @Param("endYear") Short endYear,
            Pageable pageable
    );

    @Query("SELECT p FROM ResearchPaper p " +
           "LEFT JOIN p.field f " +
           "WHERE p.pubYear BETWEEN :startYear AND :endYear " +
           "  AND (LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(f.fieldName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR EXISTS (" +
           "      SELECT 1 FROM PaperKeyword pk " +
           "      JOIN pk.keyword kw " +
           "      WHERE pk.paper = p " +
           "        AND LOWER(kw.keywordText) LIKE LOWER(CONCAT('%', :query, '%'))" +
           "   ))")
    List<ResearchPaper> findPrimaryCandidatesWithoutFilters(
            @Param("query") String query,
            @Param("startYear") Short startYear,
            @Param("endYear") Short endYear,
            Pageable pageable
    );

    @Query("SELECT DISTINCT p FROM ResearchPaper p " +
           "JOIN p.authors pa " +
           "WHERE p.pubYear BETWEEN :startYear AND :endYear " +
           "  AND LOWER(pa.author.fullName) LIKE LOWER(CONCAT('%', :authorName, '%'))")
    Page<ResearchPaper> searchByAuthorName(
            @Param("authorName") String authorName,
            @Param("startYear") Short startYear,
            @Param("endYear") Short endYear,
            Pageable pageable
    );

    Page<ResearchPaper> findByJournal_JournalIdAndPubYearBetween(UUID journalId, Short startYear, Short endYear, Pageable pageable);

    Page<ResearchPaper> findByField_FieldIdAndPubYearBetween(UUID fieldId, Short startYear, Short endYear, Pageable pageable);

    Page<ResearchPaper> findByPubYearBetween(Short startYear, Short endYear, Pageable pageable);

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

    @Query("SELECT COUNT(p) FROM ResearchPaper p " +
           "WHERE LOWER(p.title) = LOWER(:title) " +
           "  AND (:pubYear IS NULL OR p.pubYear = :pubYear)")
    long countDuplicateByTitleAndYear(
            @Param("title") String title,
            @Param("pubYear") Short pubYear
    );

    // ---- Overview Statistics Queries ----

    @Query("SELECT COUNT(p) FROM ResearchPaper p WHERE p.createdAt >= :start AND p.createdAt < :end")
    long countByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(p.citationCount), 0) FROM ResearchPaper p")
    long sumAllCitationCounts();

    @Query("SELECT COALESCE(SUM(p.citationCount), 0) FROM ResearchPaper p WHERE p.createdAt >= :start AND p.createdAt < :end")
    long sumCitationCountsByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Sum all citation counts across all research papers.
     * Returns 0 if no papers exist (COALESCE).
     */
    @Query("SELECT COALESCE(SUM(p.citationCount), 0) FROM ResearchPaper p")
    Long sumTotalCitations();

    // ---- Quick Stats Aggregation Queries ----

    /**
     * Sum citation counts for a specific list of paper IDs.
     * Returns 0 if no papers match or no IDs provided.
     */
    @Query("SELECT COALESCE(SUM(p.citationCount), 0) FROM ResearchPaper p WHERE p.paperId IN :ids")
    long sumCitationCountByIds(@Param("ids") List<UUID> ids);

    /**
     * Count papers from a list of IDs that were published in a given year.
     */
    @Query("SELECT COUNT(p) FROM ResearchPaper p WHERE p.paperId IN :ids AND p.pubYear = :year")
    long countByPaperIdsAndPubYear(@Param("ids") List<UUID> ids, @Param("year") Short year);
}
