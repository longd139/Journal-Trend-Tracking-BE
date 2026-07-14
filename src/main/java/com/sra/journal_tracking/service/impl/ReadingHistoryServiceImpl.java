package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.dto.history.ReadingHistoryResponse;
import com.sra.journal_tracking.entity.jpa.ApiSource;
import com.sra.journal_tracking.entity.jpa.ReadingHistory;
import com.sra.journal_tracking.entity.jpa.ResearchPaper;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.repository.jpa.ApiSourceRepository;
import com.sra.journal_tracking.repository.jpa.ReadingHistoryRepository;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import com.sra.journal_tracking.service.ReadingHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
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
    private final ApiSourceRepository apiSourceRepository;

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
    @Transactional
    public void recordView(String userEmail, UUID paperId, String title, String doi, Integer pubYear) {
        User user = userRepository.findByEmail(userEmail).orElse(null);
        if (user == null) {
            log.warn("User not found for reading history: {}", userEmail);
            return;
        }

        // Don't count duplicate views — one view per user per paper
        if (readingHistoryRepository.existsByUser_UserIdAndPaper_PaperId(user.getUserId(), paperId)) {
            log.debug("User {} already viewed paper {} — skipping duplicate view", userEmail, paperId);
            return;
        }

        // Find or create the paper — papers from OpenAlex/cache may not be in DB yet
        ResearchPaper paper = researchPaperRepository.findById(paperId).orElse(null);
        if (paper == null) {
            // Must set a valid ApiSource — SourceID is NOT NULL
            ApiSource defaultSource = apiSourceRepository.findBySourceNameIgnoreCase("openalex")
                    .orElseGet(() -> apiSourceRepository.findAll().stream().findFirst().orElse(null));
            if (defaultSource == null) {
                log.warn("No ApiSource found — cannot create paper stub for reading history");
                return;
            }
            paper = ResearchPaper.builder()
                    .paperId(paperId)
                    .source(defaultSource)
                    .title(title != null ? title : "Untitled")
                    .doi(doi)
                    .pubYear(pubYear != null ? pubYear.shortValue() : null)
                    .citationCount(0)
                    .isOpenAccess(false)
                    .build();
            researchPaperRepository.insertPaperStub(paperId, defaultSource.getSourceId(),
                    paper.getTitle(), paper.getDoi(), paper.getPubYear());
            log.info("Created paper stub for reading history: paperId={}", paperId);
        }

        ReadingHistory history = ReadingHistory.builder()
                .user(user)
                .paper(paper)
                .viewedAt(LocalDateTime.now())
                .build();

        readingHistoryRepository.save(history);
        log.info("Recorded view: user={}, paper={}", userEmail, paperId);

        // Prune old entries if over the limit
        long count = readingHistoryRepository.countDistinctPapersByUser(user.getUserId());
        if (count > MAX_HISTORY_PER_USER) {
            int deleted = readingHistoryRepository.deleteOldEntries(user.getUserId(), MAX_HISTORY_PER_USER);
            if (deleted > 0) {
                log.info("Pruned {} old reading history entries for user {}", deleted, userEmail);
            }
        }
    }
}

