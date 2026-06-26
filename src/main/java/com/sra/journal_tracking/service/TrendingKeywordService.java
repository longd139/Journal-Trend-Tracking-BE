package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.search.TrendingKeywordResponse;
import com.sra.journal_tracking.entity.jpa.TrendingTopic;
import com.sra.journal_tracking.repository.jpa.TrendingTopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Reads trending keywords from the TRENDING_TOPIC database table.
 * The table is refreshed every 12 hours by {@link TrendingTopicSyncService}
 * which fetches data from OpenAlex.
 *
 * When the table is empty (e.g., first deploy before the first sync runs),
 * falls back to a curated default list so the zero-state never appears blank.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrendingKeywordService {

    private final TrendingTopicRepository trendingTopicRepository;

    private static final List<TrendingKeywordResponse> DEFAULT_KEYWORDS = List.of(
            TrendingKeywordResponse.builder().keywordText("Artificial Intelligence").paperCount(0).source("default").displayOrder(1).build(),
            TrendingKeywordResponse.builder().keywordText("Climate Change").paperCount(0).source("default").displayOrder(2).build(),
            TrendingKeywordResponse.builder().keywordText("Machine Learning").paperCount(0).source("default").displayOrder(3).build(),
            TrendingKeywordResponse.builder().keywordText("Quantum Computing").paperCount(0).source("default").displayOrder(4).build(),
            TrendingKeywordResponse.builder().keywordText("Bioinformatics").paperCount(0).source("default").displayOrder(5).build()
    );

    /**
     * Returns trending keywords from the DB cache, or a default fallback list
     * if no data has been synced yet.
     *
     * @param limit max number of keywords to return
     */
    public List<TrendingKeywordResponse> getTrendingKeywords(int limit) {
        List<TrendingTopic> topics = trendingTopicRepository.findAllByOrderByDisplayOrderAsc();

        if (topics.isEmpty()) {
            log.debug("No trending topics in DB yet — returning default fallback list");
            return DEFAULT_KEYWORDS.stream().limit(limit).toList();
        }

        return topics.stream()
                .limit(limit)
                .map(t -> TrendingKeywordResponse.builder()
                        .keywordText(t.getTopicName())
                        .paperCount(t.getPaperCount())
                        .source(t.getSource())
                        .displayOrder(t.getDisplayOrder())
                        .build())
                .toList();
    }
}
