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

    /**
     * Single-query author overview stats: total authors + distinct authors this/last month.
     * Replaces 3 individual COUNT queries with 1 scan of PAPER_AUTHOR + RESEARCH_PAPER.
     * Returns [totalAuthors, authorsThisMonth, authorsLastMonth]
     */
    @Query(value = """
        SELECT
            (SELECT COUNT(*) FROM AUTHOR),
            COUNT(DISTINCT CASE WHEN p.CreatedAt >= :thisStart AND p.CreatedAt < :thisEnd THEN pa.AuthorID END),
            COUNT(DISTINCT CASE WHEN p.CreatedAt >= :lastStart AND p.CreatedAt < :lastEnd THEN pa.AuthorID END)
        FROM PAPER_AUTHOR pa
        JOIN RESEARCH_PAPER p ON p.PaperID = pa.PaperID
        """, nativeQuery = true)
    List<Object[]> getOverviewAuthorStats(@Param("thisStart") LocalDateTime thisStart,
                                           @Param("thisEnd") LocalDateTime thisEnd,
                                           @Param("lastStart") LocalDateTime lastStart,
                                           @Param("lastEnd") LocalDateTime lastEnd);

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

    /** Count unique co-authors for a given author. */
    @Query("SELECT COUNT(DISTINCT a.authorId) FROM PaperAuthor pa1 "
         + "JOIN PaperAuthor pa2 ON pa1.paper = pa2.paper "
         + "JOIN pa2.author a "
         + "WHERE pa1.author.authorId = :authorId AND pa2.author.authorId <> :authorId")
    long countCoAuthorsByAuthorId(@Param("authorId") UUID authorId);

    @Query(value = """
            SELECT grouped.Country, COUNT(*) AS paperCount, COALESCE(SUM(grouped.CitationCount), 0) AS citationCount
            FROM (
                SELECT DISTINCT a.Country, p.PaperID, COALESCE(p.CitationCount, 0) AS CitationCount
                FROM PAPER_AUTHOR pa
                JOIN AUTHOR a ON a.AuthorID = pa.AuthorID
                JOIN RESEARCH_PAPER p ON p.PaperID = pa.PaperID
                WHERE a.Country IS NOT NULL
                  AND LTRIM(RTRIM(a.Country)) <> ''
                  AND (:year IS NULL OR p.PubYear = :year)
                  AND (
                      :keyword IS NULL OR :keyword = ''
                      OR LOWER(p.Title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                      OR LOWER(p.[Abstract]) LIKE LOWER(CONCAT('%', :keyword, '%'))
                      OR EXISTS (
                          SELECT 1
                          FROM PAPER_KEYWORD pk
                          JOIN KEYWORD k ON k.KeywordID = pk.KeywordID
                          WHERE pk.PaperID = p.PaperID
                            AND LOWER(k.KeywordText) LIKE LOWER(CONCAT('%', :keyword, '%'))
                      )
                  )
            ) grouped
            GROUP BY grouped.Country
            ORDER BY paperCount DESC, citationCount DESC
            """, nativeQuery = true)
    List<Object[]> findCountryBreakdown(@Param("keyword") String keyword, @Param("year") Short year);

    @Query(value = """
            SELECT grouped.Affiliation, COUNT(*) AS paperCount, COALESCE(SUM(grouped.CitationCount), 0) AS citationCount
            FROM (
                SELECT DISTINCT a.Affiliation, p.PaperID, COALESCE(p.CitationCount, 0) AS CitationCount
                FROM PAPER_AUTHOR pa
                JOIN AUTHOR a ON a.AuthorID = pa.AuthorID
                JOIN RESEARCH_PAPER p ON p.PaperID = pa.PaperID
                WHERE a.Affiliation IS NOT NULL
                  AND LTRIM(RTRIM(a.Affiliation)) <> ''
                  AND (:year IS NULL OR p.PubYear = :year)
                  AND (
                      :keyword IS NULL OR :keyword = ''
                      OR LOWER(p.Title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                      OR LOWER(p.[Abstract]) LIKE LOWER(CONCAT('%', :keyword, '%'))
                      OR EXISTS (
                          SELECT 1
                          FROM PAPER_KEYWORD pk
                          JOIN KEYWORD k ON k.KeywordID = pk.KeywordID
                          WHERE pk.PaperID = p.PaperID
                            AND LOWER(k.KeywordText) LIKE LOWER(CONCAT('%', :keyword, '%'))
                      )
                  )
            ) grouped
            GROUP BY grouped.Affiliation
            ORDER BY paperCount DESC, citationCount DESC
            """, nativeQuery = true)
    List<Object[]> findInstitutionBreakdown(@Param("keyword") String keyword,
                                            @Param("year") Short year,
                                            Pageable pageable);

    @Query(value = """
            SELECT grouped.Country, grouped.PubYear, COUNT(*) AS paperCount, COALESCE(SUM(grouped.CitationCount), 0) AS citationCount
            FROM (
                SELECT DISTINCT a.Country, p.PubYear, p.PaperID, COALESCE(p.CitationCount, 0) AS CitationCount
                FROM PAPER_AUTHOR pa
                JOIN AUTHOR a ON a.AuthorID = pa.AuthorID
                JOIN RESEARCH_PAPER p ON p.PaperID = pa.PaperID
                WHERE a.Country IS NOT NULL
                  AND LTRIM(RTRIM(a.Country)) <> ''
                  AND p.PubYear IS NOT NULL
                  AND p.PubYear BETWEEN :startYear AND :endYear
                  AND (
                      :keyword IS NULL OR :keyword = ''
                      OR LOWER(p.Title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                      OR LOWER(p.[Abstract]) LIKE LOWER(CONCAT('%', :keyword, '%'))
                      OR EXISTS (
                          SELECT 1
                          FROM PAPER_KEYWORD pk
                          JOIN KEYWORD k ON k.KeywordID = pk.KeywordID
                          WHERE pk.PaperID = p.PaperID
                            AND LOWER(k.KeywordText) LIKE LOWER(CONCAT('%', :keyword, '%'))
                      )
                  )
            ) grouped
            GROUP BY grouped.Country, grouped.PubYear
            ORDER BY grouped.Country ASC, grouped.PubYear ASC
            """, nativeQuery = true)
    List<Object[]> findCountryTrend(@Param("keyword") String keyword,
                                    @Param("startYear") Short startYear,
                                    @Param("endYear") Short endYear);

    /** Top authors by paper count in a specific journal. */
    @Query("SELECT a.fullName, a.affiliation, a.externalAuthorId, "
         + "COUNT(DISTINCT pa.paper) AS paperCount, "
         + "COALESCE(SUM(p.citationCount), 0) AS totalCitations "
         + "FROM PaperAuthor pa "
         + "JOIN pa.author a "
         + "JOIN pa.paper p "
         + "WHERE p.journal.journalName = :journalName "
         + "GROUP BY a.authorId, a.fullName, a.affiliation, a.externalAuthorId "
         + "ORDER BY COUNT(DISTINCT pa.paper) DESC")
    List<Object[]> findTopAuthorsByJournalName(@Param("journalName") String journalName, Pageable pageable);
}
