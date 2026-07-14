package com.sra.journal_tracking.repository.jpa;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    @Query("SELECT DISTINCT p FROM ResearchPaper p " +
           "LEFT JOIN FETCH p.journal " +
           "LEFT JOIN FETCH p.authors pa " +
           "LEFT JOIN FETCH pa.author " +
           "WHERE p.paperId = :paperId")
    Optional<ResearchPaper> findByIdWithAuthors(@Param("paperId") UUID paperId);

    Optional<ResearchPaper> findByDoi(String doi);

    Optional<ResearchPaper> findByOpenAlexWorkId(String openAlexWorkId);

    @Query("SELECT COUNT(p) FROM ResearchPaper p " +
           "WHERE LOWER(p.title) = LOWER(:title) " +
           "  AND (:pubYear IS NULL OR p.pubYear = :pubYear)")
    long countDuplicateByTitleAndYear(
            @Param("title") String title,
            @Param("pubYear") Short pubYear
    );

    // ---- Overview Statistics Queries ----

    @Query("SELECT p FROM ResearchPaper p "
         + "LEFT JOIN FETCH p.source "
         + "WHERE p.createdAt >= :since "
         + "ORDER BY p.createdAt DESC")
    Page<ResearchPaper> findRecentPapers(@Param("since") LocalDateTime since, Pageable pageable);

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

    /**
     * Single-query overview stats: total papers, new-papers-this/last-month,
     * total citations, citations-this/last-month.
     * Replaces 6 individual COUNT/SUM queries with 1 table scan.
     * Returns [totalPapers, newThisMonth, newLastMonth, totalCitations, citationsThisMonth, citationsLastMonth]
     */
    @Query(value = """
        SELECT
            COUNT(*),
            COUNT(CASE WHEN CreatedAt >= :thisStart AND CreatedAt < :thisEnd THEN 1 END),
            COUNT(CASE WHEN CreatedAt >= :lastStart AND CreatedAt < :lastEnd THEN 1 END),
            COALESCE(SUM(CitationCount), 0),
            COALESCE(SUM(CASE WHEN CreatedAt >= :thisStart AND CreatedAt < :thisEnd THEN CitationCount END), 0),
            COALESCE(SUM(CASE WHEN CreatedAt >= :lastStart AND CreatedAt < :lastEnd THEN CitationCount END), 0)
        FROM RESEARCH_PAPER
        """, nativeQuery = true)
    List<Object[]> getOverviewPaperStats(@Param("thisStart") LocalDateTime thisStart,
                                          @Param("thisEnd") LocalDateTime thisEnd,
                                          @Param("lastStart") LocalDateTime lastStart,
                                          @Param("lastEnd") LocalDateTime lastEnd);

    // ---- Quick Stats Aggregation Queries ----

    /**
     * Sum citation counts for a specific list of paper IDs.
     * Returns 0 if no papers match or no IDs provided.
     */
    @Query("SELECT COALESCE(SUM(p.citationCount), 0) FROM ResearchPaper p WHERE p.paperId IN :ids")
    long sumCitationCountByIds(@Param("ids") List<UUID> ids);

    /**
     * Sum citations for papers matching a keyword (via PAPER_KEYWORD join).
     * Fallback when Neo4j has stale/incomplete data.
     */
    @Query("SELECT COALESCE(SUM(p.citationCount), 0) FROM ResearchPaper p "
         + "JOIN p.keywords pk JOIN pk.keyword kw "
         + "WHERE LOWER(kw.keywordText) = LOWER(:keyword)")
    long sumCitationCountByKeyword(@Param("keyword") String keyword);

    /**
     * Count papers from a list of IDs that were published in a given year.
     */
    @Query("SELECT COUNT(p) FROM ResearchPaper p WHERE p.paperId IN :ids AND p.pubYear = :year")
    long countByPaperIdsAndPubYear(@Param("ids") List<UUID> ids, @Param("year") Short year);

    /**
     * Find the publication year with the most papers from a list of paper IDs.
     * Returns the year (Short) or null if the list is empty.
     * Used for "top year" in keyword comparison BarChart.
     */
    @Query("SELECT p.pubYear FROM ResearchPaper p "
         + "WHERE p.paperId IN :ids "
         + "GROUP BY p.pubYear "
         + "ORDER BY COUNT(p) DESC, p.pubYear DESC")
    List<Short> findPeakYearByPaperIds(@Param("ids") List<UUID> ids, Pageable pageable);

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
    @Query("SELECT p FROM ResearchPaper p "
         + "LEFT JOIN FETCH p.journal "
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
     * Top keywords from a list of paper IDs.
     * Returns [keywordText, paperCount] ordered by frequency DESC.
     */
    @Query("SELECT kw.keywordText, COUNT(DISTINCT p) FROM ResearchPaper p "
         + "JOIN p.keywords pk JOIN pk.keyword kw "
         + "WHERE p.paperId IN :ids "
         + "GROUP BY kw.keywordText ORDER BY COUNT(DISTINCT p) DESC")
    List<Object[]> findTopKeywordsByPaperIds(@Param("ids") List<UUID> ids, Pageable pageable);

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

    /**
     * Find papers by exact keyword match (normalized) — faster and more precise than LIKE.
     * Used as primary SQL fallback when Neo4j has no data for a keyword.
     */
    @Query("SELECT DISTINCT p FROM ResearchPaper p "
         + "LEFT JOIN FETCH p.journal "
         + "LEFT JOIN p.keywords pk "
         + "LEFT JOIN pk.keyword kw "
         + "WHERE LOWER(kw.keywordText) = LOWER(:keyword) "
         + "ORDER BY p.citationCount DESC")
    List<ResearchPaper> findTopCitedByKeywordExact(@Param("keyword") String keyword, Pageable pageable);

	    // ── Report Queries ──

	    /**
	     * Top keywords published in a specific journal in recent years (from startYear).
	     * Returns [keywordText, paperCount] ordered by frequency DESC.
	     * Used for journal quality report "taste" analysis.
	     */
	    @Query("SELECT kw.keywordText, COUNT(p) FROM ResearchPaper p "
	         + "JOIN p.keywords pk JOIN pk.keyword kw "
	         + "WHERE p.journal.journalId = :journalId AND p.pubYear >= :startYear "
	         + "GROUP BY kw.keywordText ORDER BY COUNT(p) DESC")
	    List<Object[]> findTopKeywordsByJournalIdRecent(
	            @Param("journalId") UUID journalId,
	            @Param("startYear") Short startYear,
	            Pageable pageable);

	    /**
	     * Count papers for a given author in recent years (from startYear).
	     * Used to determine if an author is actively publishing.
	     */
	    @Query("SELECT COUNT(p) FROM ResearchPaper p "
	         + "JOIN p.authors pa "
	         + "WHERE pa.author.authorId = :authorId AND p.pubYear >= :startYear")
	    long countRecentPapersByAuthorId(
	            @Param("authorId") UUID authorId,
	            @Param("startYear") Short startYear);

	    /**
	     * Get yearly paper counts for a set of paper IDs.
	     * Returns [pubYear, paperCount] ordered by year ASC.
	     * Used for keyword trend report yearly breakdown chart.
	     */
	    @Query("SELECT p.pubYear, COUNT(p) FROM ResearchPaper p "
	         + "WHERE p.paperId IN :ids AND p.pubYear >= :startYear "
	         + "GROUP BY p.pubYear ORDER BY p.pubYear ASC")
	    List<Object[]> countPapersByYearForIds(
	            @Param("ids") List<UUID> ids,
	            @Param("startYear") Short startYear);

    /**
     * Get yearly citation sum for a set of paper IDs.
     * Returns [pubYear, sumCitationCount] ordered by year ASC.
     * Used for keyword trend report citation trend chart.
     */
    @Query("SELECT p.pubYear, COALESCE(SUM(p.citationCount), 0) FROM ResearchPaper p "
         + "WHERE p.paperId IN :ids AND p.pubYear >= :startYear "
         + "GROUP BY p.pubYear ORDER BY p.pubYear ASC")
    List<Object[]> sumCitationsByYearForIds(
            @Param("ids") List<UUID> ids,
            @Param("startYear") Short startYear);

	// ── Recommendation Queries ──

	/**
	 * Find papers linked to any of the given keyword IDs, ordered by citation count.
	 * Used for content-based recommendation candidate generation.
	 */
	@Query("SELECT DISTINCT rp FROM ResearchPaper rp "
	     + "JOIN rp.keywords pk JOIN pk.keyword k "
	     + "WHERE k.keywordId IN :keywordIds AND rp.pubYear >= :minYear "
	     + "ORDER BY rp.citationCount DESC")
	List<ResearchPaper> findTopCitedByKeywordIds(
	        @Param("keywordIds") List<UUID> keywordIds,
	        @Param("minYear") Short minYear,
	        Pageable pageable);

	/**
	 * Find papers bookmarked by a set of similar users, excluding papers the
	 * current user already has. Used for collaborative filtering.
	 */
	@Query("SELECT DISTINCT rp FROM ResearchPaper rp "
	     + "JOIN Bookmark b ON b.paper.paperId = rp.paperId "
	     + "WHERE b.user.userId IN :similarUserIds "
	     + "AND rp.paperId NOT IN :excludePaperIds "
	     + "AND rp.pubYear >= :minYear "
	     + "ORDER BY rp.citationCount DESC")
	List<ResearchPaper> findPapersBookmarkedByUsers(
	        @Param("similarUserIds") List<UUID> similarUserIds,
	        @Param("excludePaperIds") List<UUID> excludePaperIds,
	        @Param("minYear") Short minYear,
	        Pageable pageable);

	/**
	 * Find papers in the same research field, excluding the given paper.
	 * Used for "Similar Papers" content-based recommendations.
	 */
	@Query("SELECT rp FROM ResearchPaper rp "
	     + "LEFT JOIN FETCH rp.journal "
	     + "LEFT JOIN FETCH rp.field "
	     + "WHERE rp.field.fieldId = :fieldId AND rp.paperId <> :excludePaperId "
	     + "ORDER BY rp.citationCount DESC")
	List<ResearchPaper> findByFieldIdAndPaperIdNot(
	        @Param("fieldId") UUID fieldId,
	        @Param("excludePaperId") UUID excludePaperId,
	        Pageable pageable);

	/**
	 * Find papers by their IDs with journal and field eagerly fetched.
	 * Used by recommendation services to batch-load paper details.
	 */
	@Query("SELECT rp FROM ResearchPaper rp "
	     + "LEFT JOIN FETCH rp.journal "
	     + "LEFT JOIN FETCH rp.field "
	     + "WHERE rp.paperId IN :ids")
	List<ResearchPaper> findAllByIdWithDetails(@Param("ids") Collection<UUID> ids);

	// ── Researcher Overview Queries (User.fullName = Author.fullName) ──

	/**
	 * Fetch all papers for a researcher (by full name) with citation counts,
	 * sorted by citationCount DESC. Used to compute h-index and citation history.
	 * Returns [paperId, pubYear, citationCount].
	 */
	@Query(value = """
	    SELECT p.PaperID, p.PubYear, COALESCE(p.CitationCount, 0)
	    FROM RESEARCH_PAPER p
	    JOIN PAPER_AUTHOR pa ON p.PaperID = pa.PaperID
	    JOIN AUTHOR a ON pa.AuthorID = a.AuthorID
	    WHERE a.FullName = :fullName AND p.PubYear IS NOT NULL AND (p.Type = 'article' OR p.Type IS NULL) AND NOT (p.Title LIKE '%#%' AND p.CitationCount = 0)
	    ORDER BY p.CitationCount DESC
	    """, nativeQuery = true)
	List<Object[]> getAuthorPapersWithCitations(@Param("fullName") String fullName);

	/**
	 * Keyword distribution for a researcher's papers.
	 * Returns [keywordText, paperCount] ordered by paper count DESC.
	 */
	@Query(value = """
	    SELECT kw.KeywordText, COUNT(DISTINCT p.PaperID) AS paperCount
	    FROM RESEARCH_PAPER p
	    JOIN PAPER_AUTHOR pa ON p.PaperID = pa.PaperID
	    JOIN AUTHOR a ON pa.AuthorID = a.AuthorID
	    JOIN PAPER_KEYWORD pk ON p.PaperID = pk.PaperID
	    JOIN KEYWORD kw ON pk.KeywordID = kw.KeywordID
	    WHERE a.FullName = :fullName AND (p.Type = 'article' OR p.Type IS NULL) AND NOT (p.Title LIKE '%#%' AND p.CitationCount = 0)
	    GROUP BY kw.KeywordText
	    ORDER BY paperCount DESC
	    """, nativeQuery = true)
	List<Object[]> getAuthorKeywordDistribution(@Param("fullName") String fullName);

	/**
	 * Recent publications for a researcher, with journal name and author role info.
	 * Returns [paperId, title, journalName, pubYear, authorOrder, citationCount, isCorresponding]
	 * ordered by pubYear DESC, citationCount DESC. Limited to :limit rows.
	 */
	@Query(value = """
	    SELECT p.PaperID, p.Title, j.JournalName, p.PubYear,
	           pa.AuthorOrder, COALESCE(p.CitationCount, 0), pa.IsCorresponding
	    FROM RESEARCH_PAPER p
	    JOIN PAPER_AUTHOR pa ON p.PaperID = pa.PaperID
	    JOIN AUTHOR a ON pa.AuthorID = a.AuthorID
	    LEFT JOIN JOURNAL j ON p.JournalID = j.JournalID
	    WHERE a.FullName = :fullName AND (p.Type = 'article' OR p.Type IS NULL) AND NOT (p.Title LIKE '%#%' AND p.CitationCount = 0)
	    ORDER BY p.PubYear DESC, p.CitationCount DESC
	    OFFSET 0 ROWS FETCH NEXT :limit ROWS ONLY
	    """, nativeQuery = true)
	List<Object[]> getAuthorRecentPublications(@Param("fullName") String fullName,
	                                           @Param("limit") int limit);

	// ═══════════════════════════════════════════════════════════
	//  Author-specific dashboard overview (by authorId, not name)
	// ═══════════════════════════════════════════════════════════

	/** Sum citations for all papers of an author (by authorId). Returns 0 if no papers. */
	@Query(value = """
	    SELECT COALESCE(SUM(p.CitationCount), 0)
	    FROM RESEARCH_PAPER p
	    JOIN PAPER_AUTHOR pa ON p.PaperID = pa.PaperID
	    WHERE pa.AuthorID = :authorId
	    """, nativeQuery = true)
	Long sumCitationsByAuthorId(@Param("authorId") UUID authorId);

	/** Get [paperId, citationCount] for all papers of an author, sorted by citations DESC. Used for h-index. */
	@Query(value = """
	    SELECT p.PaperID, COALESCE(p.CitationCount, 0)
	    FROM RESEARCH_PAPER p
	    JOIN PAPER_AUTHOR pa ON p.PaperID = pa.PaperID
	    WHERE pa.AuthorID = :authorId
	    ORDER BY p.CitationCount DESC
	    """, nativeQuery = true)
	List<Object[]> getCitationCountsByAuthorId(@Param("authorId") UUID authorId);

	/** Insert a minimal paper stub (used by reading history when paper not in DB). */
	@Modifying
	@Query(value = """
	    INSERT INTO RESEARCH_PAPER (PaperID, SourceID, Title, DOI, PubYear, CitationCount, IsOpenAccess, CreatedAt)
	    VALUES (:paperId, :sourceId, :title, :doi, :pubYear, 0, 0, GETDATE())
	    """, nativeQuery = true)
	int insertPaperStub(@Param("paperId") UUID paperId,
	                    @Param("sourceId") UUID sourceId,
	                    @Param("title") String title,
	                    @Param("doi") String doi,
	                    @Param("pubYear") Short pubYear);

	/** Top cited papers by author name — no year filter, all time. Used by author top-papers endpoint. */
	@Query("""
	    SELECT p FROM ResearchPaper p
	    JOIN p.authors pa
	    JOIN pa.author a
	    WHERE a.fullName = :fullName
	    ORDER BY p.citationCount DESC
	    """)
	List<ResearchPaper> findTopCitedByAuthorName(@Param("fullName") String fullName, Pageable pageable);
}
