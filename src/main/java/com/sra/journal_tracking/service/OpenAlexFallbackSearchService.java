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

    public List<PaperDetailResponseDTO> search(String query, int size) {
        String normalizedQuery = normalizeOpenAlexSearchQuery(query);
        if (normalizedQuery.isBlank()) {
            return List.of();
        }

        LocalDate today = LocalDate.now();
        int startYear = Year.now().getValue() - RECENT_PUBLICATION_YEAR_WINDOW + 1;
        int perPage = Math.min(25, Math.max(size * 4, 10));

        String url = UriComponentsBuilder
                .fromHttpUrl(OPEN_ALEX_BASE_URL + "/works")
                .queryParam("search", normalizedQuery)
                .queryParam("filter", "from_publication_date:" + startYear + "-01-01,to_publication_date:" + today)
                .queryParam("sort", "relevance_score:desc")
                .queryParam("per-page", perPage)
                .queryParam("select", "id,doi,title,display_name,publication_year,publication_date,cited_by_count,abstract_inverted_index,open_access,primary_location,topics,keywords,authorships")
                .build()
                .encode()
                .toUriString();

        try {
            OpenAlexResponseDTO response = restTemplate.getForObject(url, OpenAlexResponseDTO.class);
            if (response == null || response.getResults() == null) {
                return List.of();
            }

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

    private boolean isRecent(OpenAlexResponseDTO.OpenAlexWorkDTO work, int startYear, LocalDate today) {
        Short publicationYear = work.getPublicationYear();
        if (publicationYear == null || publicationYear < startYear || publicationYear > today.getYear()) {
            return false;
        }

        LocalDate publicationDate = parseDate(work.getPublicationDate());
        return publicationDate == null || !publicationDate.isAfter(today);
    }

    private boolean isRelevant(OpenAlexResponseDTO.OpenAlexWorkDTO work, String abstractText, String query) {
        List<String> tokens = keywordExpansionService.extractTokens(query);
        if (tokens.isEmpty()) {
            return false;
        }

        String normalizedQuery = keywordExpansionService.normalize(query);
        String strongText = buildStrongText(work);
        String fullText = normalize(strongText + " " + nullToEmpty(abstractText));

        if (containsTerm(strongText, normalizedQuery)) {
            return true;
        }

        if (tokens.size() == 1) {
            return containsTokenVariant(strongText, tokens.get(0));
        }

        return tokens.stream().allMatch(token -> containsTokenVariant(fullText, token))
                && tokens.stream().anyMatch(token -> containsTokenVariant(strongText, token));
    }

    private String buildStrongText(OpenAlexResponseDTO.OpenAlexWorkDTO work) {
        StringBuilder text = new StringBuilder();
        append(text, work.getTitle());
        append(text, work.getDisplayName());

        if (work.getTopics() != null) {
            work.getTopics().forEach(topic -> {
                append(text, topic.getDisplayName());
                if (topic.getField() != null) {
                    append(text, topic.getField().getDisplayName());
                }
                if (topic.getDomain() != null) {
                    append(text, topic.getDomain().getDisplayName());
                }
            });
        }

        if (work.getKeywords() != null) {
            work.getKeywords().forEach(keyword -> append(text, keyword.getDisplayName()));
        }

        return normalize(text.toString());
    }

    private PaperDetailResponseDTO mapToPaper(OpenAlexResponseDTO.OpenAlexWorkDTO work, String abstractText) {
        String doi = normalizeDoi(work.getDoi());
        String sourceUrl = doi != null ? "https://doi.org/" + doi : work.getId();
        OpenAlexResponseDTO.Source source = work.getPrimaryLocation() != null
                ? work.getPrimaryLocation().getSource()
                : null;

        return PaperDetailResponseDTO.builder()
                .paperId(stablePreviewId(work))
                .title(firstNonBlank(work.getTitle(), work.getDisplayName()))
                .abstractText(abstractText)
                .doi(doi)
                .pubYear(work.getPublicationYear())
                .pubDate(parseDate(work.getPublicationDate()))
                .citationCount(work.getCitedByCount() != null ? work.getCitedByCount() : 0)
                .isOpenAccess(work.getOpenAccess() != null && Boolean.TRUE.equals(work.getOpenAccess().getIsOa()))
                .journalName(source != null ? source.getDisplayName() : null)
                .journalId(null)
                .fieldName(resolveFieldName(work))
                .fieldId(null)
                .authors(mapAuthors(work))
                .keywords(mapKeywords(work))
                .sourceUrl(sourceUrl)
                .pdfAvailable(work.getOpenAccess() != null && Boolean.TRUE.equals(work.getOpenAccess().getIsOa()))
                .downloadUrl(sourceUrl)
                .rating(0.0d)
                .downloadCount(0)
                .commentCount(0)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private UUID stablePreviewId(OpenAlexResponseDTO.OpenAlexWorkDTO work) {
        String key = firstNonBlank(work.getId(), firstNonBlank(work.getDoi(), firstNonBlank(work.getTitle(), "")));
        return UUID.nameUUIDFromBytes(("openalex-preview:" + key).getBytes(StandardCharsets.UTF_8));
    }

    private List<AuthorDTO> mapAuthors(OpenAlexResponseDTO.OpenAlexWorkDTO work) {
        if (work.getAuthorships() == null) {
            return List.of();
        }

        List<AuthorDTO> authors = new ArrayList<>();
        int order = 1;
        for (OpenAlexResponseDTO.Authorship authorship : work.getAuthorships()) {
            if (order > MAX_AUTHORS) {
                break;
            }
            String name = authorship.getAuthor() != null
                    ? firstNonBlank(authorship.getAuthor().getDisplayName(), authorship.getRawAuthorName())
                    : authorship.getRawAuthorName();
            if (name == null || name.isBlank()) {
                continue;
            }
            authors.add(AuthorDTO.builder()
                    .fullName(name)
                    .affiliation(firstAffiliation(authorship))
                    .totalCitations(0)
                    .authorOrder(order)
                    .hIndex(0)
                    .build());
            order++;
        }
        return authors;
    }

    private List<KeywordDTO> mapKeywords(OpenAlexResponseDTO.OpenAlexWorkDTO work) {
        List<KeywordDTO> keywords = new ArrayList<>();
        if (work.getKeywords() != null) {
            work.getKeywords().stream()
                    .filter(keyword -> keyword.getDisplayName() != null && !keyword.getDisplayName().isBlank())
                    .sorted(Comparator.comparing(
                            keyword -> keyword.getScore() != null ? keyword.getScore() : 0.0d,
                            Comparator.reverseOrder()))
                    .limit(MAX_KEYWORDS)
                    .forEach(keyword -> keywords.add(KeywordDTO.builder()
                            .keywordText(keyword.getDisplayName())
                            .relevanceScore(keyword.getScore())
                            .build()));
        }

        if (keywords.isEmpty() && work.getTopics() != null) {
            work.getTopics().stream()
                    .filter(topic -> topic.getDisplayName() != null && !topic.getDisplayName().isBlank())
                    .limit(MAX_KEYWORDS)
                    .forEach(topic -> keywords.add(KeywordDTO.builder()
                            .keywordText(topic.getDisplayName())
                            .relevanceScore(topic.getScore())
                            .build()));
        }
        return keywords;
    }

    private String resolveFieldName(OpenAlexResponseDTO.OpenAlexWorkDTO work) {
        if (work.getTopics() == null || work.getTopics().isEmpty()) {
            return null;
        }
        OpenAlexResponseDTO.Topic topic = work.getTopics().get(0);
        return topic.getField() != null ? topic.getField().getDisplayName() : null;
    }

    private String rebuildAbstract(Map<String, List<Integer>> invertedIndex) {
        if (invertedIndex == null || invertedIndex.isEmpty()) {
            return null;
        }

        List<String> words = new ArrayList<>();
        invertedIndex.forEach((word, positions) -> {
            if (positions != null) {
                positions.forEach(position -> {
                    while (words.size() <= position) {
                        words.add("");
                    }
                    words.set(position, word);
                });
            }
        });

        String abstractText = words.stream()
                .filter(word -> word != null && !word.isBlank())
                .collect(Collectors.joining(" "));
        return abstractText.isBlank() ? null : abstractText;
    }

    private boolean containsTokenVariant(String text, String token) {
        return tokenVariants(token).stream().anyMatch(variant -> containsTerm(text, variant));
    }

    private List<String> tokenVariants(String token) {
        List<String> variants = new ArrayList<>();
        variants.add(token);
        if ("phenomenon".equals(token)) {
            variants.add("phenomena");
        } else if ("phenomena".equals(token)) {
            variants.add("phenomenon");
        }
        if (token.endsWith("y") && token.length() > 3) {
            variants.add(token.substring(0, token.length() - 1) + "ies");
        } else if (token.endsWith("ies") && token.length() > 4) {
            variants.add(token.substring(0, token.length() - 3) + "y");
        } else if (token.endsWith("s") && token.length() > 3) {
            variants.add(token.substring(0, token.length() - 1));
        } else if (token.length() > 3) {
            variants.add(token + "s");
        }
        return variants.stream().distinct().collect(Collectors.toList());
    }

    private boolean containsTerm(String text, String term) {
        String normalizedText = normalize(text);
        String normalizedTerm = normalize(term);
        if (normalizedText.isBlank() || normalizedTerm.isBlank()) {
            return false;
        }
        return (" " + normalizedText + " ").contains(" " + normalizedTerm + " ");
    }

    private String normalize(String value) {
        return keywordExpansionService.normalize(value);
    }

    private String normalizeOpenAlexSearchQuery(String query) {
        if (query == null) {
            return "";
        }
        return query
                .replace("&", " ")
                .replace("/", " ")
                .replace("\\", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private String normalizeDoi(String doi) {
        if (doi == null || doi.isBlank()) {
            return null;
        }
        return doi.replace("https://doi.org/", "")
                .replace("http://doi.org/", "")
                .trim();
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date);
        } catch (Exception e) {
            return null;
        }
    }

    private String firstAffiliation(OpenAlexResponseDTO.Authorship authorship) {
        if (authorship.getRawAffiliationStrings() == null || authorship.getRawAffiliationStrings().isEmpty()) {
            return null;
        }
        return authorship.getRawAffiliationStrings().get(0);
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void append(StringBuilder text, String value) {
        if (value != null && !value.isBlank()) {
            text.append(' ').append(value);
        }
    }

    private record WorkWithAbstract(OpenAlexResponseDTO.OpenAlexWorkDTO work, String abstractText) {
    }
}
