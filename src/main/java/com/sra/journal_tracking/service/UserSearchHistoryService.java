package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.search.RecentSearchResponse;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.entity.jpa.UserSearchHistory;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import com.sra.journal_tracking.repository.jpa.UserSearchHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSearchHistoryService {

    private final UserSearchHistoryRepository userSearchHistoryRepository;
    private final UserRepository userRepository;

    /**
     * Record a user's search for the zero-state recent-searches feature.
     * Runs in a new transaction to commit independently from the calling search transaction.
     *
     * @param userEmail  the authenticated user's email
     * @param searchText the raw search text entered
     * @param searchType KEYWORD, AUTHOR, or JOURNAL
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSearch(String userEmail, String searchText, String searchType) {
        if (searchText == null || searchText.trim().isEmpty()) {
            return;
        }
        String trimmed = searchText.trim();
        // Truncate to 500 chars to match DB column
        if (trimmed.length() > 500) {
            trimmed = trimmed.substring(0, 500);
        }

        try {
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));

            UserSearchHistory history = UserSearchHistory.builder()
                    .user(user)
                    .searchText(trimmed)
                    .searchType(searchType)
                    .searchedAt(LocalDateTime.now())
                    .build();

            userSearchHistoryRepository.save(history);
            log.debug("Recorded {} search '{}' for user {}", searchType, trimmed, userEmail);
        } catch (Exception e) {
            log.warn("Failed to record {} search '{}' for user {}: {}",
                    searchType, trimmed, userEmail, e.getMessage());
        }
    }

    /**
     * Get the N most recent distinct searches for a user.
     * Deduplication: same (searchText + searchType) pair → keeps the most recent.
     *
     * @param userEmail the authenticated user's email
     * @param limit     max number of recent searches to return (3-5 recommended)
     * @return deduplicated list ordered by searchedAt descending
     */
    public List<RecentSearchResponse> getRecentSearches(String userEmail, int limit) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));

        // Fetch more than needed to account for deduplication
        int fetchSize = limit * 3;
        List<UserSearchHistory> raw = userSearchHistoryRepository
                .findByUser_UserIdOrderBySearchedAtDesc(user.getUserId(), PageRequest.of(0, fetchSize));

        // Deduplicate: same (searchText + searchType) → keep first (most recent)
        LinkedHashMap<String, RecentSearchResponse> deduped = new LinkedHashMap<>();
        for (UserSearchHistory h : raw) {
            String key = h.getSearchType() + "::" + h.getSearchText().toLowerCase().trim();
            deduped.putIfAbsent(key, RecentSearchResponse.builder()
                    .searchText(h.getSearchText())
                    .searchType(h.getSearchType())
                    .searchedAt(h.getSearchedAt())
                    .build());
            if (deduped.size() >= limit) {
                break;
            }
        }

        return List.copyOf(deduped.values());
    }
}
