package com.sra.journal_tracking.service.impl;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sra.journal_tracking.dto.overview.UserOverviewResponse;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.entity.jpa.UserUsage;
import com.sra.journal_tracking.repository.jpa.BookmarkRepository;
import com.sra.journal_tracking.repository.jpa.KeywordRepository;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.repository.jpa.SystemConfigRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import com.sra.journal_tracking.repository.jpa.UserUsageRepository;
import com.sra.journal_tracking.service.UserOverviewService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserOverviewServiceImpl implements UserOverviewService {

    private final UserRepository userRepository;
    private final BookmarkRepository bookmarkRepository;
    private final UserUsageRepository userUsageRepository;
    private final KeywordRepository keywordRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final ResearchPaperRepository researchPaperRepository;

    @Override
    public UserOverviewResponse getUserOverview(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));

        String userRole = user.getRole().getRoleName();
        String currentMonth = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));

        // Card 1: Total papers in the system (replaces savedPapers)
        long totalPapers = researchPaperRepository.count();

        // Card 2: Papers viewed this month
        long papersViewed = userUsageRepository
                .findByUser_UserIdAndUsageMonth(user.getUserId(), currentMonth)
                .map(UserUsage::getViewCount)
                .map(Long::valueOf)
                .orElse(0L);

        // Card 3: Searches remaining (only for ACADEMIC_USER)
        Integer searchesRemaining = null;
        Integer monthlySearchLimit = null;

        if ("ACADEMIC_USER".equalsIgnoreCase(userRole)) {
            int limit = systemConfigRepository.findByConfigKey("academic_monthly_search_limit")
                    .map(cfg -> Integer.parseInt(cfg.getConfigValue()))
                    .orElse(30); // default fallback

            int usedSearches = userUsageRepository
                    .findByUser_UserIdAndUsageMonth(user.getUserId(), currentMonth)
                    .map(UserUsage::getSearchCount)
                    .orElse(0);

            searchesRemaining = Math.max(0, limit - usedSearches);
            monthlySearchLimit = limit;
        }

        // Card 4: Total keywords indexed in the system
        long totalKeywords = keywordRepository.count();

        log.info("User overview for {} ({}): totalPapers={}, papersViewed={}, searchesRemaining={}, totalKeywords={}",
                userEmail, userRole, totalPapers, papersViewed, searchesRemaining, totalKeywords);

        return UserOverviewResponse.builder()
                .totalPapers(totalPapers)
                .papersViewed(papersViewed)
                .searchesRemaining(searchesRemaining)
                .monthlySearchLimit(monthlySearchLimit)
                .totalKeywords(totalKeywords)
                .build();
    }
}
