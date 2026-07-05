package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.dto.history.ReadingHistoryResponse;
import com.sra.journal_tracking.entity.jpa.ReadingHistory;
import com.sra.journal_tracking.entity.jpa.ResearchPaper;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.repository.jpa.ReadingHistoryRepository;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import com.sra.journal_tracking.service.ReadingHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
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
public class ReadingHistoryServiceImpl implements ReadingHistoryService {

    private static final int MAX_HISTORY_PER_USER = 200;

    private final ReadingHistoryRepository readingHistoryRepository;
    private final UserRepository userRepository;
    private final ResearchPaperRepository researchPaperRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ReadingHistoryResponse> getRecentViews(String userEmail, int limit) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));

        // Fetch more to allow for deduplication
        int fetchSize = Math.min(limit * 3, 100);
        List<ReadingHistory> raw = readingHistoryRepository
                .findByUser_UserIdOrderByViewedAtDesc(user.getUserId(), PageRequest.of(0, fetchSize));

        // Deduplicate by paperId — keep the most recent view per paper
        LinkedHashMap<UUID, ReadingHistoryResponse> deduped = new LinkedHashMap<>();
        for (ReadingHistory rh : raw) {
            ResearchPaper paper = rh.getPaper();
            if (paper == null) continue;

            UUID paperId = paper.getPaperId();
            deduped.putIfAbsent(paperId, ReadingHistoryResponse.builder()
                    .readingHistoryId(rh.getReadingHistoryId())
                    .paperId(paperId)
                    .paperTitle(paper.getTitle())
                    .pubYear(paper.getPubYear() != null ? (int) paper.getPubYear() : null)
                    .journalName(paper.getJournal() != null ? paper.getJournal().getJournalName() : null)
                    .doi(paper.getDoi())
                    .citationCount(paper.getCitationCount())
                    .isOpenAccess(paper.getIsOpenAccess())
                    .viewedAt(rh.getViewedAt())
                    .build());

            if (deduped.size() >= limit) break;
        }

        return List.copyOf(deduped.values());
    }

    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordView(String userEmail, UUID paperId) {
        try {
            User user = userRepository.findByEmail(userEmail).orElse(null);
            ResearchPaper paper = researchPaperRepository.findById(paperId).orElse(null);
            if (user == null || paper == null) return;

            ReadingHistory history = ReadingHistory.builder()
                    .user(user)
                    .paper(paper)
                    .viewedAt(LocalDateTime.now())
                    .build();

            readingHistoryRepository.save(history);
            log.debug("Recorded view: user={}, paper={}", userEmail, paperId);

            // Prune old entries if over the limit
            long count = readingHistoryRepository.countDistinctPapersByUser(user.getUserId());
            if (count > MAX_HISTORY_PER_USER) {
                int deleted = readingHistoryRepository.deleteOldEntries(user.getUserId(), MAX_HISTORY_PER_USER);
                if (deleted > 0) {
                    log.debug("Pruned {} old reading history entries for user {}", deleted, userEmail);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to record view for user={}, paper={}: {}", userEmail, paperId, e.getMessage());
        }
    }
}
