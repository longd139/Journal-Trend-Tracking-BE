package com.sra.journal_tracking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sra.journal_tracking.entity.jpa.*;
import com.sra.journal_tracking.repository.jpa.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Focused crawl service for the Research Gap Explorer.
 * Crawls papers for specific keywords from OpenAlex, applies quality filtering,
 * runs AI enrichment, and saves to both SQL Server and Neo4j.
 */
@Service
public class GapDataCrawlService {

    private static final Logger log = LoggerFactory.getLogger(GapDataCrawlService.class);
    private static final String OPENALEX_WORKS_URL =
            "https://api.openalex.org/works?search={query}&filter=publication_year:2024,2025,2026&per_page=50&sort=cited_by_count:desc";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ResearchPaperRepository paperRepository;
    private final JournalRepository journalRepository;
    private final AuthorRepository authorRepository;
    private final KeywordRepository keywordRepository;
    private final ResearchFieldRepository fieldRepository;
    private final PaperAuthorRepository paperAuthorRepository;
    private final PaperKeywordRepository paperKeywordRepository;
    private final ApiSourceRepository apiSourceRepository;
    private final ResearchTopicRepository topicRepository;
    private final TopicKeywordRepository topicKeywordRepository;
    private final GraphService graphService;
    private final GraphEnrichmentService graphEnrichment;
    private final PaperEnrichmentService paperEnrichment;
    private final QualityScoreFilter qualityFilter;
    private final Neo4jClient neo4jClient;

    // Progress tracking
    private final ConcurrentHashMap<String, CrawlProgress> progressMap = new ConcurrentHashMap<>();

    @Value("${app.openalex-api-key:}")
    private String openalexApiKey;

    @Value("${app.openalex-email:}")
    private String openalexEmail;

    public GapDataCrawlService(RestTemplate restTemplate, ObjectMapper objectMapper,
                                ResearchPaperRepository paperRepository,
                                JournalRepository journalRepository,
                                AuthorRepository authorRepository,
                                KeywordRepository keywordRepository,
                                ResearchFieldRepository fieldRepository,
                                PaperAuthorRepository paperAuthorRepository,
                                PaperKeywordRepository paperKeywordRepository,
                                ApiSourceRepository apiSourceRepository,
                                ResearchTopicRepository topicRepository,
                                TopicKeywordRepository topicKeywordRepository,
                                GraphService graphService,
                                GraphEnrichmentService graphEnrichment,
                                PaperEnrichmentService paperEnrichment,
                                QualityScoreFilter qualityFilter,
                                Neo4jClient neo4jClient) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.paperRepository = paperRepository;
        this.journalRepository = journalRepository;
        this.authorRepository = authorRepository;
        this.keywordRepository = keywordRepository;
        this.fieldRepository = fieldRepository;
        this.paperAuthorRepository = paperAuthorRepository;
        this.paperKeywordRepository = paperKeywordRepository;
        this.apiSourceRepository = apiSourceRepository;
        this.topicRepository = topicRepository;
        this.topicKeywordRepository = topicKeywordRepository;
        this.graphService = graphService;
        this.graphEnrichment = graphEnrichment;
        this.paperEnrichment = paperEnrichment;
        this.qualityFilter = qualityFilter;
        this.neo4jClient = neo4jClient;
    }

    /**
     * Start a focused crawl for a keyword.
     * Returns a taskId for progress tracking.
     */
    public String startCrawl(List<String> keywords) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        CrawlProgress progress = new CrawlProgress();
        progress.keywords = keywords;
        progress.status = "STARTED";
        progressMap.put(taskId, progress);
        crawlAsync(taskId, keywords);
        return taskId;
    }

    /**
     * Get crawl progress.
     */
    public CrawlProgress getProgress(String taskId) {
        return progressMap.getOrDefault(taskId, null);
    }

    @Async("taskExecutor")
    private void crawlAsync(String taskId, List<String> keywords) {
        CrawlProgress p = progressMap.get(taskId);
        p.startedAt = System.currentTimeMillis();
        try {
            // ── STAGE 1: FETCHING ──
            p.status = "FETCHING";
            p.currentStage = "FETCHING";
            p.estimatedSeconds = keywords.size() * 60; // ~1 min per keyword rough estimate
            List<PaperData> allPapers = new ArrayList<>();

            for (String keyword : keywords) {
                p.currentKeyword = keyword;
                List<PaperData> papers = fetchFromOpenAlex(keyword, 50);
                allPapers.addAll(papers);
                p.fetchedPapers = allPapers.size();
                log.info("Fetched {} papers for keyword '{}' (total so far: {})",
                        papers.size(), keyword, allPapers.size());
            }

            // Deduplicate by DOI
            Map<String, PaperData> unique = new LinkedHashMap<>();
            for (PaperData paper : allPapers) {
                String key = paper.doi != null ? paper.doi.toLowerCase() : paper.title.toLowerCase();
                unique.putIfAbsent(key, paper);
            }
            allPapers = new ArrayList<>(unique.values());
            p.totalPapers = allPapers.size();
            p.fetchedPapers = allPapers.size();

            // ── STAGE 2: FILTERING ──
            p.status = "FILTERING";
            p.currentStage = "FILTERING";
            p.estimatedSeconds = Math.max(30, allPapers.size() * 2);
            List<PaperData> qualityPapers = new ArrayList<>();
            List<Integer> scores = new ArrayList<>();
            int rejected = 0;
            for (PaperData paper : allPapers) {
                int score = estimateQualityScore(paper);
                scores.add(score);
                if (score >= QualityScoreFilter.MIN_QUALITY_SCORE) {
                    paper.qualityScore = score;
                    qualityPapers.add(paper);
                } else {
                    rejected++;
                }
                p.filteredPapers = qualityPapers.size() + rejected;
                p.rejectedPapers = rejected;
            }
            qualityFilter.logBatchSummary(scores);
            p.qualityPapers = qualityPapers.size();
            p.filteredPapers = qualityPapers.size();

            // ── STAGE 3: SAVING ──
            p.status = "SAVING";
            p.currentStage = "SAVING";
            p.estimatedSeconds = Math.max(15, qualityPapers.size() * 3);
            AtomicInteger saved = new AtomicInteger(0);
            AtomicInteger enriched = new AtomicInteger(0);
            Set<String> newKwSet = new HashSet<>();

            for (PaperData paper : qualityPapers) {
                try {
                    ResearchPaper entity = savePaper(paper);
                    if (entity != null) {
                        saved.incrementAndGet();

                        // Save to Neo4j (keywords) + track new keywords
                        try {
                            int kwBefore = newKwSet.size();
                            saveToNeo4j(entity, paper);
                            // track new keywords from paper
                            if (paper.keywords != null) {
                                for (String kw : paper.keywords) {
                                    newKwSet.add(kw.toLowerCase().trim());
                                }
                            }
                        } catch (Throwable t) {
                            log.warn("Neo4j save failed for {} (non-fatal): {}",
                                    entity.getPaperId(), t.getMessage());
                        }

                        p.savedPapers = saved.get();
                        p.newKeywords = newKwSet.size();
                    }
                } catch (Exception e) {
                    log.warn("Failed to process paper '{}': {}", paper.title, e.getMessage());
                }
            }

            // ── STAGE 4: ENRICHING ──
            p.status = "ENRICHING";
            p.currentStage = "ENRICHING";
            p.estimatedSeconds = Math.max(20, qualityPapers.size() * 5);
            for (PaperData paper : qualityPapers) {
                try {
                    // Need to look up the paper entity again
                    String doi = paper.doi;
                    if (doi == null || doi.isBlank()) continue;
                    var paperOpt = paperRepository.findByDoi(doi);
                    if (paperOpt.isEmpty()) continue;
                    ResearchPaper entity = paperOpt.get();

                    PaperEnrichmentService.EnrichedData enrichedData =
                            paperEnrichment.enrich(
                                    entity.getPaperId().toString(),
                                    entity.getAbstractText(),
                                    null
                            );

                    if (!enrichedData.isEmpty()) {
                        graphEnrichment.saveEnrichmentData(
                                entity.getPaperId().toString(), enrichedData);
                        enriched.incrementAndGet();
                    }
                } catch (Throwable t) {
                    log.warn("Enrichment failed for paper '{}' (non-fatal): {}",
                            paper.title, t.getMessage());
                }
                p.enrichedPapers = enriched.get();
            }

            // ── STAGE 5: MIGRATING ──
            p.status = "MIGRATING";
            p.currentStage = "MIGRATING";
            p.estimatedSeconds = 10;
            migrateHierarchy();

            p.status = "DONE";
            p.currentStage = "DONE";
            p.estimatedSeconds = 0;
            log.info("Crawl complete: {} papers saved, {} enriched, {} new keywords",
                    saved.get(), enriched.get(), newKwSet.size());

        } catch (Exception e) {
            log.error("Crawl failed: {}", e.getMessage(), e);
            p.status = "FAILED";
            p.currentStage = "FAILED";
            p.error = e.getMessage();
            p.estimatedSeconds = 0;
        }
    }

    // ═══════════════════════════════════════════════════
    // OpenAlex Fetch
    // ═══════════════════════════════════════════════════

    private List<PaperData> fetchFromOpenAlex(String query, int limit) {
        List<PaperData> papers = new ArrayList<>();
        try {
            String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
            // OpenAlex: use | for OR multiple years
            String url = "https://api.openalex.org/works?search=" + encodedQuery
                    + "&filter=publication_year:2024|2025|2026"
                    + "&per_page=50&sort=cited_by_count:desc";
            // Add API key (required since Feb 2026)
            if (openalexApiKey != null && !openalexApiKey.isBlank()) {
                url += "&api_key=" + openalexApiKey;
            }
            // Add mailto for backward compatibility
            if (openalexEmail != null && !openalexEmail.isBlank()) {
                url += "&mailto=" + openalexEmail;
            }
            log.info("Fetching OpenAlex: {}", url.replaceAll("api_key=[^&]+", "api_key=***"));
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode results = root.get("results");

            if (results != null && results.isArray()) {
                for (JsonNode work : results) {
                    PaperData p = new PaperData();
                    p.title = jsonStr(work, "title");
                    p.doi = jsonStr(work, "doi");
                    p.pubYear = work.has("publication_year") ? work.get("publication_year").asInt() : null;
                    p.citedByCount = work.has("cited_by_count") ? work.get("cited_by_count").asInt() : 0;
                    p.isOa = work.has("open_access") && work.get("open_access").has("is_oa")
                            && work.get("open_access").get("is_oa").asBoolean();
                    p.type = jsonStr(work, "type");

                    // Abstract
                    JsonNode abstractNode = work.get("abstract_inverted_index");
                    if (abstractNode != null && !abstractNode.isNull()) {
                        p.abstractText = reconstructAbstract(abstractNode);
                    }

                    // STEP 1: Extract keywords from OpenAlex `keywords` field (author-supplied)
                    JsonNode keywordsNode = work.get("keywords");
                    if (keywordsNode != null && keywordsNode.isArray()) {
                        for (JsonNode kw : keywordsNode) {
                            String kwText = jsonStr(kw, "display_name"); // OpenAlex uses "display_name", not "keyword"
                            if (kwText != null && !kwText.isBlank()) {
                                p.keywords.add(kwText.trim());
                            }
                        }
                    }

                    // STEP 2: Fallback — extract from `concepts` field (always present)
                    if (p.keywords.isEmpty()) {
                        try {
                            p.keywords.addAll(extractKeywordsFromConcepts(work));
                        } catch (Exception e) {
                            log.debug("Concept extraction failed for '{}': {}", p.title, e.getMessage());
                        }
                    }

                    // STEP 3: Last resort — extract from title
                    if (p.keywords.isEmpty()) {
                        try {
                            p.keywords.addAll(extractKeywordsFromTitle(p.title));
                        } catch (Exception e) {
                            log.debug("Title extraction failed for '{}': {}", p.title, e.getMessage());
                        }
                    }

                    // Journal
                    JsonNode source = work.path("primary_location").path("source");
                    if (!source.isMissingNode() && !source.isNull()) {
                        p.journalName = jsonStr(source, "display_name");
                        p.issn = jsonStr(source, "issn_l");
                    }

                    // Authors
                    JsonNode authorships = work.get("authorships");
                    if (authorships != null && authorships.isArray()) {
                        for (JsonNode auth : authorships) {
                            JsonNode author = auth.get("author");
                            if (author != null) {
                                AuthorData ad = new AuthorData();
                                ad.fullName = jsonStr(author, "display_name");
                                ad.externalId = jsonStr(author, "id");
                                p.authors.add(ad);
                            }
                        }
                    }

                    // Only add paper once (FIX: was adding inside author loop before)
                    if (p.title != null && !p.title.isBlank()) {
                        papers.add(p);
                    }
                    if (papers.size() >= limit) break;
                }
            }
        } catch (Exception e) {
            log.warn("OpenAlex fetch failed for '{}': {}", query, e.getMessage());
        }
        return papers;
    }

    private String jsonStr(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? value.asText() : null;
    }

    /** Sanitize text for SQL Server — replace characters that cause conversion errors. */
    private String sanitize(String text) {
        if (text == null) return null;
        return text.replace("\"", "'")  // double quotes → single quotes
                   .replace("“", "'")  // left double quote
                   .replace("”", "'")  // right double quote
                   .replace("‘", "'")  // left single quote
                   .replace("’", "'"); // right single quote
    }

    private String reconstructAbstract(JsonNode invertedIndex) {
        if (invertedIndex == null) return null;
        Map<Integer, String> wordMap = new TreeMap<>();
        invertedIndex.fields().forEachRemaining(entry -> {
            String word = entry.getKey();
            JsonNode positions = entry.getValue();
            if (positions != null && positions.isArray()) {
                for (JsonNode pos : positions) {
                    wordMap.put(pos.asInt(), word);
                }
            }
        });
        StringBuilder sb = new StringBuilder();
        for (String word : wordMap.values()) {
            sb.append(word).append(" ");
        }
        String result = sb.toString().trim();
        return result.length() > 100 ? result : null;
    }

    /**
     * Extract keywords from OpenAlex "concepts" field.
     * Concepts are always present for indexed works. We filter:
     * - level >= 1 (skip broad field-level concepts like "Computer Science")
     * - score >= 20 (reasonably relevant to this paper)
     * Returns up to 10 keywords sorted by score descending.
     */
    private List<String> extractKeywordsFromConcepts(JsonNode work) {
        List<String> keywords = new ArrayList<>();
        JsonNode conceptsNode = work.get("concepts");
        if (conceptsNode == null || !conceptsNode.isArray()) return keywords;

        // Collect (display_name, score, level) tuples, filter relevant ones
        List<ConceptEntry> entries = new ArrayList<>();
        for (JsonNode c : conceptsNode) {
            int level = c.has("level") ? c.get("level").asInt() : 0;
            double score = c.has("score") ? c.get("score").asDouble() : 0.0;
            String name = jsonStr(c, "display_name");
            // OpenAlex concept score is 0.0-1.0 (relevance), not 0-100
            if (name != null && !name.isBlank() && level >= 1 && score >= 0.2) {
                entries.add(new ConceptEntry(name.trim(), score));
            }
        }

        // Sort by score descending, take top 10
        entries.sort((a, b) -> Double.compare(b.score, a.score));
        for (int i = 0; i < Math.min(entries.size(), 10); i++) {
            String name = entries.get(i).name;
            if (!keywords.contains(name.toLowerCase())) {
                keywords.add(name);
            }
        }
        return keywords;
    }

    /**
     * Last-resort keyword extraction from paper title.
     * Splits on common delimiters, filters stopwords, returns key phrases.
     */
    private List<String> extractKeywordsFromTitle(String title) {
        List<String> keywords = new ArrayList<>();
        if (title == null || title.isBlank()) return keywords;

        // Common academic title stopwords
        Set<String> stopWords = new HashSet<>(Set.of(
                "a", "an", "the", "of", "in", "on", "at", "to", "for", "with",
                "and", "or", "is", "are", "was", "were", "be", "been", "being",
                "by", "from", "as", "into", "through", "during", "before", "after",
                "above", "below", "between", "under", "over", "its", "it", "this",
                "that", "these", "those", "which", "who", "whom", "whose",
                "new", "using", "based", "toward", "towards", "via", "can", "may",
                "has", "have", "had", "do", "does", "did", "will", "would", "could",
                "should", "about", "than", "also", "not", "no", "but", "however",
                "approach", "method", "study", "review", "survey", "analysis",
                "application", "system", "model", "data"
        ));

        // Split title by common delimiters
        String[] parts = title.split("[:,;.–—\\-\\s]+");
        for (String part : parts) {
            String cleaned = part.trim()
                    .replaceAll("[()\\[\\]{}\"']", "")
                    .replaceAll("^[0-9.]+", ""); // strip leading numbers
            if (cleaned.length() > 3 && !stopWords.contains(cleaned.toLowerCase())) {
                keywords.add(cleaned);
            }
        }

        // Deduplicate case-insensitively
        List<String> unique = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String kw : keywords) {
            String lower = kw.toLowerCase();
            if (!seen.contains(lower)) {
                seen.add(lower);
                unique.add(kw);
            }
        }
        return unique.size() > 10 ? unique.subList(0, 10) : unique;
    }

    // Inner class for concept sorting
    private static class ConceptEntry {
        final String name;
        final double score;
        ConceptEntry(String name, double score) { this.name = name; this.score = score; }
    }

    // ═══════════════════════════════════════════════════
    // Quality Score
    // ═══════════════════════════════════════════════════

    private int estimateQualityScore(PaperData paper) {
        int score = 0;
        if (paper.citedByCount >= 50) score += 30;
        else if (paper.citedByCount >= 20) score += 20;
        else if (paper.citedByCount >= 5) score += 10;
        if (paper.type != null && paper.type.contains("journal")) score += 15;
        if (paper.isOa) score += 15;
        if (paper.abstractText != null && paper.abstractText.split("\\s+").length > 200) score += 15;
        if (paper.doi != null && !paper.doi.isBlank()) score += 5;
        return Math.min(score, 100);
    }

    // ═══════════════════════════════════════════════════
    // Save to SQL
    // ═══════════════════════════════════════════════════

    private ApiSource defaultSource; // cached

    private ResearchPaper savePaper(PaperData paper) {
        try {
            if (paper.doi != null) {
                Optional<ResearchPaper> existing = paperRepository.findByDoi(paper.doi);
                if (existing.isPresent()) return existing.get();
            }

            ResearchPaper entity = new ResearchPaper();
            // Sanitize: escape/remove characters that break SQL Server
            entity.setTitle(sanitize(paper.title));
            entity.setDoi(paper.doi);
            entity.setAbstractText(sanitize(paper.abstractText));
            entity.setPubYear(paper.pubYear != null ? paper.pubYear.shortValue() : null);
            entity.setCitationCount(paper.citedByCount);
            entity.setIsOpenAccess(paper.isOa);
            entity.setType(paper.type);
            entity.setCreatedAt(LocalDateTime.now());

            // Set required ApiSource (lazy-load once)
            if (defaultSource == null) {
                defaultSource = apiSourceRepository.findBySourceName("OpenAlex")
                        .orElseGet(() -> {
                            ApiSource src = new ApiSource();
                            src.setSourceName("OpenAlex");
                            src.setBaseUrl("https://api.openalex.org");
                            src.setIsActive(true);
                            return apiSourceRepository.save(src);
                        });
            }
            entity.setSource(defaultSource);

            entity = paperRepository.save(entity);
            return entity;
        } catch (Exception e) {
            log.warn("Skip paper '{}': {}", paper.title, e.getMessage());
            return null;
        }
    }

    // ═══════════════════════════════════════════════════
    // Save to Neo4j
    // ═══════════════════════════════════════════════════

    private void saveToNeo4j(ResearchPaper entity, PaperData paper) {
        try {
            graphService.savePaperWithKeywords(
                    entity.getPaperId().toString(),
                    entity.getPubYear() != null ? entity.getPubYear().intValue() : null,
                    entity.getTitle(),
                    entity.getDoi(),
                    null, // fieldId — set later during migration
                    paper.keywords
            );
        } catch (Exception e) {
            log.warn("Neo4j save failed for {}: {}", entity.getPaperId(), e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════
    // Hierarchy Migration
    // ═══════════════════════════════════════════════════

    private void migrateHierarchy() {
        log.info("Running hierarchy migration...");
        try {
            // 1. Create Year nodes + PUBLISHED_IN relationships
            neo4jClient.query("""
                    MATCH (p:Paper) WHERE p.pubYear IS NOT NULL
                    WITH DISTINCT p.pubYear AS year
                    MERGE (y:Year {year: year})
                    """).run();

            neo4jClient.query("""
                    MATCH (p:Paper) WHERE p.pubYear IS NOT NULL
                    MATCH (y:Year {year: p.pubYear})
                    MERGE (p)-[:PUBLISHED_IN]->(y)
                    """).run();
            log.info("Year nodes + PUBLISHED_IN created");

            // 2. Create ResearchField nodes from SQL
            List<ResearchField> fields = fieldRepository.findAll();
            if (!fields.isEmpty()) {
                List<Map<String, String>> fieldMaps = fields.stream().map(f -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("fieldId", f.getFieldId().toString());
                    m.put("fieldName", f.getFieldName());
                    return m;
                }).toList();

                neo4jClient.query("""
                        UNWIND $fields AS f
                        MERGE (rf:ResearchField {fieldId: f.fieldId})
                        ON CREATE SET rf.fieldName = f.fieldName
                        """).bind(fieldMaps).to("fields").run();
                log.info("{} ResearchField nodes created", fields.size());
            }

            // 3. Create BELONGS_TO_FIELD (Paper → ResearchField)
            // Uses Journal.FieldID from SQL. For papers without journal, skip.
            // This is best done with a SQL→Neo4j batch:
            try {
                List<ResearchPaper> papers = paperRepository.findAll();
                List<Map<String, String>> mappings = new ArrayList<>();
                for (ResearchPaper paper : papers) {
                    if (paper.getJournal() != null && paper.getJournal().getField() != null) {
                        Map<String, String> m = new LinkedHashMap<>();
                        m.put("paperId", paper.getPaperId().toString());
                        m.put("fieldId", paper.getJournal().getField().getFieldId().toString());
                        mappings.add(m);
                    }
                }
                if (!mappings.isEmpty()) {
                    // Batch update fieldId on Paper nodes
                    for (Map<String, String> m : mappings) {
                        neo4jClient.query("""
                                MATCH (p:Paper {paperId: $paperId})
                                MATCH (f:ResearchField {fieldId: $fieldId})
                                SET p.fieldId = $fieldId
                                MERGE (p)-[:BELONGS_TO_FIELD]->(f)
                                """).bind(m.get("paperId")).to("paperId")
                                .bind(m.get("fieldId")).to("fieldId").run();
                    }
                    log.info("{} BELONGS_TO_FIELD relationships created", mappings.size());
                }
            } catch (Exception e) {
                log.warn("BELONGS_TO_FIELD migration skipped: {}", e.getMessage());
            }

            // 4. Create Topic nodes from SQL RESEARCH_TOPIC + TOPIC_KEYWORD
            migrateTopics();

            log.info("Hierarchy migration complete");
        } catch (Exception e) {
            log.warn("Hierarchy migration failed: {}", e.getMessage());
        }
    }

    /**
     * Create Topic nodes in Neo4j from SQL RESEARCH_TOPIC table.
     * Also creates BELONGS_TO (Topic→ResearchField) and
     * COVERS (Topic→Keyword) relationships from TOPIC_KEYWORD table.
     */
    private void migrateTopics() {
        try {
            // Use JPQL scalar queries to avoid LazyInitializationException
            List<Object[]> topicData = topicRepository.findAllTopicData();
            if (topicData.isEmpty()) {
                log.info("No topics in SQL — skipping Topic node migration");
                return;
            }

            int topicCount = 0;
            for (Object[] row : topicData) {
                String topicId = row[0] != null ? row[0].toString() : null;
                String topicName = row[1] != null ? row[1].toString() : "";
                double trendScore = row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;
                boolean isTrending = row[3] != null && (Boolean) row[3];
                String fieldId = row[4] != null ? row[4].toString() : null;

                if (topicId == null) continue;

                // Create/update Topic node
                neo4jClient.query("""
                        MERGE (t:Topic {topicId: $topicId})
                        ON CREATE SET t.topicName = $topicName,
                                      t.trendScore = $trendScore,
                                      t.isTrending = $isTrending
                        ON MATCH SET t.topicName = $topicName,
                                     t.trendScore = $trendScore,
                                     t.isTrending = $isTrending
                        """)
                        .bind(topicId).to("topicId")
                        .bind(topicName).to("topicName")
                        .bind(trendScore).to("trendScore")
                        .bind(isTrending).to("isTrending")
                        .run();
                topicCount++;

                // BELONGS_TO: Topic → ResearchField
                if (fieldId != null) {
                    neo4jClient.query("""
                            MATCH (t:Topic {topicId: $topicId})
                            MATCH (f:ResearchField {fieldId: $fieldId})
                            MERGE (t)-[:BELONGS_TO]->(f)
                            """)
                            .bind(topicId).to("topicId")
                            .bind(fieldId).to("fieldId")
                            .run();
                }
            }

            // COVERS: Topic → Keyword (from TOPIC_KEYWORD with scalar query)
            List<Object[]> topicKeywords = topicKeywordRepository.findAllTopicKeywordMappings();
            int coverCount = 0;
            for (Object[] row : topicKeywords) {
                String topicId = row[0] != null ? row[0].toString() : null;
                String keywordText = row[1] != null ? row[1].toString() : null;
                double weight = row[2] != null ? ((Number) row[2]).doubleValue() : 1.0;

                if (topicId == null || keywordText == null) continue;

                String normalizedKeyword = keywordText.toLowerCase().trim();
                neo4jClient.query("""
                        MATCH (t:Topic {topicId: $topicId})
                        MATCH (k:Keyword {normalizedText: $normalizedText})
                        MERGE (t)-[c:COVERS]->(k)
                        ON CREATE SET c.weight = $weight
                        ON MATCH SET c.weight = $weight
                        """)
                        .bind(topicId).to("topicId")
                        .bind(normalizedKeyword).to("normalizedText")
                        .bind(weight).to("weight")
                        .run();
                coverCount++;
            }

            log.info("Topic migration: {} topics, {} COVERS relationships created", topicCount, coverCount);
        } catch (Exception e) {
            log.warn("Topic migration skipped: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════
    // Data classes
    // ═══════════════════════════════════════════════════

    static class PaperData {
        String title, doi, abstractText, type, journalName, issn;
        Integer pubYear, citedByCount, qualityScore;
        boolean isOa;
        List<String> keywords = new ArrayList<>();
        List<AuthorData> authors = new ArrayList<>();
    }

    static class AuthorData {
        String fullName, externalId;
    }

    public static class CrawlProgress {
        public String status = "PENDING"; // STARTED, FETCHING, FILTERING, SAVING, ENRICHING, MIGRATING, DONE, FAILED
        public List<String> keywords;
        public String currentKeyword;
        public int totalPapers;
        public int qualityPapers;
        public int savedPapers;
        public int enrichedPapers;
        public String error;

        // ── User-facing real-time fields ──
        public int fetchedPapers;        // papers fetched from OpenAlex so far
        public int filteredPapers;       // papers processed by quality filter so far
        public String currentStage;      // FETCHING | FILTERING | SAVING | ENRICHING | MIGRATING | DONE | FAILED
        public int estimatedSeconds;     // rough ETA remaining
        public long startedAt;           // System.currentTimeMillis() when crawl began
        public int rejectedPapers;       // papers rejected by quality filter
        public int newKeywords;          // new keywords added to Neo4j

        public int getElapsedSeconds() {
            return (int) ((System.currentTimeMillis() - startedAt) / 1000);
        }
    }
}
