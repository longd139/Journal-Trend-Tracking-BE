package com.sra.journal_tracking.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sra.journal_tracking.dto.idea.*;
import com.sra.journal_tracking.dto.paper.PaperDetailResponseDTO;
import com.sra.journal_tracking.entity.jpa.IdeaAnalysis;
import com.sra.journal_tracking.entity.jpa.PaperEvaluationCache;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.repository.jpa.IdeaAnalysisRepository;
import com.sra.journal_tracking.repository.jpa.PaperEvalCacheRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Core service for the Research Idea Analysis pipeline.
 *
 * <h3>Pipeline (4 AI calls)</h3>
 * <ol>
 *   <li><b>Extract Keywords</b> — AI extracts + suggests keywords from idea text</li>
 *   <li><b>Evaluate Papers</b> — For each paper, AI judges 4 criteria against the idea</li>
 *   <li><b>Gap Analysis</b> — Cross-paper synthesis: what's solved, what's missing</li>
 *   <li><b>Literature Review</b> — AI-generated Related Work section with citations</li>
 * </ol>
 *
 * <h3>3-Tier Caching</h3>
 * <pre>
 *   L1: ConcurrentHashMap + TTL (RAM, per-JVM)
 *   L2: PAPER_EVALUATION_CACHE table (DB, cross-user for same ideaHash)
 *   L3: DeepSeek AI API (only called on L1+L2 miss)
 * </pre>
 */
@Slf4j
@Service
public class IdeaAnalysisService {

    private static final int MAX_PAPERS = 5;
    private static final int KEYWORD_MAX_TOKENS = 512;
    private static final int EVAL_MAX_TOKENS = 1024;
    private static final int GAP_MAX_TOKENS = 2048;
    private static final int LIT_REVIEW_MAX_TOKENS = 2048;
    private static final double LOW_TEMP = 0.1;
    private static final double MEDIUM_TEMP = 0.3;

    // Caffeine-style TTL cache entries
    private static final long KEYWORD_CACHE_TTL_MS = 24 * 60 * 60 * 1000; // 24h
    private static final long EVAL_CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000; // 7 days

    private final AIClient aiClient;
    private final PdfExtractionService pdfExtractionService;
    private final OpenAlexFallbackSearchService openAlexSearchService;
    private final IdeaAnalysisRepository ideaAnalysisRepository;
    private final PaperEvalCacheRepository paperEvalCacheRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Value("${deepseek.idea.max-papers:5}")
    private int maxPapers;

    // ── L1 RAM Caches ──
    private final ConcurrentHashMap<String, CacheEntry<ExtractKeywordsResponse>> keywordCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry<List<CriterionResult>>> evalCache = new ConcurrentHashMap<>();

    public IdeaAnalysisService(AIClient aiClient,
                               PdfExtractionService pdfExtractionService,
                               OpenAlexFallbackSearchService openAlexSearchService,
                               IdeaAnalysisRepository ideaAnalysisRepository,
                               PaperEvalCacheRepository paperEvalCacheRepository,
                               UserRepository userRepository,
                               ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.pdfExtractionService = pdfExtractionService;
        this.openAlexSearchService = openAlexSearchService;
        this.ideaAnalysisRepository = ideaAnalysisRepository;
        this.paperEvalCacheRepository = paperEvalCacheRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    // ═══════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Step 1: Extract keywords from idea text + suggest additional ones.
     * Results cached in RAM for 24h (same text → same keywords).
     */
    public ExtractKeywordsResponse extractKeywords(String ideaText) {
        String key = md5(ideaText);

        // L1 cache
        CacheEntry<ExtractKeywordsResponse> cached = keywordCache.get(key);
        if (cached != null && !cached.isExpired()) {
            log.info("Keywords cache HIT for idea hash {}", key);
            return cached.data;
        }

        // L3: AI call
        try {
            ExtractKeywordsResponse response = aiExtractKeywords(ideaText);
            keywordCache.put(key, new CacheEntry<>(response, KEYWORD_CACHE_TTL_MS));
            return response;
        } catch (Exception e) {
            log.error("AI keyword extraction failed: {}", e.getMessage());
            throw new AppException(ErrorCode.IDEA_AI_FAILED);
        }
    }

    /**
     * Full analysis pipeline (steps 2-4).
     * Searches for papers, evaluates them, performs gap analysis,
     * generates literature review, and saves everything.
     */
    @Transactional
    public IdeaAnalysisResponse analyze(String ideaText, List<String> keywords, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String ideaHash = md5(ideaText);

        // ── Fetch papers from OpenAlex API ──
        // Strategy: relevance-first, then top-cited for supplementary coverage.
        // Relevance search finds papers actually about the topic (avoids highly-cited
        // off-topic papers like ResNet showing up for "deep learning" keyword).
        List<PaperDetailResponseDTO> allPapers = new ArrayList<>();
        Set<UUID> seenPaperIds = new HashSet<>();

        int currentYear = Year.now().getValue();
        int searchStartYear = Math.max(2015, currentYear - 10);

        for (String kw : keywords) {
            try {
                // Primary: search by relevance — returns papers semantically related to the query
                List<PaperDetailResponseDTO> relevancePapers = openAlexSearchService.searchByRelevance(
                        kw, 5, searchStartYear, currentYear);
                log.info("OpenAlex keyword '{}' (relevance, {}–{}) → {} papers",
                        kw, searchStartYear, currentYear, relevancePapers.size());
                for (PaperDetailResponseDTO paper : relevancePapers) {
                    if (seenPaperIds.add(paper.getPaperId())) {
                        allPapers.add(paper);
                    }
                }

                // Supplementary: search top-cited recent papers for high-impact coverage
                List<PaperDetailResponseDTO> citedPapers = openAlexSearchService.searchTopCited(
                        kw, 3, currentYear - 3, currentYear);
                log.info("OpenAlex keyword '{}' (top-cited, {}–{}) → {} papers",
                        kw, currentYear - 3, currentYear, citedPapers.size());
                for (PaperDetailResponseDTO paper : citedPapers) {
                    if (seenPaperIds.add(paper.getPaperId())) {
                        allPapers.add(paper);
                    }
                }
            } catch (Exception e) {
                log.warn("OpenAlex search failed for '{}': {}", kw, e.getMessage());
            }
        }

        // Limit to top N papers (prioritize those with PDF URLs + citations)
        List<PaperDetailResponseDTO> topPapers = allPapers.stream()
                .sorted(Comparator
                        .comparing((PaperDetailResponseDTO p) ->
                                p.getPdfUrl() != null && !p.getPdfUrl().isBlank() ? 0 : 1)
                        .thenComparing(PaperDetailResponseDTO::getCitationCount,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.min(maxPapers, MAX_PAPERS))
                .collect(Collectors.toList());

        log.info("Found {} total papers from OpenAlex for {} keywords, selected top {} for analysis",
                allPapers.size(), keywords.size(), topPapers.size());

        // ── Evaluate each paper (with 3-tier cache) ──
        List<PaperAnalysisDTO> paperResults = new ArrayList<>();
        for (PaperDetailResponseDTO paper : topPapers) {
            PaperAnalysisDTO dto = evaluatePaper(paper, ideaText, ideaHash);
            paperResults.add(dto);
        }

        // ── Gap Analysis (AI call 3) ──
        GapAnalysisDTO gapAnalysis = null;
        try {
            gapAnalysis = aiGapAnalysis(ideaText, paperResults);
        } catch (Exception e) {
            log.warn("Gap analysis failed: {}", e.getMessage());
        }
        // Fallback: if AI returned empty/broken gap analysis, generate one programmatically
        if (gapAnalysis == null || isGapAnalysisEmpty(gapAnalysis)) {
            log.warn("Gap analysis was empty or null, using programmatic fallback");
            gapAnalysis = buildFallbackGapAnalysis(ideaText, paperResults);
        }

        // ── Literature Review (AI call 4) ──
        LiteratureReviewDTO literatureReview = null;
        try {
            literatureReview = aiLiteratureReview(ideaText, paperResults, gapAnalysis);
        } catch (Exception e) {
            log.warn("Literature review generation failed: {}", e.getMessage());
        }
        // Fallback: if AI returned empty/broken lit review, generate one programmatically
        if (literatureReview == null || isLitReviewEmpty(literatureReview)) {
            log.warn("Literature review was empty or null, using programmatic fallback");
            literatureReview = buildFallbackLitReview(ideaText, paperResults, gapAnalysis);
        }

        // ── Build & save response ──
        IdeaAnalysisResponse response = IdeaAnalysisResponse.builder()
                .ideaText(ideaText)
                .keywords(keywords)
                .papers(paperResults)
                .gapAnalysis(gapAnalysis)
                .literatureReview(literatureReview)
                .build();

        // Persist to DB (async-friendly — wrapped in try-catch so save failure
        // doesn't break the response to the user)
        try {
            IdeaAnalysis entity = IdeaAnalysis.builder()
                    .user(user)
                    .ideaText(ideaText)
                    .ideaHash(ideaHash)
                    .keywords(toJson(keywords))
                    .resultJson(toJson(response))
                    .paperCount(topPapers.size())
                    .build();
            entity = ideaAnalysisRepository.save(entity);
            response.setAnalysisId(entity.getAnalysisId());
            response.setCreatedAt(entity.getCreatedAt() != null
                    ? entity.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    : null);
        } catch (Exception e) {
            log.error("Failed to save analysis to DB: {}", e.getMessage());
        }

        return response;
    }

    /**
     * Get paginated history of analyses for a user.
     */
    public HistoryListResponse getHistory(UUID userId, int page, int size) {
        Page<IdeaAnalysis> result = ideaAnalysisRepository
                .findByUser_UserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));

        List<HistoryListResponse.HistoryItem> items = result.getContent().stream()
                .map(entity -> {
                    // Try to extract noveltyScore from stored JSON
                    Integer noveltyScore = null;
                    try {
                        IdeaAnalysisResponse parsed = objectMapper.readValue(
                                entity.getResultJson(), IdeaAnalysisResponse.class);
                        if (parsed.getGapAnalysis() != null) {
                            noveltyScore = parsed.getGapAnalysis().getNoveltyScore();
                        }
                    } catch (Exception ignored) {}

                    return HistoryListResponse.HistoryItem.builder()
                            .analysisId(entity.getAnalysisId().toString())
                            .ideaText(entity.getIdeaText())
                            .keywords(parseJsonStringList(entity.getKeywords()))
                            .paperCount(entity.getPaperCount())
                            .noveltyScore(noveltyScore)
                            .createdAt(entity.getCreatedAt() != null
                                    ? entity.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                    : null)
                            .build();
                })
                .collect(Collectors.toList());

        return HistoryListResponse.builder()
                .items(items)
                .totalItems(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .currentPage(result.getNumber())
                .build();
    }

    /**
     * Load a single historical analysis from the DB (no AI calls).
     */
    public IdeaAnalysisResponse getHistoryDetail(UUID analysisId, UUID userId) {
        IdeaAnalysis entity = ideaAnalysisRepository.findById(analysisId)
                .orElseThrow(() -> new AppException(ErrorCode.IDEA_ANALYSIS_NOT_FOUND));

        // Ownership check
        if (!entity.getUser().getUserId().equals(userId)) {
            throw new AppException(ErrorCode.IDEA_ANALYSIS_UNAUTHORIZED);
        }

        try {
            IdeaAnalysisResponse response = objectMapper.readValue(
                    entity.getResultJson(), IdeaAnalysisResponse.class);
            response.setAnalysisId(entity.getAnalysisId());
            response.setCreatedAt(entity.getCreatedAt() != null
                    ? entity.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    : null);
            return response;
        } catch (Exception e) {
            log.error("Failed to parse stored analysis JSON: {}", e.getMessage());
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }

    /**
     * Delete a historical analysis. User must own it.
     */
    @Transactional
    public void deleteHistory(UUID analysisId, UUID userId) {
        ideaAnalysisRepository.deleteByAnalysisIdAndUser_UserId(analysisId, userId);
    }

    // ═══════════════════════════════════════════════════════════════
    //  AI PROMPTS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Escape literal {@code %} characters so they survive {@link String#format}.
     * Academic paper text routinely contains e.g. "95% CI", "p &lt; 0.05%" etc.
     */
    private static String esc(String s) {
        return s == null ? "" : s.replace("%", "%%");
    }

    private ExtractKeywordsResponse aiExtractKeywords(String ideaText) throws Exception {
        // Extract meaningful seed phrases from the idea text to help the AI focus.
        // These are NOT the final keywords — the AI transforms them into academic search terms.
        List<String> seedPhrases = extractPhrasesFromText(ideaText, 5);

        String prompt = String.format("""
                You are an academic librarian helping a researcher find papers.
                Extract 5 precise search keywords and suggest 5 related search terms from this research idea.

                RULES:
                - extractedKeywords: 5 specific, searchable terms pulled directly from the idea.
                  Use multi-word phrases when the idea contains them (e.g. "code generation", "systematic review").
                  Do NOT add generic AI/ML terms (e.g. "machine learning", "deep learning") unless the idea is specifically about them.
                - suggestedKeywords: 5 related academic terms that would help find more papers on this topic.
                  Think about: alternative terminology, broader/narrower concepts, specific tools or frameworks mentioned.

                RESEARCH IDEA: %s

                KEY CONCEPTS (for context only — you decide the final keywords): %s

                Respond with ONLY this JSON (no markdown, no extra text):
                {"extractedKeywords":["kw1","kw2","kw3","kw4","kw5"],"suggestedKeywords":["sk1","sk2","sk3","sk4","sk5"]}""",
                esc(ideaText),
                String.join(", ", seedPhrases));

        String raw = aiClient.call(prompt, KEYWORD_MAX_TOKENS, 0.0);
        String json = extractJson(raw, "extractKeywords");

        if (!"{}".equals(json)) {
            try {
                ExtractKeywordsResponse response = objectMapper.readValue(json, ExtractKeywordsResponse.class);
                // Post-process: filter out obviously bad keywords (single chars, template artifacts)
                response.setExtractedKeywords(filterValidKeywords(response.getExtractedKeywords()));
                response.setSuggestedKeywords(filterValidKeywords(response.getSuggestedKeywords()));
                if (!response.getExtractedKeywords().isEmpty()) {
                    return response;
                }
            } catch (Exception e) {
                log.warn("Failed to parse AI JSON, using fallback. JSON: {}", json);
            }
        }

        // ── Fallback: programmatic keyword extraction ──
        log.warn("AI returned non-JSON for keywords, using text fallback.");
        return fallbackExtractKeywords(ideaText, raw);
    }

    /** Filter out template artifacts and too-short keywords from AI response. */
    private List<String> filterValidKeywords(List<String> keywords) {
        if (keywords == null) return List.of();
        return keywords.stream()
                .filter(k -> k != null && k.length() >= 3)
                .filter(k -> !k.equalsIgnoreCase("suggestedkeywords")
                        && !k.equalsIgnoreCase("extractedkeywords")
                        && !k.equalsIgnoreCase("string"))
                .distinct()
                .limit(5)
                .collect(Collectors.toList());
    }

    /**
     * Extract meaningful multi-word phrases from idea text.
     * Uses bigram/trigram detection to catch phrases like "code generation",
     * "systematic review", "agile development".
     */
    private List<String> extractPhrasesFromText(String text, int max) {
        if (text == null || text.isBlank()) return List.of("research");
        List<String> result = new ArrayList<>();

        // Normalize: lowercase, strip special chars except hyphens
        String clean = text.toLowerCase().replaceAll("[^a-z0-9\\s-]", " ");
        String[] words = clean.trim().split("\\s+");

        // Collect bigrams (2-word phrases)
        for (int i = 0; i < words.length - 1 && result.size() < max; i++) {
            if (isValidWord(words[i]) && isValidWord(words[i + 1])) {
                String bigram = words[i] + " " + words[i + 1];
                if (!isStopPhrase(bigram) && !result.contains(bigram)) {
                    result.add(bigram);
                }
            }
        }

        // Collect trigrams (3-word phrases)
        for (int i = 0; i < words.length - 2 && result.size() < max; i++) {
            if (isValidWord(words[i]) && isValidWord(words[i + 1]) && isValidWord(words[i + 2])) {
                String trigram = words[i] + " " + words[i + 1] + " " + words[i + 2];
                if (!isStopPhrase(trigram) && !result.contains(trigram)) {
                    result.add(trigram);
                }
            }
        }

        // Fallback: single significant words (≥5 chars, not stop word)
        for (String w : words) {
            if (result.size() >= max) break;
            if (w.length() >= 5 && !isStopWord(w) && isValidSignificantWord(w)
                    && !result.contains(w)) {
                result.add(w);
            }
        }

        if (result.isEmpty()) result.add("research");
        return result;
    }

    private boolean isValidWord(String w) {
        return w.length() >= 2 && !isStopWord(w) && !w.matches("\\d+");
    }

    private boolean isValidSignificantWord(String w) {
        // Exclude common academic filler words that make poor search keywords
        return !Set.of("based", "using", "paper", "study", "approach", "method",
                "proposed", "novel", "improved", "efficient", "effective",
                "present", "discuss", "focus", "propose", "develop",
                "compare", "investigate", "examine", "evaluation",
                "introduction", "conclusion", "experiment", "abstract",
                "purpose", "background", "related", "work", "result",
                "show", "demonstrate", "system", "model", "review",
                "survey", "analysis", "data", "research", "literature",
                "findings", "limitation", "future", "direction",
                "implication", "contribution", "framework", "design",
                "implementation", "assessment", "toward", "towards",
                "through", "within", "across", "among", "including",
                "various", "different", "several", "many", "much",
                "however", "therefore", "thus", "although", "because",
                "since", "while", "where", "when", "how", "what")
                .contains(w);
    }

    private boolean isStopPhrase(String phrase) {
        // Reject phrases that are entirely stop words or common filler
        String[] words = phrase.split(" ");
        if (words.length == 2) {
            return (isStopWord(words[0]) && isStopWord(words[1]));
        }
        if (words.length == 3) {
            int stopCount = 0;
            for (String w : words) if (isStopWord(w)) stopCount++;
            return stopCount >= 2;
        }
        return false;
    }

    private boolean isStopWord(String w) {
        return STOP_WORDS.contains(w.toLowerCase());
    }

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "is", "are", "was", "were", "be", "been",
            "being", "have", "has", "had", "do", "does", "did", "will", "would",
            "could", "should", "may", "might", "can", "shall", "not", "no", "nor",
            "so", "if", "then", "than", "that", "this", "these", "those", "it",
            "its", "we", "they", "he", "she", "as", "about", "into", "over",
            "after", "before", "between", "under", "again", "further", "here",
            "there", "which", "who", "whom", "whose", "both", "each", "every",
            "all", "any", "few", "more", "most", "other", "some", "such", "only",
            "own", "same", "too", "very", "just", "also", "now", "new", "using",
            "based", "via", "per", "up", "out", "our", "their", "his", "her",
            "one", "two", "three", "well", "yet", "still", "however", "therefore",
            "thus", "although", "because", "since", "while", "where", "when",
            "how", "what", "conduct", "study", "want", "like", "need", "try",
            "make", "use", "research", "paper", "method", "approach", "result",
            "analysis", "data", "model", "system", "review", "survey", "proposed",
            "novel", "improved", "efficient", "effective", "present", "discuss",
            "show", "demonstrate", "focus", "propose", "develop", "compare",
            "investigate", "examine", "introduction", "conclusion", "experiment",
            "evaluation", "abstract", "purpose", "background", "related", "work"
    );

    /**
     * Fallback: when AI returns text instead of JSON, extract keywords programmatically.
     * Uses phrase detection on the idea text + quoted strings from AI raw response.
     * No longer injects generic AI/ML terms — only uses terms from the actual idea.
     */
    private ExtractKeywordsResponse fallbackExtractKeywords(String ideaText, String rawResponse) {
        List<String> extracted = new ArrayList<>();
        List<String> suggested = new ArrayList<>();

        // 1. Try to find quoted strings in the AI response
        if (rawResponse != null && !rawResponse.isBlank()) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\"([^\"]{3,80})\"")
                    .matcher(rawResponse);
            while (m.find()) {
                String kw = m.group(1).trim().toLowerCase();
                if (kw.length() >= 3 && !kw.matches(".*[{}:\\[\\]].*")
                        && !kw.equals("suggestedkeywords")
                        && !kw.equals("extractedkeywords")
                        && !isStopWord(kw) && !extracted.contains(kw)) {
                    if (extracted.size() < 5) extracted.add(kw);
                    else if (suggested.size() < 5) suggested.add(kw);
                }
            }
        }

        // 2. Use phrase extraction from idea text as primary source
        if (extracted.isEmpty()) {
            extracted = extractPhrasesFromText(ideaText, 5);
        }

        // 3. If suggested is still empty, derive related terms from extracted keywords
        //    by adding common academic qualifiers (NOT generic AI/ML terms)
        if (suggested.isEmpty() && !extracted.isEmpty()) {
            for (String kw : extracted) {
                if (suggested.size() >= 5) break;
                // Suggest variations: add "systematic" prefix or "tools" suffix for relevant keywords
                if (kw.contains("code") || kw.contains("software") || kw.contains("development")) {
                    if (!extracted.contains("software engineering") && !suggested.contains("software engineering"))
                        suggested.add("software engineering");
                    if (!extracted.contains("developer productivity") && !suggested.contains("developer productivity"))
                        suggested.add("developer productivity");
                }
                if (kw.contains("review") || kw.contains("survey")) {
                    if (!extracted.contains("literature review") && !suggested.contains("literature review"))
                        suggested.add("literature review");
                    if (!extracted.contains("evidence synthesis") && !suggested.contains("evidence synthesis"))
                        suggested.add("evidence synthesis");
                }
                if (kw.contains("agile") || kw.contains("scrum")) {
                    if (!extracted.contains("scrum") && !suggested.contains("scrum"))
                        suggested.add("scrum");
                    if (!extracted.contains("sprint") && !suggested.contains("sprint"))
                        suggested.add("sprint planning");
                }
            }
        }

        return ExtractKeywordsResponse.builder()
                .extractedKeywords(extracted)
                .suggestedKeywords(suggested)
                .build();
    }

    private List<CriterionResult> aiEvaluatePaper(PaperDetailResponseDTO paper, String ideaText,
                                                   String pdfText) throws Exception {
        String abstractSnippet = paper.getAbstractText() != null
                ? paper.getAbstractText() : "";
        if (abstractSnippet.length() > 1500) {
            abstractSnippet = abstractSnippet.substring(0, 1500) + "...";
        }

        String pdfSnippet = pdfText != null && pdfText.length() > 3000
                ? pdfText.substring(0, 3000) + "..."
                : (pdfText != null ? pdfText : "");

        String prompt = String.format("""
                CRITICAL: Respond with ONLY a valid JSON object. No other text. ONLY the JSON.

                Evaluate whether this paper is relevant to the researcher's idea across 4 criteria.
                For each criterion, give true/false WITH a verbatim quote from the paper as evidence.
                If no evidence exists, set value=false and evidenceQuote="".

                IDEA: "%s"

                PAPER — Title: %s
                Abstract: %s
                %s

                CRITERIA:
                TOPIC_MATCH = same topic? | METHOD_RELEVANT = method useful?
                GAP_ADDRESSED = addresses the gap? | CITE_WORTHY = worth citing?

                RESPOND WITH EXACTLY THIS JSON (replace values):
                {"criteria":[{"criterionName":"TOPIC_MATCH","value":true,"evidenceQuote":"exact quote"},
                {"criterionName":"METHOD_RELEVANT","value":false,"evidenceQuote":""},
                {"criterionName":"GAP_ADDRESSED","value":false,"evidenceQuote":""},
                {"criterionName":"CITE_WORTHY","value":true,"evidenceQuote":"exact quote"}]}
                """, esc(ideaText), esc(paper.getTitle()), esc(abstractSnippet),
                esc(pdfSnippet.isBlank() ? "" : "Full Text Excerpt: " + pdfSnippet));

        String raw = aiClient.call(prompt, EVAL_MAX_TOKENS, LOW_TEMP);
        JsonNode root = objectMapper.readTree(extractJson(raw, "evaluatePaper"));
        JsonNode criteriaNode = root.get("criteria");
        if (criteriaNode.isMissingNode() || !criteriaNode.isArray()) {
            log.warn("evaluatePaper: AI returned no criteria array, using N/A fallback");
            return buildNaCriteria();
        }
        return objectMapper.readValue(criteriaNode.traverse(),
                new TypeReference<List<CriterionResult>>() {});
    }

    private List<CriterionResult> buildNaCriteria() {
        return Arrays.asList(
                new CriterionResult("TOPIC_MATCH", null, ""),
                new CriterionResult("METHOD_RELEVANT", null, ""),
                new CriterionResult("GAP_ADDRESSED", null, ""),
                new CriterionResult("CITE_WORTHY", null, "")
        );
    }

    private GapAnalysisDTO aiGapAnalysis(String ideaText,
                                          List<PaperAnalysisDTO> papers) throws Exception {
        String papersJson = objectMapper.writeValueAsString(papers);

        String prompt = String.format("""
                CRITICAL: Respond with ONLY a valid JSON object. No other text. ONLY the JSON.

                Cross-paper gap analysis for %d papers evaluated against this research idea.

                IDEA: "%s"

                EVALUATIONS: %s

                RESPOND WITH EXACTLY THIS JSON (replace values/arrays):
                {"solvedAreas":[{"area":"what is solved","papers":["title1"],"summary":"1-2 sentences"}],
                "partiallyAddressed":[{"area":"partial topic","papers":["title2"],"limitation":"what is missing"}],
                "researchGaps":[{"gap":"unaddressed gap","rationale":"why it is a gap","suggestedDirection":"how to address"}],
                "suggestedDirections":["research direction 1","research direction 2"],
                "noveltyScore":65,"noveltyExplanation":"Score rationale in 2-3 sentences"}
                """, papers.size(), esc(ideaText), esc(papersJson));

        String raw = aiClient.call(prompt, GAP_MAX_TOKENS, MEDIUM_TEMP);
        return objectMapper.readValue(extractJson(raw, "gapAnalysis"), GapAnalysisDTO.class);
    }

    private LiteratureReviewDTO aiLiteratureReview(String ideaText,
                                                    List<PaperAnalysisDTO> papers,
                                                    GapAnalysisDTO gapAnalysis) throws Exception {
        // Build paper context with relevance flags so the AI knows which papers to cite
        List<Map<String, Object>> paperContexts = new ArrayList<>();
        for (int i = 0; i < papers.size(); i++) {
            PaperAnalysisDTO p = papers.get(i);
            boolean topicMatch = p.getCriteria() != null && p.getCriteria().stream()
                    .anyMatch(c -> "TOPIC_MATCH".equals(c.getCriterionName()) && Boolean.TRUE.equals(c.getValue()));
            boolean anyRelevant = p.getCriteria() != null && p.getCriteria().stream()
                    .anyMatch(c -> Boolean.TRUE.equals(c.getValue()));
            Map<String, Object> ctx = new LinkedHashMap<>();
            ctx.put("index", i + 1);
            ctx.put("title", p.getTitle());
            ctx.put("abstract", p.getAbstractText() != null ? p.getAbstractText().substring(0,
                    Math.min(300, p.getAbstractText().length())) : "");
            ctx.put("isTopicallyRelevant", topicMatch);
            ctx.put("isAtAllRelevant", anyRelevant);
            paperContexts.add(ctx);
        }

        long relevantCount = paperContexts.stream().filter(c -> Boolean.TRUE.equals(c.get("isTopicallyRelevant"))).count();
        long anyRelevantCount = paperContexts.stream().filter(c -> Boolean.TRUE.equals(c.get("isAtAllRelevant"))).count();
        String papersJson = objectMapper.writeValueAsString(paperContexts);

        String relevanceInstruction;
        if (anyRelevantCount == 0) {
            relevanceInstruction = """
                    CRITICAL: NONE of the papers are topically relevant to this research idea.
                    Do NOT cite any paper as "foundational work" or "recent work in this area."
                    Instead, write a literature review that honestly states:
                    - The idea addresses a niche/novel area not yet covered by the search results
                    - What the broader field context is (based on your knowledge, not these papers)
                    - Why a systematic search using specialized databases would be needed
                    Do NOT fabricate paper titles or claim these irrelevant papers support the idea.
                    """;
        } else if (relevantCount <= 1) {
            relevanceInstruction = """
                    Only cite papers marked as topically relevant. Papers with isAtAllRelevant=false
                    should NOT appear in the literature review. If only one paper is relevant,
                    acknowledge that the field is underexplored.
                    """;
        } else {
            relevanceInstruction = """
                    Focus on papers marked as topically relevant. You may briefly mention partially
                    relevant papers (isAtAllRelevant=true but isTopicallyRelevant=false) for
                    methodological context only.
                    """;
        }

        String prompt = String.format("""
                CRITICAL: Respond with ONLY a valid JSON object. No other text. ONLY the JSON.

                Write a "Related Work" section (3-5 paragraphs, ~300-500 words) for this research idea.
                Use formal academic English with in-text citations [1],[2] etc. matching paper index numbers.

                %s

                IDEA: "%s"

                PAPERS (with relevance flags — check isTopicallyRelevant before citing):
                %s

                GAPS: %s

                RESPOND WITH EXACTLY THIS JSON:
                {"text":"full literature review with [1][2] citations...","references":[{"number":1,"paperTitle":"...","authors":"Smith et al.","year":2024,"journal":"Journal","doi":"10.xxx"}]}
                """, relevanceInstruction, esc(ideaText), esc(papersJson),
                esc(gapAnalysis != null ? objectMapper.writeValueAsString(gapAnalysis) : "{}"));

        String raw = aiClient.call(prompt, LIT_REVIEW_MAX_TOKENS, MEDIUM_TEMP);
        return objectMapper.readValue(extractJson(raw, "literatureReview"), LiteratureReviewDTO.class);
    }

    // ═══════════════════════════════════════════════════════════════
    //  FALLBACK: programmatic gap analysis & literature review
    //  Used when AI returns empty/broken JSON
    // ═══════════════════════════════════════════════════════════════

    private boolean isGapAnalysisEmpty(GapAnalysisDTO g) {
        return (g.getSolvedAreas() == null || g.getSolvedAreas().isEmpty())
            && (g.getPartiallyAddressed() == null || g.getPartiallyAddressed().isEmpty())
            && (g.getResearchGaps() == null || g.getResearchGaps().isEmpty())
            && (g.getNoveltyScore() == null || g.getNoveltyScore() == 0);
    }

    private boolean isLitReviewEmpty(LiteratureReviewDTO l) {
        return l.getText() == null || l.getText().isBlank();
    }

    private GapAnalysisDTO buildFallbackGapAnalysis(String ideaText, List<PaperAnalysisDTO> papers) {
        List<GapAnalysisDTO.GapArea> solved = new ArrayList<>();
        List<GapAnalysisDTO.PartialArea> partial = new ArrayList<>();
        List<GapAnalysisDTO.GapItem> gaps = new ArrayList<>();
        List<String> directions = new ArrayList<>();

        if (papers.isEmpty()) {
            gaps.add(GapAnalysisDTO.GapItem.builder()
                    .gap("No papers found matching your keywords")
                    .rationale("Try broadening your search terms or adding more specific keywords")
                    .suggestedDirection("Add more detailed technical terms to your research idea description")
                    .build());
        } else {
            // Build basic analysis from paper criteria
            for (PaperAnalysisDTO paper : papers) {
                boolean hasTopicMatch = paper.getCriteria() != null && paper.getCriteria().stream()
                        .anyMatch(c -> "TOPIC_MATCH".equals(c.getCriterionName()) && Boolean.TRUE.equals(c.getValue()));
                boolean hasMethodMatch = paper.getCriteria() != null && paper.getCriteria().stream()
                        .anyMatch(c -> "METHOD_RELEVANT".equals(c.getCriterionName()) && Boolean.TRUE.equals(c.getValue()));

                if (hasTopicMatch) {
                    solved.add(GapAnalysisDTO.GapArea.builder()
                            .area(paper.getTitle())
                            .papers(List.of(paper.getTitle()))
                            .summary("This paper addresses topics related to your research idea")
                            .build());
                }
                if (hasTopicMatch && !hasMethodMatch) {
                    partial.add(GapAnalysisDTO.PartialArea.builder()
                            .area(paper.getTitle())
                            .papers(List.of(paper.getTitle()))
                            .limitation("Related topic but different methodology — consider adapting their approach")
                            .build());
                }
            }

            // Add generic research gap
            gaps.add(GapAnalysisDTO.GapItem.builder()
                    .gap("Further investigation needed to validate your specific research angle")
                    .rationale("The existing papers cover related topics but your exact research question appears to be underexplored")
                    .suggestedDirection("Consider combining methodologies from the matched papers with your unique research perspective")
                    .build());

            directions.add("Conduct a systematic literature review focusing on your specific research question");
            directions.add("Identify methodological gaps in the existing papers and propose improvements");
        }

        directions.add("Develop a prototype or proof-of-concept to validate your approach");
        directions.add("Collaborate with domain experts to refine your research methodology");

        // Calculate a basic novelty score
        int score = papers.isEmpty() ? 75 : Math.min(85, Math.max(20, 80 - papers.size() * 10));

        return GapAnalysisDTO.builder()
                .solvedAreas(solved)
                .partiallyAddressed(partial)
                .researchGaps(gaps)
                .suggestedDirections(directions)
                .noveltyScore(score)
                .noveltyExplanation("Based on analysis of " + papers.size()
                        + " papers, your research idea shows promising novelty. "
                        + "The existing literature covers related areas but your specific angle "
                        + "appears to be underexplored, suggesting room for original contribution.")
                .build();
    }

    private LiteratureReviewDTO buildFallbackLitReview(String ideaText,
                                                        List<PaperAnalysisDTO> papers,
                                                        GapAnalysisDTO gapAnalysis) {
        // Filter: only cite papers that are at least partially relevant
        List<PaperAnalysisDTO> relevantPapers = papers.stream()
                .filter(p -> p.getCriteria() != null && p.getCriteria().stream()
                        .anyMatch(c -> Boolean.TRUE.equals(c.getValue())))
                .collect(Collectors.toList());

        boolean noRelevantPapers = relevantPapers.isEmpty();

        StringBuilder sb = new StringBuilder();

        if (noRelevantPapers) {
            // No relevant papers found — don't cite them as foundations
            sb.append("A targeted literature search was conducted to identify prior work "
                    + "directly related to this research topic. ");
            sb.append("The initial search did not return papers with strong topical alignment, "
                    + "suggesting that this research direction occupies a relatively novel or "
                    + "underexplored niche within the broader field. ");
            if (gapAnalysis != null && gapAnalysis.getResearchGaps() != null
                    && !gapAnalysis.getResearchGaps().isEmpty()) {
                sb.append("Specifically, ");
                sb.append(gapAnalysis.getResearchGaps().get(0).getGap()).append(". ");
            }
            sb.append("A more comprehensive search across specialized databases and venues "
                    + "(e.g., IEEE Xplore, ACM Digital Library, SpringerLink) is recommended "
                    + "to fully map the existing literature landscape. ");
            sb.append("This work aims to establish foundational knowledge in an area where "
                    + "consolidated academic literature is currently sparse.");
        } else {
            // We have relevant papers — cite them normally
            sb.append("The research landscape surrounding this topic has been shaped by several key contributions. ");
            sb.append("Recent work in this area includes ");
            for (int i = 0; i < relevantPapers.size(); i++) {
                PaperAnalysisDTO p = relevantPapers.get(i);
                if (i > 0 && i == relevantPapers.size() - 1) sb.append("and ");
                sb.append(p.getTitle());
                sb.append(" [").append(i + 1).append("]");
                if (i < relevantPapers.size() - 1) sb.append(", ");
            }
            sb.append(". ");

            if (gapAnalysis != null && gapAnalysis.getResearchGaps() != null
                    && !gapAnalysis.getResearchGaps().isEmpty()) {
                sb.append("However, several gaps remain. ");
                sb.append(gapAnalysis.getResearchGaps().get(0).getGap()).append(". ");
            }

            sb.append("This work aims to address these gaps by building upon the existing "
                    + "foundations while introducing novel approaches to the problem.");
        }

        // Build references ONLY from relevant papers (or empty if none)
        List<LiteratureReviewDTO.Reference> refs = new ArrayList<>();
        for (int i = 0; i < relevantPapers.size(); i++) {
            PaperAnalysisDTO p = relevantPapers.get(i);
            refs.add(LiteratureReviewDTO.Reference.builder()
                    .number(i + 1)
                    .paperTitle(p.getTitle())
                    .authors("See paper for author list")
                    .year(2024)
                    .journal("See paper for journal")
                    .doi("")
                    .build());
        }

        return LiteratureReviewDTO.builder()
                .text(sb.toString())
                .references(refs)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    //  PAPER EVALUATION WITH 3-TIER CACHE
    // ═══════════════════════════════════════════════════════════════

    private PaperAnalysisDTO evaluatePaper(PaperDetailResponseDTO paper, String ideaText, String ideaHash) {
        String evalKey = paper.getPaperId() + ":" + ideaHash;

        // ── L1: RAM cache ──
        CacheEntry<List<CriterionResult>> ramCached = evalCache.get(evalKey);
        if (ramCached != null && !ramCached.isExpired()) {
            log.info("Paper eval L1 cache HIT for paper {}", paper.getPaperId());
            return buildPaperDTO(paper, ramCached.data);
        }

        // ── L2: DB cache (cross-user) ──
        Optional<PaperEvaluationCache> dbCached = paperEvalCacheRepository
                .findByPaperIdAndIdeaHash(paper.getPaperId(), ideaHash);
        if (dbCached.isPresent()) {
            try {
                List<CriterionResult> criteria = objectMapper.readValue(
                        dbCached.get().getCriteriaJson(),
                        new TypeReference<List<CriterionResult>>() {});
                evalCache.put(evalKey, new CacheEntry<>(criteria, EVAL_CACHE_TTL_MS));
                log.info("Paper eval L2 cache HIT for paper {}", paper.getPaperId());
                return buildPaperDTO(paper, criteria);
            } catch (Exception e) {
                log.warn("Failed to parse cached criteria: {}", e.getMessage());
            }
        }

        // ── L3: AI evaluation ──
        try {
            // Download & extract PDF text (in-memory only)
            String pdfText = null;
            if (paper.getPdfUrl() != null && !paper.getPdfUrl().isBlank()) {
                pdfText = pdfExtractionService.downloadAndExtract(paper.getPdfUrl());
            }

            List<CriterionResult> criteria = aiEvaluatePaper(paper, ideaText, pdfText);

            // Store in L1 RAM
            evalCache.put(evalKey, new CacheEntry<>(criteria, EVAL_CACHE_TTL_MS));

            // Store in L2 DB (async-friendly)
            try {
                PaperEvaluationCache cacheEntity = PaperEvaluationCache.builder()
                        .paperId(paper.getPaperId())
                        .ideaHash(ideaHash)
                        .criteriaJson(toJson(criteria))
                        .build();
                paperEvalCacheRepository.save(cacheEntity);
            } catch (Exception e) {
                log.warn("Failed to save paper evaluation cache: {}", e.getMessage());
            }

            return buildPaperDTO(paper, criteria);
        } catch (Exception e) {
            log.error("AI paper evaluation failed for paper {}: {}", paper.getPaperId(), e.getMessage());
            // Return N/A criteria on failure
            return buildPaperDTO(paper, buildNaCriteria());
        }
    }

    private PaperAnalysisDTO buildPaperDTO(PaperDetailResponseDTO paper, List<CriterionResult> criteria) {
        return PaperAnalysisDTO.builder()
                .paperId(paper.getPaperId())
                .title(paper.getTitle())
                .pdfUrl(paper.getPdfUrl())
                .abstractText(paper.getAbstractText())
                .criteria(criteria)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    //  UTILITIES
    // ═══════════════════════════════════════════════════════════════

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.trim().toLowerCase().hashCode());
        }
    }

    /**
     * Extract a JSON object from an AI response that may contain surrounding text.
     * <p>
     * Tries multiple strategies in order:
     * <ol>
     *   <li>Strip ```json / ``` fences</li>
     *   <li>Strip bare ``` fences</li>
     *   <li>Find text between first '{' and last '}'</li>
     *   <li>If no JSON found at all → log warning and return "{}"</li>
     * </ol>
     *
     * @param raw     raw AI response text
     * @param context label for log messages (e.g. "extractKeywords")
     * @return JSON string (guaranteed to at least be "{}")
     */
    private String extractJson(String raw, String context) {
        if (raw == null || raw.isBlank()) {
            log.warn("[{}] AI returned empty response, using {}", context, "{}");
            return "{}";
        }
        String trimmed = raw.trim();

        // Strategy 1: ```json ... ``` fence
        if (trimmed.contains("```json")) {
            int start = trimmed.indexOf("```json") + 7;
            int end = trimmed.indexOf("```", start);
            if (end > start) {
                trimmed = trimmed.substring(start, end).trim();
            }
        }
        // Strategy 2: bare ``` ... ``` fence
        else if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                trimmed = trimmed.substring(start + 1, end).trim();
            }
        }

        // Strategy 3: Find outermost { ... }
        int braceStart = trimmed.indexOf('{');
        int braceEnd = trimmed.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return trimmed.substring(braceStart, braceEnd + 1);
        }

        // Strategy 4: No JSON found — log and return empty object
        String preview = raw.length() > 200 ? raw.substring(0, 200) + "..." : raw;
        log.warn("[{}] AI response contains no JSON. Raw preview: {}", context, preview);
        return "{}";
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("JSON serialization failed: {}", e.getMessage());
            return "{}";
        }
    }

    private List<String> parseJsonStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    // ── Cache entry with TTL ──
    private static class CacheEntry<T> {
        final T data;
        final long expiryTime;

        CacheEntry(T data, long ttlMs) {
            this.data = data;
            this.expiryTime = System.currentTimeMillis() + ttlMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }
}
