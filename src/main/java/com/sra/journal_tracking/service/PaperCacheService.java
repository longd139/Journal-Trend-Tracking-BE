package com.sra.journal_tracking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sra.journal_tracking.dto.paper.PaperDetailResponseDTO;
import com.sra.journal_tracking.entity.jpa.PaperCache;
import com.sra.journal_tracking.repository.jpa.PaperCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * Simple cache layer for papers fetched from OpenAlex.
 * Saves to PAPER_CACHE table with 7-day TTL.
 * No FK, no validation — just store and retrieve.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperCacheService {

    private static final int CACHE_TTL_DAYS = 7;

    private final PaperCacheRepository paperCacheRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void save(PaperDetailResponseDTO dto, String openAlexWorkUrl) {
        try {
            PaperCache cache = PaperCache.builder()
                    .paperId(dto.getPaperId())
                    .title(dto.getTitle())
                    .abstractText(dto.getAbstractText())
                    .doi(dto.getDoi())
                    .pubYear(dto.getPubYear())
                    .citationCount(dto.getCitationCount())
                    .journalName(dto.getJournalName())
                    .sourceUrl(dto.getSourceUrl())
                    .openAlexWorkId(openAlexWorkUrl)
                    .dataJson(objectMapper.writeValueAsString(dto))
                    .updatedAt(LocalDateTime.now())
                    .build();
            paperCacheRepository.save(cache);
            log.debug("PaperCache saved: {}", dto.getPaperId());
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize paper DTO for cache: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Optional<PaperDetailResponseDTO> get(UUID paperId) {
        return paperCacheRepository.findById(paperId)
                .filter(cache -> !isExpired(cache))
                .map(cache -> {
                    try {
                        if (cache.getDataJson() != null) {
                            return objectMapper.readValue(cache.getDataJson(), PaperDetailResponseDTO.class);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to deserialize PaperCache {}: {}", paperId, e.getMessage());
                    }
                    // Fallback: build from columns
                    return PaperDetailResponseDTO.builder()
                            .paperId(cache.getPaperId())
                            .title(cache.getTitle())
                            .abstractText(cache.getAbstractText())
                            .doi(cache.getDoi())
                            .pubYear(cache.getPubYear())
                            .citationCount(cache.getCitationCount())
                            .journalName(cache.getJournalName())
                            .sourceUrl(cache.getSourceUrl())
                            .build();
                });
    }

    private boolean isExpired(PaperCache cache) {
        return cache.getUpdatedAt() != null
                && cache.getUpdatedAt().isBefore(LocalDateTime.now().minus(CACHE_TTL_DAYS, ChronoUnit.DAYS));
    }
}
