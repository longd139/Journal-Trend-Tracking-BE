package com.sra.journal_tracking.service.impl;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sra.journal_tracking.dto.dashboard.OverviewStatsResponse;
import com.sra.journal_tracking.dto.dashboard.TotalPapersResponse;
import com.sra.journal_tracking.repository.jpa.PaperAuthorRepository;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
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

    /**
     * Helper: calculate MoM growth rate = ((this - last) / last) * 100.
     * Returns null when last is zero.
     */
    private static Double growthRate(long thisMonth, long lastMonth) {
        if (lastMonth <= 0) return null;
        return ((double) (thisMonth - lastMonth) / lastMonth) * 100.0;
    }

    @Override
    @Cacheable(value = "dashboardOverview", key = "'overview'")
    public OverviewStatsResponse getOverviewStats() {
        log.info("Fetching overview dashboard statistics (cache miss)");

        // Month ranges
        YearMonth thisMonth = YearMonth.now();
        YearMonth lastMonth = thisMonth.minusMonths(1);
        LocalDateTime thisStart = thisMonth.atDay(1).atStartOfDay();
        LocalDateTime thisEnd   = thisMonth.plusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime lastStart = lastMonth.atDay(1).atStartOfDay();
        LocalDateTime lastEnd   = thisMonth.atDay(1).atStartOfDay();

        // ---- Single query: all paper stats ----
        List<Object[]> paperStats = researchPaperRepository.getOverviewPaperStats(
                thisStart, thisEnd, lastStart, lastEnd);
        Object[] ps = paperStats.get(0);
        long papersTracked     = ((Number) ps[0]).longValue();
        long newPapersThisMonth = ((Number) ps[1]).longValue();
        long newPapersLastMonth = ((Number) ps[2]).longValue();
        long totalCitations     = ((Number) ps[3]).longValue();
        long citationsThisMonth = ((Number) ps[4]).longValue();
        long citationsLastMonth = ((Number) ps[5]).longValue();

        // ---- Single query: all author stats ----
        List<Object[]> authorStats = paperAuthorRepository.getOverviewAuthorStats(
                thisStart, thisEnd, lastStart, lastEnd);
        Object[] as = authorStats.get(0);
        long totalAuthors     = ((Number) as[0]).longValue();
        long authorsThisMonth = ((Number) as[1]).longValue();
        long authorsLastMonth = ((Number) as[2]).longValue();

        // Growth rates
        Double papersGrowthRate   = growthRate(newPapersThisMonth, newPapersLastMonth);
        Double citationsGrowthRate = growthRate(citationsThisMonth, citationsLastMonth);
        Double authorsGrowthRate  = growthRate(authorsThisMonth, authorsLastMonth);

        OverviewStatsResponse response = OverviewStatsResponse.builder()
                .papersTracked(papersTracked)
                .papersTrackedGrowthRate(papersGrowthRate)
                .totalCitations(totalCitations)
                .totalCitationsGrowthRate(citationsGrowthRate)
                .paperGrowth(newPapersThisMonth)
                .paperGrowthRate(papersGrowthRate)
                .totalAuthors(totalAuthors)
                .totalAuthorsGrowthRate(authorsGrowthRate)
                .build();

        log.info("Overview stats: papers={}({}%), citations={}({}%), "
                + "paperGrowth={}({}%), authors={}({}%)",
                papersTracked, papersGrowthRate,
                totalCitations, citationsGrowthRate,
                newPapersThisMonth, papersGrowthRate,
                totalAuthors, authorsGrowthRate);

        return response;
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