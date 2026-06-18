package com.sra.journal_tracking.service;

import com.sra.journal_tracking.constants.KeywordConstants;
import com.sra.journal_tracking.dto.paper.AuthorDTO;
import com.sra.journal_tracking.dto.paper.KeywordDTO;
import com.sra.journal_tracking.dto.paper.PaperDetailResponseDTO;
import com.sra.journal_tracking.dto.paper.PaperSearchResultDTO;
import com.sra.journal_tracking.entity.jpa.ResearchPaper;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrator cho flow tìm kiếm paper theo keyword:
 * Neo4j (graph cache) → SQL (full data) → OpenAlex API (external fallback)
 *
 * Flow:
 * 1. Query Neo4j graph để tìm paper IDs liên quan đến keyword
 * 2. Nếu có → lấy full data từ SQL Server
 * 3. Nếu không → gọi OpenAlex API → lưu SQL + Neo4j → trả kết quả
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperSearchOrchestrator {

    private final GraphService graphService;
    private final DataSyncService dataSyncService;
    private final SearchKeywordService searchKeywordService;
    private final ResearchPaperRepository researchPaperRepository;
    private final UserRepository userRepository;

    /**
     * Tìm paper theo keyword, ưu tiên Neo4j trước, fallback ra OpenAlex.
     *
     * @param keyword   từ khóa tìm kiếm
     * @param userEmail email user hiện tại (để check usage limit)
     * @return PaperSearchResultDTO chứa danh sách papers
     */
    @Transactional
    public PaperSearchResultDTO searchByKeyword(String keyword, String userEmail) {
        String trimmedKeyword = keyword.trim();
        if (trimmedKeyword.isEmpty()) {
            throw new IllegalArgumentException("Keyword cannot be empty");
        }

        // Truncate keyword to max allowed length
        if (trimmedKeyword.length() > KeywordConstants.MAX_KEYWORD_LENGTH) {
            log.warn("Keyword truncated from {} chars to {} chars", trimmedKeyword.length(), KeywordConstants.MAX_KEYWORD_LENGTH);
            trimmedKeyword = trimmedKeyword.substring(0, KeywordConstants.MAX_KEYWORD_LENGTH);
        }

        log.info("Graph search: keyword='{}', user='{}'", trimmedKeyword, userEmail);

        // Record the search keyword for hot-keywords tracking (non-blocking)
        try {
            searchKeywordService.recordSearch(trimmedKeyword);
        } catch (Exception e) {
            log.warn("Failed to record search keyword '{}': {}", trimmedKeyword, e.getMessage());
        }

        // ── BƯỚC 1: Tìm trong Neo4j ──────────────────────────
        List<String> neo4jPaperIds = graphService.searchPapersByKeyword(trimmedKeyword);

        if (!neo4jPaperIds.isEmpty()) {
            log.info("Neo4j HIT: {} papers found for '{}'", neo4jPaperIds.size(), trimmedKeyword);

            // Dùng findByIdsWithDetails (có JOIN FETCH) thay vì findAllById (N+1)
            List<ResearchPaper> sqlPapers = fetchPapersFromSqlWithDetails(neo4jPaperIds);

            if (!sqlPapers.isEmpty()) {
                log.info("SQL MATCH: {} papers valid in SQL", sqlPapers.size());
                return mapToSearchResultDTO(sqlPapers);
            }

            // Neo4j có nhưng SQL không có → data stale → dọn dẹp + fallback
            log.warn("Neo4j STALE: {} IDs found in Neo4j but 0 in SQL. Cleaning up & falling back.",
                    neo4jPaperIds.size());
            graphService.deleteStalePapers(neo4jPaperIds);
        } else {
            log.info("Neo4j MISS for '{}'", trimmedKeyword);
        }

        // ── BƯỚC 2: Fallback — dùng regular search (đã có cache + tối ưu) rồi sync Neo4j ──
        log.info("Falling back to regular search for '{}'", trimmedKeyword);
        return fallbackToRegularSearch(trimmedKeyword);
    }

    // ============================================
    //  PRIVATE HELPERS
    // ============================================

    /**
     * Lấy papers từ SQL theo danh sách ID từ Neo4j, dùng JOIN FETCH để load authors.
     */
    private List<ResearchPaper> fetchPapersFromSqlWithDetails(List<String> paperIdStrings) {
        List<UUID> uuids = paperIdStrings.stream()
                .map(UUID::fromString)
                .collect(Collectors.toList());

        if (uuids.isEmpty()) {
            return List.of();
        }

        // Dùng findByIdsWithDetails (JOIN FETCH) thay vì findAllById (N+1)
        List<ResearchPaper> papers = researchPaperRepository.findByIdsWithDetails(uuids);

        // Sắp xếp theo thứ tự từ Neo4j
        Map<UUID, ResearchPaper> paperMap = papers.stream()
                .collect(Collectors.toMap(ResearchPaper::getPaperId, java.util.function.Function.identity()));
        return uuids.stream()
                .map(paperMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Fallback khi Neo4j miss: dùng regular search (đã có cache + simple query).
     * Nếu SQL không có → gọi OpenAlex sync → trả kết quả.
     */
    private PaperSearchResultDTO fallbackToRegularSearch(String keyword) {
        // Thử search trong SQL trước (dùng cache nếu có từ regular search trước đó)
        List<UUID> paperIds = researchPaperRepository
                .searchPaperIdsByKeyword(keyword, PageRequest.of(0, 20))
                .getContent();

        if (!paperIds.isEmpty()) {
            List<ResearchPaper> papers = researchPaperRepository.findByIdsWithDetails(paperIds);
            log.info("Fallback SQL HIT: {} papers for '{}'", papers.size(), keyword);
            // Sync vào Neo4j cho lần sau
            syncToNeo4j(papers, keyword);
            return mapToSearchResultDTO(papers);
        }

        // SQL cũng không có → gọi OpenAlex
        log.info("SQL MISS for '{}', calling OpenAlex API", keyword);
        try {
            dataSyncService.syncFromOpenAlex(keyword, 20);
        } catch (Exception e) {
            log.error("OpenAlex sync failed for '{}': {}", keyword, e.getMessage());
            return buildEmptyResult();
        }

        // Query papers vừa sync về
        List<UUID> syncedIds = researchPaperRepository
                .searchPaperIdsByKeyword(keyword, PageRequest.of(0, 20))
                .getContent();

        if (syncedIds.isEmpty()) {
            return buildEmptyResult();
        }

        List<ResearchPaper> papers = researchPaperRepository.findByIdsWithDetails(syncedIds);
        log.info("OpenAlex sync + SQL query: {} papers for '{}'", papers.size(), keyword);

        syncToNeo4j(papers, keyword);
        return mapToSearchResultDTO(papers);
    }

    private void syncToNeo4j(List<ResearchPaper> papers, String keyword) {
        for (ResearchPaper paper : papers) {
            try {
                List<String> keywords = extractKeywords(paper);
                graphService.savePaperWithKeywords(
                        paper.getPaperId().toString(),
                        paper.getTitle(),
                        paper.getDoi(),
                        paper.getPubYear() != null ? paper.getPubYear().intValue() : null,
                        keywords.isEmpty() ? List.of(keyword) : keywords
                );
            } catch (Exception e) {
                log.warn("Failed to save paper {} to Neo4j: {}", paper.getPaperId(), e.getMessage());
            }
        }
    }

    private PaperSearchResultDTO mapToSearchResultDTO(List<ResearchPaper> papers) {
        List<PaperDetailResponseDTO> dtos = papers.stream()
                .map(this::mapToDetailDTO)
                .collect(Collectors.toList());

        return PaperSearchResultDTO.builder()
                .papers(dtos)
                .totalElements((long) dtos.size())
                .totalPages(1)
                .currentPage(0)
                .pageSize(dtos.size())
                .hasNext(false)
                .hasPrev(false)
                .build();
    }

    private PaperDetailResponseDTO mapToDetailDTO(ResearchPaper paper) {
        List<AuthorDTO> authors = paper.getAuthors() != null ? paper.getAuthors().stream()
                .map(pa -> AuthorDTO.builder()
                        .fullName(pa.getAuthor().getFullName())
                        .affiliation(pa.getAuthor().getAffiliation())
                        .hIndex(pa.getAuthor().getHIndex())
                        .totalCitations(pa.getAuthor().getTotalCitations())
                        .authorOrder(pa.getAuthorOrder())
                        .build())
                .collect(Collectors.toList()) : new ArrayList<>();

        List<KeywordDTO> keywords = paper.getKeywords() != null ? paper.getKeywords().stream()
                .map(pk -> KeywordDTO.builder()
                        .keywordText(pk.getKeyword().getKeywordText())
                        .relevanceScore(pk.getRelevanceScore())
                        .build())
                .collect(Collectors.toList()) : new ArrayList<>();

        String sourceUrl = paper.getDoi() != null ? "https://doi.org/" + paper.getDoi() : null;
        Boolean pdfAvailable = Boolean.TRUE.equals(paper.getIsOpenAccess()) || paper.getDoi() != null;

        return PaperDetailResponseDTO.builder()
                .paperId(paper.getPaperId())
                .title(paper.getTitle())
                .abstractText(paper.getAbstractText())
                .doi(paper.getDoi())
                .pubYear(paper.getPubYear())
                .pubDate(paper.getPubDate())
                .citationCount(paper.getCitationCount())
                .isOpenAccess(paper.getIsOpenAccess())
                .journalName(paper.getJournal() != null ? paper.getJournal().getJournalName() : null)
                .journalId(paper.getJournal() != null ? paper.getJournal().getJournalId() : null)
                .fieldName(paper.getField() != null ? paper.getField().getFieldName() : null)
                .fieldId(paper.getField() != null ? paper.getField().getFieldId() : null)
                .authors(authors)
                .keywords(keywords)
                .sourceUrl(sourceUrl)
                .pdfAvailable(pdfAvailable)
                .downloadUrl(sourceUrl)
                .rating(0.0)
                .downloadCount(0)
                .commentCount(0)
                .createdAt(paper.getCreatedAt())
                .build();
    }

    private List<String> extractKeywords(ResearchPaper paper) {
        if (paper.getKeywords() == null || paper.getKeywords().isEmpty()) {
            return List.of();
        }
        return paper.getKeywords().stream()
                .map(pk -> pk.getKeyword().getKeywordText())
                .filter(k -> k != null && !k.isBlank())
                .collect(Collectors.toList());
    }

    private PaperSearchResultDTO buildEmptyResult() {
        return PaperSearchResultDTO.builder()
                .papers(List.of())
                .totalElements(0L)
                .totalPages(0)
                .currentPage(0)
                .pageSize(0)
                .hasNext(false)
                .hasPrev(false)
                .build();
    }
}
