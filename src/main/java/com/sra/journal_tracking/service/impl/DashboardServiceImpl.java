package com.sra.journal_tracking.service.impl;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sra.journal_tracking.dto.dashboard.OverviewStatsResponse;
import com.sra.journal_tracking.dto.dashboard.TotalPapersResponse;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.repository.jpa.AuthorRepository;
import com.sra.journal_tracking.repository.jpa.PaperAuthorRepository;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.service.AuthorQuickStatsService;
import com.sra.journal_tracking.service.DashboardService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final ResearchPaperRepository researchPaperRepository;
    private final PaperAuthorRepository paperAuthorRepository;
    private final AuthorRepository authorRepository;
    private final AuthorQuickStatsService authorQuickStatsService;

    /** Helper: calculate MoM growth rate = ((this - last) / last) * 100. */
    private static Double growthRate(long thisMonth, long lastMonth) {
        if (lastMonth <= 0) return null;
        return ((double) (thisMonth - lastMonth) / lastMonth) * 100.0;
    }

    /** Compute h-index from a list of papers sorted by citationCount DESC. */
    private static int computeHIndex(List<Object[]> papersSortedByCitationsDesc) {
        int h = 0;
        for (int i = 0; i < papersSortedByCitationsDesc.size(); i++) {
            int citations = ((Number) papersSortedByCitationsDesc.get(i)[1]).intValue();
            if (citations >= i + 1) {
                h = i + 1;
            } else {
                break;
            }
        }
        return h;
    }

    @Override
    @Cacheable(value = "dashboardOverview", key = "#authorId != null ? 'author_' + #authorId.toString() : 'system'")
    public OverviewStatsResponse getOverviewStats(UUID authorId) {
        if (authorId != null) {
            return buildAuthorStats(authorId);
        }
        return buildSystemStats();
    }

    // ── Author-specific stats (from OpenAlex API, not local DB) ──────

    private OverviewStatsResponse buildAuthorStats(UUID authorId) {
        var author = authorRepository.findById(authorId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        String authorName = author.getFullName();
        log.info("Fetching author-specific dashboard stats for '{}' from OpenAlex", authorName);

        try {
            // Fetch real stats from OpenAlex (same as search author quick-stats)
            var openAlexStats = authorQuickStatsService.searchAuthor(authorName);

            long totalPapers = openAlexStats.getTotalPapers() != null ? openAlexStats.getTotalPapers() : 0L;
            long totalCitations = openAlexStats.getTotalCitations() != null ? openAlexStats.getTotalCitations() : 0L;
            int hIndex = openAlexStats.getHIndex() != null ? openAlexStats.getHIndex() : 0;

            // Co-authors from local DB (only if author has papers synced locally)
            long coAuthors = paperAuthorRepository.countCoAuthorsByAuthorId(authorId);

            log.info("Author '{}' from OpenAlex: papers={}, citations={}, hIndex={}, coAuthors={}",
                    authorName, totalPapers, totalCitations, hIndex, coAuthors);

            return OverviewStatsResponse.builder()
                    .authorName(openAlexStats.getFullName())
                    .authorTotalPapers(totalPapers)
                    .authorTotalCitations(totalCitations)
                    .authorHIndex(hIndex)
                    .authorCoAuthors(coAuthors)
                    .build();

        } catch (AppException e) {
            if (e.getErrorCode() == ErrorCode.AUTHOR_NOT_FOUND) {
                log.info("Author '{}' not found on OpenAlex, falling back to local DB", authorName);
            } else {
                log.warn("OpenAlex unavailable for '{}': {}", authorName, e.getMessage());
            }
            return buildAuthorStatsFromLocal(authorId, authorName);
        } catch (Exception e) {
            log.warn("Unexpected error fetching OpenAlex stats for '{}': {}", authorName, e.getMessage());
            return buildAuthorStatsFromLocal(authorId, authorName);
        }
    }

    /** Fallback: use local DB when OpenAlex is unavailable. */
    private OverviewStatsResponse buildAuthorStatsFromLocal(UUID authorId, String authorName) {
        long totalPapers = paperAuthorRepository.countByAuthor_AuthorId(authorId);
        Long totalCitations = researchPaperRepository.sumCitationsByAuthorId(authorId);
        if (totalCitations == null) totalCitations = 0L;
        List<Object[]> citationCounts = researchPaperRepository.getCitationCountsByAuthorId(authorId);
        int hIndex = citationCounts.isEmpty() ? 0 : computeHIndex(citationCounts);
        long coAuthors = paperAuthorRepository.countCoAuthorsByAuthorId(authorId);

        log.info("Author '{}' from local DB: papers={}, citations={}, hIndex={}, coAuthors={}",
                authorName, totalPapers, totalCitations, hIndex, coAuthors);

        return OverviewStatsResponse.builder()
                .authorName(authorName)
                .authorTotalPapers(totalPapers)
                .authorTotalCitations(totalCitations)
                .authorHIndex(hIndex)
                .authorCoAuthors(coAuthors)
                .build();
    }

    // ── System-wide stats (backward compat) ────────────────────

    private OverviewStatsResponse buildSystemStats() {
        log.info("Fetching system-wide overview dashboard statistics (cache miss)");

        YearMonth thisMonth = YearMonth.now();
        YearMonth lastMonth = thisMonth.minusMonths(1);
        LocalDateTime thisStart = thisMonth.atDay(1).atStartOfDay();
        LocalDateTime thisEnd   = thisMonth.plusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime lastStart = lastMonth.atDay(1).atStartOfDay();
        LocalDateTime lastEnd   = thisMonth.atDay(1).atStartOfDay();

        List<Object[]> paperStats = researchPaperRepository.getOverviewPaperStats(
                thisStart, thisEnd, lastStart, lastEnd);
        Object[] ps = paperStats.get(0);
        long papersTracked      = ((Number) ps[0]).longValue();
        long newPapersThisMonth = ((Number) ps[1]).longValue();
        long newPapersLastMonth = ((Number) ps[2]).longValue();
        long totalCitations     = ((Number) ps[3]).longValue();
        long citationsThisMonth = ((Number) ps[4]).longValue();
        long citationsLastMonth = ((Number) ps[5]).longValue();

        List<Object[]> authorStats = paperAuthorRepository.getOverviewAuthorStats(
                thisStart, thisEnd, lastStart, lastEnd);
        Object[] as = authorStats.get(0);
        long totalAuthors     = ((Number) as[0]).longValue();
        long authorsThisMonth = ((Number) as[1]).longValue();
        long authorsLastMonth = ((Number) as[2]).longValue();

        Double papersGrowthRate    = growthRate(newPapersThisMonth, newPapersLastMonth);
        Double citationsGrowthRate = growthRate(citationsThisMonth, citationsLastMonth);
        Double authorsGrowthRate   = growthRate(authorsThisMonth, authorsLastMonth);

        log.info("System overview stats: papers={}({}%), citations={}({}%), "
                + "paperGrowth={}({}%), authors={}({}%)",
                papersTracked, papersGrowthRate,
                totalCitations, citationsGrowthRate,
                newPapersThisMonth, papersGrowthRate,
                totalAuthors, authorsGrowthRate);

        return OverviewStatsResponse.builder()
                .papersTracked(papersTracked)
                .papersTrackedGrowthRate(papersGrowthRate)
                .totalCitations(totalCitations)
                .totalCitationsGrowthRate(citationsGrowthRate)
                .paperGrowth(newPapersThisMonth)
                .paperGrowthRate(papersGrowthRate)
                .totalAuthors(totalAuthors)
                .totalAuthorsGrowthRate(authorsGrowthRate)
                .build();
    }

    @Override
    @Cacheable(value = "dashboardOverview", key = "'totalPapers'")
    public TotalPapersResponse getTotalPapers() {
        long count = researchPaperRepository.count();
        log.info("Total papers in system: {}", count);
        return TotalPapersResponse.builder()
                .totalPapers(count)
                .build();
    }
}
