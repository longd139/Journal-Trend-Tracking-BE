package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.paper.AuthorDTO;
import com.sra.journal_tracking.dto.paper.KeywordDTO;
import com.sra.journal_tracking.dto.paper.PaperDetailResponseDTO;
import com.sra.journal_tracking.dto.sync.OpenAlexResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAlexFallbackSearchService {
    private static final String OPEN_ALEX_BASE_URL = "https://api.openalex.org";
    private static final int RECENT_PUBLICATION_YEAR_WINDOW = 3;
    private static final int MAX_AUTHORS = 5;
    private static final int MAX_KEYWORDS = 8;

    private final RestTemplate restTemplate;
    private final KeywordExpansionService keywordExpansionService;
    private final PaperCacheService paperCacheService;

    // In-memory map: stablePreviewId UUID → OpenAlex work URL. No DB needed.
    private final ConcurrentHashMap<UUID, String> paperIdToWorkUrl = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Value("${app.openalex-api-key:}")
    private String openalexApiKey;

    @org.springframework.beans.factory.annotation.Value("${app.openalex-email:}")
    private String openalexEmail;

    /**
     * Fetch a single paper by local UUID — looks up OpenAlex work URL from in-memory cache.
     * Used by getPaperDetails when paper is not in local DB.
     */
    public PaperDetailResponseDTO getPaperByUuid(UUID paperId) {
        String workUrl = paperIdToWorkUrl.get(paperId);
        if (workUrl != null) {
            return getPaperByOpenAlexId(workUrl);
        }
        return null;
    }

    /**
     * Fetch a single paper from OpenAlex by its work ID URL (e.g. https://openalex.org/W123456).
     * No DB interaction — pure OpenAlex API call.
     */
    public PaperDetailResponseDTO getPaperByOpenAlexId(String openAlexWorkUrl) {
        String shortId = extractShortId(openAlexWorkUrl);
        if (shortId == null) return null;

        String url = withApiKey(UriComponentsBuilder
                .fromHttpUrl(OPEN_ALEX_BASE_URL + "/works/" + shortId)
                .queryParam("select", "id,doi,title,display_name,publication_year,publication_date,"
                        + "cited_by_count,abstract_inverted_index,open_access,"
                        + "primary_location,best_oa_location,topics,keywords,authorships"))
                .build().encode().toUriString();

        try {
            OpenAlexResponseDTO.OpenAlexWorkDTO work = restTemplate.getForObject(url, OpenAlexResponseDTO.OpenAlexWorkDTO.class);
            if (work == null) return null;
            String abstractText = rebuildAbstract(work.getAbstractInvertedIndex());
            return mapToPaper(work, abstractText);
        } catch (RestClientException e) {
            log.warn("OpenAlex fetch single work failed for '{}': {}", shortId, e.getMessage());
            return null;
        }
    }

    /**
     * Search top cited papers by keyword — all time, sorted by citation count.
     */
    public List<PaperDetailResponseDTO> searchTopCited(String query, int size) {
        String normalizedQuery = normalizeOpenAlexSearchQuery(query);
        if (normalizedQuery.isBlank()) return List.of();

        String url = withApiKey(UriComponentsBuilder
                .fromHttpUrl(OPEN_ALEX_BASE_URL + "/works")
                .queryParam("search", normalizedQuery)
                .queryParam("sort", "cited_by_count:desc")
                .queryParam("per-page", size)
                .queryParam("select", "id,doi,title,display_name,publication_year,publication_date,"
                        + "cited_by_count,abstract_inverted_index,open_access,"
                        + "primary_location,best_oa_location,topics,keywords,authorships"))
                .build().encode().toUriString();

        try {
            OpenAlexResponseDTO response = restTemplate.getForObject(url, OpenAlexResponseDTO.class);
            if (response == null || response.getResults() == null) return List.of();

            return response.getResults().stream()
                    .map(work -> new WorkWithAbstract(work, rebuildAbstract(work.getAbstractInvertedIndex())))
                    .map(work -> mapToPaper(work.work(), work.abstractText()))
                    .limit(size)
                    .collect(Collectors.toList());
        } catch (RestClientException e) {
            log.warn("OpenAlex top-cited search failed for '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    public List<PaperDetailResponseDTO> search(String query, int size) {
        String normalizedQuery = normalizeOpenAlexSearchQuery(query);
        if (normalizedQuery.isBlank()) return List.of();

        LocalDate today = LocalDate.now();
        int startYear = Year.now().getValue() - RECENT_PUBLICATION_YEAR_WINDOW + 1;
        int perPage = Math.min(25, Math.max(size * 4, 10));

        String url = withApiKey(UriComponentsBuilder
                .fromHttpUrl(OPEN_ALEX_BASE_URL + "/works")
                .queryParam("search", normalizedQuery)
                .queryParam("filter", "from_publication_date:" + startYear + "-01-01,to_publication_date:" + today)
                .queryParam("sort", "relevance_score:desc")
                .queryParam("per-page", perPage)
                .queryParam("select", "id,doi,title,display_name,publication_year,publication_date,cited_by_count,abstract_inverted_index,open_access,primary_location,best_oa_location,topics,keywords,authorships"))
                .build().encode().toUriString();

        try {
            OpenAlexResponseDTO response = restTemplate.getForObject(url, OpenAlexResponseDTO.class);
            if (response == null || response.getResults() == null) return List.of();

            return response.getResults().stream()
                    .filter(work -> isRecent(work, startYear, today))
                    .map(work -> new WorkWithAbstract(work, rebuildAbstract(work.getAbstractInvertedIndex())))
                    .filter(work -> isRelevant(work.work(), work.abstractText(), query))
                    .map(work -> mapToPaper(work.work(), work.abstractText()))
                    .limit(Math.max(1, size))
                    .collect(Collectors.toList());
        } catch (RestClientException e) {
            log.warn("OpenAlex fallback search failed for '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    private UriComponentsBuilder withApiKey(UriComponentsBuilder builder) {
        if (openalexApiKey != null && !openalexApiKey.isBlank()) {
            builder.queryParam("api_key", openalexApiKey);
        }
        return builder;
    }

    private String extractShortId(String openAlexUrl) {
        if (openAlexUrl == null) return null;
        int lastSlash = openAlexUrl.lastIndexOf('/');
        return lastSlash >= 0 ? openAlexUrl.substring(lastSlash + 1) : openAlexUrl;
    }

    // ── mapping, helpers identical to before ──

    private boolean isRecent(OpenAlexResponseDTO.OpenAlexWorkDTO work, int startYear, LocalDate today) {
        Short publicationYear = work.getPublicationYear();
        if (publicationYear == null || publicationYear < startYear || publicationYear > today.getYear()) return false;
        LocalDate publicationDate = parseDate(work.getPublicationDate());
        return publicationDate == null || !publicationDate.isAfter(today);
    }

    private boolean isRelevant(OpenAlexResponseDTO.OpenAlexWorkDTO work, String abstractText, String query) {
        List<String> tokens = keywordExpansionService.extractTokens(query);
        if (tokens.isEmpty()) return false;
        String normalizedQuery = keywordExpansionService.normalize(query);
        String strongText = buildStrongText(work);
        String fullText = normalize(strongText + " " + nullToEmpty(abstractText));
        if (containsTerm(strongText, normalizedQuery)) return true;
        if (tokens.size() == 1) return containsTokenVariant(strongText, tokens.get(0));
        return tokens.stream().allMatch(token -> containsTokenVariant(fullText, token))
                && tokens.stream().anyMatch(token -> containsTokenVariant(strongText, token));
    }

    private String buildStrongText(OpenAlexResponseDTO.OpenAlexWorkDTO work) {
        StringBuilder text = new StringBuilder();
        append(text, work.getTitle());
        append(text, work.getDisplayName());
        if (work.getTopics() != null) work.getTopics().forEach(topic -> {
            append(text, topic.getDisplayName());
            if (topic.getField() != null) append(text, topic.getField().getDisplayName());
            if (topic.getDomain() != null) append(text, topic.getDomain().getDisplayName());
        });
        if (work.getKeywords() != null) work.getKeywords().forEach(keyword -> append(text, keyword.getDisplayName()));
        return normalize(text.toString());
    }

    private PaperDetailResponseDTO mapToPaper(OpenAlexResponseDTO.OpenAlexWorkDTO work, String abstractText) {
        String doi = normalizeDoi(work.getDoi());
        String sourceUrl = doi != null ? "https://doi.org/" + doi : work.getId();
        UUID paperId = stablePreviewId(work);

        // Register work URL so paper detail can fetch from OpenAlex without DB
        if (work.getId() != null) {
            paperIdToWorkUrl.put(paperId, work.getId());
        }

        PaperDetailResponseDTO dto = PaperDetailResponseDTO.builder()
                .paperId(paperId)
                .title(firstNonBlank(work.getTitle(), work.getDisplayName()))
                .abstractText(abstractText)
                .doi(doi)
                .pubYear(work.getPublicationYear())
                .pubDate(parseDate(work.getPublicationDate()))
                .citationCount(work.getCitedByCount() != null ? work.getCitedByCount() : 0)
                .isOpenAccess(work.getOpenAccess() != null && Boolean.TRUE.equals(work.getOpenAccess().getIsOa()))
                .journalName(work.getPrimaryLocation() != null && work.getPrimaryLocation().getSource() != null
                        ? work.getPrimaryLocation().getSource().getDisplayName() : null)
                .journalId(null)
                .fieldName(resolveFieldName(work))
                .fieldId(null)
                .authors(mapAuthors(work))
                .keywords(mapKeywords(work))
                .sourceUrl(sourceUrl)
                .pdfAvailable(work.getOpenAccess() != null && Boolean.TRUE.equals(work.getOpenAccess().getIsOa()))
                .downloadUrl(sourceUrl)
                .pdfUrl(resolvePdfUrl(work))
                .rating(0.0d)
                .downloadCount(0)
                .commentCount(0)
                .createdAt(LocalDateTime.now())
                .build();

        // Save to paper cache for persistence across DB switches
        try {
            paperCacheService.save(dto, work.getId());
        } catch (Exception e) {
            log.debug("Failed to cache paper {}: {}", paperId, e.getMessage());
        }

        return dto;
    }

    private UUID stablePreviewId(OpenAlexResponseDTO.OpenAlexWorkDTO work) {
        String key = work.getId() != null ? work.getId() : work.getDoi();
        if (key == null) key = work.getTitle() != null ? work.getTitle() : "";
        return UUID.nameUUIDFromBytes(("openalex:" + key).getBytes(StandardCharsets.UTF_8));
    }

    private List<AuthorDTO> mapAuthors(OpenAlexResponseDTO.OpenAlexWorkDTO work) {
        if (work.getAuthorships() == null) return List.of();
        List<AuthorDTO> authors = new ArrayList<>();
        int order = 1;
        for (OpenAlexResponseDTO.Authorship authorship : work.getAuthorships()) {
            if (order > MAX_AUTHORS) break;
            String name = authorship.getAuthor() != null
                    ? firstNonBlank(authorship.getAuthor().getDisplayName(), authorship.getRawAuthorName())
                    : authorship.getRawAuthorName();
            if (name == null || name.isBlank()) continue;
            authors.add(AuthorDTO.builder().fullName(name)
                    .affiliation(authorship.getRawAffiliationStrings() != null && !authorship.getRawAffiliationStrings().isEmpty()
                            ? authorship.getRawAffiliationStrings().get(0) : null)
                    .totalCitations(0).authorOrder(order).hIndex(0).build());
            order++;
        }
        return authors;
    }

    private List<KeywordDTO> mapKeywords(OpenAlexResponseDTO.OpenAlexWorkDTO work) {
        List<KeywordDTO> keywords = new ArrayList<>();
        if (work.getKeywords() != null) {
            work.getKeywords().stream()
                    .filter(k -> k.getDisplayName() != null && !k.getDisplayName().isBlank())
                    .sorted(Comparator.comparing(k -> k.getScore() != null ? k.getScore() : 0.0d, Comparator.reverseOrder()))
                    .limit(MAX_KEYWORDS)
                    .forEach(k -> keywords.add(KeywordDTO.builder().keywordText(k.getDisplayName()).relevanceScore(k.getScore()).build()));
        }
        if (keywords.isEmpty() && work.getTopics() != null) {
            work.getTopics().stream()
                    .filter(t -> t.getDisplayName() != null && !t.getDisplayName().isBlank())
                    .limit(MAX_KEYWORDS)
                    .forEach(t -> keywords.add(KeywordDTO.builder().keywordText(t.getDisplayName()).relevanceScore(t.getScore()).build()));
        }
        return keywords;
    }

    private String resolveFieldName(OpenAlexResponseDTO.OpenAlexWorkDTO work) {
        if (work.getTopics() == null || work.getTopics().isEmpty()) return null;
        OpenAlexResponseDTO.Topic topic = work.getTopics().get(0);
        return topic.getField() != null ? topic.getField().getDisplayName() : null;
    }

    private String rebuildAbstract(Map<String, List<Integer>> invertedIndex) {
        if (invertedIndex == null || invertedIndex.isEmpty()) return null;
        List<String> words = new ArrayList<>();
        invertedIndex.forEach((word, positions) -> {
            if (positions != null) positions.forEach(pos -> {
                while (words.size() <= pos) words.add("");
                words.set(pos, word);
            });
        });
        String result = words.stream().filter(w -> w != null && !w.isBlank()).collect(Collectors.joining(" "));
        return result.isBlank() ? null : result;
    }

    private boolean containsTokenVariant(String text, String token) {
        return tokenVariants(token).stream().anyMatch(v -> containsTerm(text, v));
    }

    private List<String> tokenVariants(String token) {
        List<String> variants = new ArrayList<>();
        variants.add(token);
        if ("phenomenon".equals(token)) variants.add("phenomena");
        else if ("phenomena".equals(token)) variants.add("phenomenon");
        if (token.endsWith("y") && token.length() > 3) variants.add(token.substring(0, token.length() - 1) + "ies");
        else if (token.endsWith("ies") && token.length() > 4) variants.add(token.substring(0, token.length() - 3) + "y");
        else if (token.endsWith("s") && token.length() > 3) variants.add(token.substring(0, token.length() - 1));
        else if (token.length() > 3) variants.add(token + "s");
        return variants.stream().distinct().collect(Collectors.toList());
    }

    private boolean containsTerm(String text, String term) {
        String nt = normalize(text), nterm = normalize(term);
        if (nt.isBlank() || nterm.isBlank()) return false;
        return (" " + nt + " ").contains(" " + nterm + " ");
    }

    private String normalize(String v) { return keywordExpansionService.normalize(v); }

    private String normalizeOpenAlexSearchQuery(String query) {
        if (query == null) return "";
        return query.replace("&", " ").replace("/", " ").replace("\\", " ").trim().replaceAll("\\s+", " ");
    }

    private String normalizeDoi(String doi) {
        if (doi == null || doi.isBlank()) return null;
        return doi.replace("https://doi.org/", "").replace("http://doi.org/", "").trim();
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) return null;
        try { return LocalDate.parse(date); } catch (Exception e) { return null; }
    }

    private String firstNonBlank(String a, String b) { return a != null && !a.isBlank() ? a : b; }
    private String nullToEmpty(String v) { return v == null ? "" : v; }
    private void append(StringBuilder sb, String v) { if (v != null && !v.isBlank()) sb.append(' ').append(v); }

    private String resolvePdfUrl(OpenAlexResponseDTO.OpenAlexWorkDTO work) {
        if (work.getBestOaLocation() != null && work.getBestOaLocation().getPdfUrl() != null
                && !work.getBestOaLocation().getPdfUrl().isBlank()) return work.getBestOaLocation().getPdfUrl();
        if (work.getPrimaryLocation() != null && work.getPrimaryLocation().getPdfUrl() != null
                && !work.getPrimaryLocation().getPdfUrl().isBlank()) return work.getPrimaryLocation().getPdfUrl();
        if (work.getOpenAccess() != null && work.getOpenAccess().getOaUrl() != null
                && !work.getOpenAccess().getOaUrl().isBlank()) return work.getOpenAccess().getOaUrl();
        return null;
    }

    private record WorkWithAbstract(OpenAlexResponseDTO.OpenAlexWorkDTO work, String abstractText) {}
}
