package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.paper.KeywordDTO;
import com.sra.journal_tracking.dto.paper.PaperDetailResponseDTO;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.dto.trend.ResearchTopicResponse;
import com.sra.journal_tracking.entity.jpa.ResearchPaper;
import com.sra.journal_tracking.entity.jpa.ResearchTopic;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.repository.jpa.ResearchTopicRepository;
import com.sra.journal_tracking.service.GraphService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Public REST endpoints for browsing research topics — trending topics,
 * topic detail with linked papers, and topics filtered by research field.
 * No authentication required (under /api/public/).
 */
@Slf4j
@RestController
@RequestMapping("/api/public/research-topics")
@RequiredArgsConstructor
public class ResearchTopicController {

    private final ResearchTopicRepository researchTopicRepository;
    private final GraphService graphService;
    private final ResearchPaperRepository researchPaperRepository;

    @Operation(
            summary = "Get top trending research topics",
            description = "Returns paginated trending topics ordered by trend score descending (hottest first). "
                        + "Used for the trending topics discovery sidebar and Analytics page."
    )
    @GetMapping("/trending")
    public ResponseEntity<AppResponse<List<ResearchTopicResponse>>> getTrendingTopics(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (page < 0) page = 0;
        if (size < 1) size = 1;
        if (size > 50) size = 50;

        log.info("Fetching trending topics: page={}, size={}", page, size);

        List<ResearchTopic> topics = researchTopicRepository
                .findByIsTrendingTrueOrderByTrendScoreDesc(PageRequest.of(page, size));

        List<ResearchTopicResponse> response = topics.stream()
                .map(this::mapToResponse)
                .toList();

        return ResponseEntity.ok(AppResponse.success("Trending topics retrieved", response));
    }

    @Operation(
            summary = "Get research topic detail with linked papers",
            description = "Returns a single research topic's metadata plus the top 10 most-cited "
                        + "papers linked to this topic, discovered via Neo4j keyword graph traversal."
    )
    @GetMapping("/{id}")
    public ResponseEntity<AppResponse<ResearchTopicResponse>> getTopicDetail(
            @PathVariable UUID id) {

        log.info("Fetching research topic detail: id={}", id);

        ResearchTopic topic = researchTopicRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Research topic not found: " + id));

        ResearchTopicResponse response = mapToResponse(topic);

        return ResponseEntity.ok(AppResponse.success("Topic detail retrieved", response));
    }

    @Operation(
            summary = "Get linked papers for a research topic",
            description = "Returns the top 10 most-cited papers associated with this research topic. "
                        + "Uses the topic name as a keyword to discover papers via Neo4j graph, "
                        + "then loads full paper data from SQL ordered by citation count."
    )
    @GetMapping("/{id}/papers")
    public ResponseEntity<AppResponse<List<PaperDetailResponseDTO>>> getTopicPapers(
            @PathVariable UUID id) {

        log.info("Fetching papers for research topic: id={}", id);

        ResearchTopic topic = researchTopicRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Research topic not found: " + id));

        // Use topic name as keyword → Neo4j graph traversal → paper IDs
        List<String> paperIdStrings = graphService.searchPapersByKeyword(topic.getTopicName());
        List<UUID> paperIds = paperIdStrings.stream()
                .map(UUID::fromString)
                .toList();

        List<PaperDetailResponseDTO> papers = new ArrayList<>();
        if (!paperIds.isEmpty()) {
            List<ResearchPaper> topPapers = researchPaperRepository
                    .findTopCitedByIds(paperIds, PageRequest.of(0, 10));

            for (ResearchPaper paper : topPapers) {
                List<KeywordDTO> kwList = new ArrayList<>();
                if (paper.getKeywords() != null) {
                    for (var pk : paper.getKeywords()) {
                        kwList.add(KeywordDTO.builder()
                                .keywordText(pk.getKeyword().getKeywordText())
                                .relevanceScore(pk.getRelevanceScore())
                                .build());
                    }
                }

                papers.add(PaperDetailResponseDTO.builder()
                        .paperId(paper.getPaperId())
                        .title(paper.getTitle())
                        .abstractText(paper.getAbstractText())
                        .doi(paper.getDoi())
                        .pubYear(paper.getPubYear())
                        .citationCount(paper.getCitationCount())
                        .isOpenAccess(paper.getIsOpenAccess())
                        .journalName(paper.getJournal() != null ? paper.getJournal().getJournalName() : null)
                        .sourceUrl(paper.getDoi() != null ? "https://doi.org/" + paper.getDoi() : null)
                        .pdfUrl(paper.getPdfUrl())
                        .keywords(kwList)
                        .build());
            }
        }

        return ResponseEntity.ok(AppResponse.success("Topic papers retrieved", papers));
    }

    @Operation(
            summary = "Get research topics by field",
            description = "Returns research topics within a specific research field, "
                        + "ordered by trend score descending."
    )
    @GetMapping("/by-field/{fieldId}")
    public ResponseEntity<AppResponse<List<ResearchTopicResponse>>> getTopicsByField(
            @PathVariable UUID fieldId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (page < 0) page = 0;
        if (size < 1) size = 1;
        if (size > 50) size = 50;

        log.info("Fetching research topics by field: fieldId={}, page={}, size={}", fieldId, page, size);

        List<ResearchTopic> topics = researchTopicRepository
                .findByField_FieldIdOrderByTrendScoreDesc(fieldId, PageRequest.of(page, size));

        List<ResearchTopicResponse> response = topics.stream()
                .map(this::mapToResponse)
                .toList();

        return ResponseEntity.ok(AppResponse.success("Topics by field retrieved", response));
    }

    /**
     * Map ResearchTopic entity → ResearchTopicResponse DTO.
     */
    private ResearchTopicResponse mapToResponse(ResearchTopic topic) {
        return ResearchTopicResponse.builder()
                .topicId(topic.getTopicId())
                .topicName(topic.getTopicName())
                .fieldId(topic.getField() != null ? topic.getField().getFieldId() : null)
                .fieldName(topic.getField() != null ? topic.getField().getFieldName() : null)
                .isTrending(topic.getIsTrending())
                .trendScore(topic.getTrendScore())
                .paperCount(topic.getPaperCount())
                .updatedAt(topic.getUpdatedAt())
                .build();
    }
}
