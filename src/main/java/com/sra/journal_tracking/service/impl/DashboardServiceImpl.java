package com.sra.journal_tracking.service.impl;

import java.time.LocalDateTime;
import java.time.YearMonth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sra.journal_tracking.dto.dashboard.OverviewStatsResponse;
import com.sra.journal_tracking.dto.dashboard.TotalPapersResponse;
import com.sra.journal_tracking.repository.jpa.AuthorRepository;
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
    private final AuthorRepository authorRepository;
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
    public OverviewStatsResponse getOverviewStats() {
        log.info("Fetching overview dashboard statistics");

        // Month ranges
        YearMonth thisMonth = YearMonth.now();
        YearMonth lastMonth = thisMonth.minusMonths(1);
        LocalDateTime thisStart = thisMonth.atDay(1).atStartOfDay();
        LocalDateTime thisEnd   = thisMonth.plusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime lastStart = lastMonth.atDay(1).atStartOfDay();
        LocalDateTime lastEnd   = thisMonth.atDay(1).atStartOfDay();

        // ---- Card 1: Papers tracked ----
        long papersTracked = researchPaperRepository.count();
        long newPapersThisMonth = researchPaperRepository.countByCreatedAtBetween(thisStart, thisEnd);
        long newPapersLastMonth = researchPaperRepository.countByCreatedAtBetween(lastStart, lastEnd);
        Double papersGrowthRate = growthRate(newPapersThisMonth, newPapersLastMonth);

        // ---- Card 2: Total citations ----
        Long totalCitations = researchPaperRepository.sumTotalCitations();
        long citationsThisMonth = researchPaperRepository.sumCitationCountsByCreatedAtBetween(thisStart, thisEnd);
        long citationsLastMonth = researchPaperRepository.sumCitationCountsByCreatedAtBetween(lastStart, lastEnd);
        Double citationsGrowthRate = growthRate(citationsThisMonth, citationsLastMonth);

        // ---- Card 3: Paper Growth (new papers this month) ----
        long paperGrowth = newPapersThisMonth;
        Double paperGrowthRate = papersGrowthRate; // same MoM growth

        // ---- Card 4: Total authors ----
        long totalAuthors = authorRepository.count();
        long authorsThisMonth = paperAuthorRepository.countDistinctAuthorsByPaperCreatedAtBetween(thisStart, thisEnd);
        long authorsLastMonth = paperAuthorRepository.countDistinctAuthorsByPaperCreatedAtBetween(lastStart, lastEnd);
        Double authorsGrowthRate = growthRate(authorsThisMonth, authorsLastMonth);

        OverviewStatsResponse response = OverviewStatsResponse.builder()
                .papersTracked(papersTracked)
                .papersTrackedGrowthRate(papersGrowthRate)
                .totalCitations(totalCitations)
                .totalCitationsGrowthRate(citationsGrowthRate)
                .paperGrowth(paperGrowth)
                .paperGrowthRate(paperGrowthRate)
                .totalAuthors(totalAuthors)
                .totalAuthorsGrowthRate(authorsGrowthRate)
                .build();

        log.info("Overview stats: papers={}({}%), citations={}({}%), "
                + "paperGrowth={}({}%), authors={}({}%)",
                papersTracked, papersGrowthRate,
                totalCitations, citationsGrowthRate,
                paperGrowth, paperGrowthRate,
                totalAuthors, authorsGrowthRate);

        return response;
    }

    @Override
    public TotalPapersResponse getTotalPapers() {
        long count = researchPaperRepository.count();
        log.info("Total papers in system: {}", count);
        return TotalPapersResponse.builder()
                .totalPapers(count)
                .build();
    }
}