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

    /**
     * Count papers per journal from a list of paper IDs.
     * Returns [journalName, impactFactor, quartile, publisher, paperCount] tuples,
     * ordered by paper count descending (top journals first).
     * Used for the "Top Journals" horizontal bar chart in keyword quick-stats.
     */
    @Query("SELECT j.journalName, COALESCE(j.impactFactor, 0), j.quartile, j.publisher, COUNT(p) "
         + "FROM ResearchPaper p JOIN p.journal j "
         + "WHERE p.paperId IN :ids "
         + "GROUP BY j.journalName, j.impactFactor, j.quartile, j.publisher "
         + "ORDER BY COUNT(p) DESC")
    List<Object[]> findTopJournalsByPaperIds(@Param("ids") List<UUID> ids, Pageable pageable);

    /**
     * Find papers by IDs, ordered by citation count descending.
     * Used for "Top 5 Most Influential Papers" in keyword quick-stats.
     */
    @Query("SELECT DISTINCT p FROM ResearchPaper p "
         + "LEFT JOIN FETCH p.journal "
         + "LEFT JOIN FETCH p.keywords pk "
         + "LEFT JOIN FETCH pk.keyword "
         + "WHERE p.paperId IN :ids "
         + "ORDER BY p.citationCount DESC")
    List<ResearchPaper> findTopCitedByIds(@Param("ids") List<UUID> ids, Pageable pageable);

    // ---- Journal Quick Stats Aggregation ----

    /** Count papers published in a specific journal. */
    long countByJournal_JournalId(@Param("journalId") UUID journalId);

    /** Sum citations for all papers in a specific journal. */
    @Query("SELECT COALESCE(SUM(p.citationCount), 0) FROM ResearchPaper p WHERE p.journal.journalId = :journalId")
    long sumCitationsByJournalId(@Param("journalId") UUID journalId);

    /**
     * Top keywords published in a specific journal.
     * Returns [keywordText, paperCount] ordered by frequency DESC.
     */
    @Query("SELECT kw.keywordText, COUNT(p) FROM ResearchPaper p "
         + "JOIN p.keywords pk JOIN pk.keyword kw "
         + "WHERE p.journal.journalId = :journalId "
         + "GROUP BY kw.keywordText ORDER BY COUNT(p) DESC")
    List<Object[]> findTopKeywordsByJournalId(@Param("journalId") UUID journalId, Pageable pageable);

    /**
     * Yearly paper count and citation sum for a journal (timeline).
     * Returns [pubYear, paperCount, citationCount] ordered by year ASC.
     */
    @Query("SELECT p.pubYear, COUNT(p), COALESCE(SUM(p.citationCount), 0) "
         + "FROM ResearchPaper p "
         + "WHERE p.journal.journalId = :journalId AND p.pubYear >= :startYear "
         + "GROUP BY p.pubYear ORDER BY p.pubYear ASC")
    List<Object[]> getJournalYearlyStats(@Param("journalId") UUID journalId,
                                         @Param("startYear") Short startYear);

    /**
     * Top-cited papers in a journal (no year filter).
     * Used for "Top 5 Most Cited Papers" in journal detail.
     */
    @Query("SELECT p FROM ResearchPaper p "
         + "LEFT JOIN FETCH p.journal "
         + "LEFT JOIN FETCH p.keywords pk "
         + "LEFT JOIN FETCH pk.keyword "
         + "WHERE p.journal.journalId = :journalId "
         + "ORDER BY p.citationCount DESC")
    List<ResearchPaper> findTopCitedByJournalId(@Param("journalId") UUID journalId, Pageable pageable);

    /**
     * Top contributing authors in a journal.
     * Returns [authorName, externalAuthorId, paperCount, totalCitations] ordered by paper count DESC.
     */
    @Query("SELECT a.fullName, a.externalAuthorId, COUNT(DISTINCT p), COALESCE(SUM(p.citationCount), 0) "
         + "FROM ResearchPaper p "
         + "JOIN p.authors pa JOIN pa.author a "
         + "WHERE p.journal.journalId = :journalId "
         + "GROUP BY a.fullName, a.externalAuthorId "
         + "ORDER BY COUNT(DISTINCT p) DESC")
    List<Object[]> findTopAuthorsByJournalId(@Param("journalId") UUID journalId, Pageable pageable);

    // ---- Weekly Breakout / Sparkline Queries ----

    /**
     * Sum citation counts grouped by (year, month) of pubDate for a list of paper IDs.
     * Only considers papers where pubDate IS NOT NULL.
     * Returns [year, month, sumCitationCount] ordered by year/month DESC.
     */
    @Query("SELECT YEAR(p.pubDate), MONTH(p.pubDate), COALESCE(SUM(p.citationCount), 0) "
         + "FROM ResearchPaper p "
         + "WHERE p.paperId IN :ids AND p.pubDate IS NOT NULL "
         + "GROUP BY YEAR(p.pubDate), MONTH(p.pubDate) "
         + "ORDER BY YEAR(p.pubDate) DESC, MONTH(p.pubDate) DESC")
    List<Object[]> sumCitationsByPubDateMonthForPaperIds(@Param("ids") List<UUID> ids);

    /**
     * Sum citation counts grouped by (year, month) of createdAt for a list of paper IDs.
     * Only considers papers where pubDate IS NULL (fallback when pubDate is missing).
     * Returns [year, month, sumCitationCount] ordered by year/month DESC.
     */
    @Query("SELECT YEAR(p.createdAt), MONTH(p.createdAt), COALESCE(SUM(p.citationCount), 0) "
         + "FROM ResearchPaper p "
         + "WHERE p.paperId IN :ids AND p.pubDate IS NULL "
         + "GROUP BY YEAR(p.createdAt), MONTH(p.createdAt) "
         + "ORDER BY YEAR(p.createdAt) DESC, MONTH(p.createdAt) DESC")
    List<Object[]> sumCitationsByCreatedAtMonthForPaperIds(@Param("ids") List<UUID> ids);

    /**
     * Fast top-cited papers by keyword — single SQL query, no Neo4j.
     * Searches title + abstract + keywords with LIKE, orders by citation DESC, limit N.
     */
    @Query("SELECT DISTINCT p FROM ResearchPaper p "
         + "LEFT JOIN FETCH p.journal "
         + "LEFT JOIN p.keywords pk "
         + "LEFT JOIN pk.keyword kw "
         + "WHERE (LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) "
         + "   OR LOWER(p.abstractText) LIKE LOWER(CONCAT('%', :query, '%')) "
         + "   OR LOWER(kw.keywordText) LIKE LOWER(CONCAT('%', :query, '%'))) "
         + "ORDER BY p.citationCount DESC")
    List<ResearchPaper> findTopCitedByKeyword(@Param("query") String query, Pageable pageable);
}
