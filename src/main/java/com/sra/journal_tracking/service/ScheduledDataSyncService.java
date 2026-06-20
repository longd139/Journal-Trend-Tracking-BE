package com.sra.journal_tracking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledDataSyncService {
    private static final int SCHEDULED_TOPIC_SYNC_LIMIT = 25;
    private static final List<String> SCHEDULED_OPENALEX_TOPICS = List.of(
            "artificial intelligence",
            "machine learning",
            "deep learning",
            "data science",
            "statistics",
            "computer vision",
            "natural language processing",
            "healthcare",
            "education technology",
            "brain research",
            "climate change",
            "renewable energy"
    );

    private final DataSyncService dataSyncService;

    @Scheduled(cron = "0 0 2 * * ?")
    public void syncPopularTopics() {
        for (String topic : SCHEDULED_OPENALEX_TOPICS) {
            try {
                log.info("Scheduled OpenAlex sync started for topic '{}'", topic);
                dataSyncService.syncFromOpenAlex(topic, SCHEDULED_TOPIC_SYNC_LIMIT);
            } catch (Exception e) {
                log.warn("Scheduled OpenAlex sync failed for '{}': {}", topic, e.getMessage());
            }
        }
    }
}
