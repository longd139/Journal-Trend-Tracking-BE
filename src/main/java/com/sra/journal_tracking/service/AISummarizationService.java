package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.ai.BatchAnalysisResponseDTO.PaperSummaryItem;
import com.sra.journal_tracking.dto.paper.PaperDetailResponseDTO.AiSummarySection;
import com.sra.journal_tracking.entity.jpa.ResearchPaper;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI-powered summarization, methodology extraction, and batch paper analysis
 * using DeepSeek (via {@link AIClient}).
 * <p>
 * <b>Persistence strategy:</b>
 * <ul>
 *   <li>L1: In-memory cache (1-hour TTL) — fastest path</li>
 *   <li>L2: RESEARCH_PAPER table (AiSummary, Methodology columns) — survives restarts</li>
 *   <li>On cache miss → check DB → if present, hydrate cache + return</li>
 *   <li>On DB miss → call AI → persist to DB → hydrate cache → return</li>
 * </ul>
 * When the AI provider is unavailable, the service gracefully returns null.
 */
@Slf4j
@Service
public class AISummarizationService {

    private static final long CACHE_TTL_MS = 60 * 60 * 1000; // 1 hour
    private static final int MAX_ABSTRACT_CHARS = 1500;
    private static final int MAX_BATCH_PAPERS = 10;
    private static final int SUMMARY_MAX_TOKENS = 1536;
    private static final int METHODOLOGY_MAX_TOKENS = 64;
    private static final int BATCH_MAX_TOKENS = 4096;
    private static final double SUMMARY_TEMPERATURE = 0.3;
    private static final double METHODOLOGY_TEMPERATURE = 0.2;
    private static final double BATCH_TEMPERATURE = 0.4;

    // ── In-memory caches: 1 hour TTL ──

    private static class CacheEntry<T> {
        final T data;
        final long expiryTime;

        CacheEntry(T data) {
            this.data = data;
            this.expiryTime = System.currentTimeMillis() + CACHE_TTL_MS;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }

    private final ConcurrentHashMap<String, CacheEntry<String>> summaryCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry<String>> methodologyCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry<BatchAnalysisResult>> batchCache = new ConcurrentHashMap<>();

    private final AIClient aiClient;
    private final ResearchPaperRepository researchPaperRepository;

    public AISummarizationService(AIClient aiClient,
                                  ResearchPaperRepository researchPaperRepository) {
        this.aiClient = aiClient;
        this.researchPaperRepository = researchPaperRepository;
    }

    // ═══════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Generate a 2-3 sentence summary of a paper's abstract.
     * Checks L1 cache → L2 DB → AI generation → persist to DB.
     *
     * @param paperId the paper to summarize
     * @return AI-generated summary, or null if AI is unavailable or abstract is empty
     */
    public String summarizeAbstract(UUID paperId) {
        String cacheKey = "summary:" + paperId.toString();

        // ── L1: In-memory cache ──
        CacheEntry<String> cached = summaryCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.info("CACHE HIT: summary for paper {}", paperId);
            return cached.data;
        }
        if (cached != null) {
            summaryCache.remove(cacheKey);
        }

        // ── L2: Check DB ──
        ResearchPaper paper = researchPaperRepository.findById(paperId).orElse(null);
        if (paper == null) {
            log.debug("Skipping summarization: paper {} not found in DB", paperId);
            return null;
        }
        if (!isBlank(paper.getAiSummary())) {
            log.info("DB HIT: summary for paper {}", paperId);
            summaryCache.put(cacheKey, new CacheEntry<>(paper.getAiSummary()));
            return paper.getAiSummary();
        }
        if (isBlank(paper.getAbstractText())) {
            log.debug("Skipping summarization for paper {}: no abstract", paperId);
            return null;
        }

        // ── L3: Generate with AI + persist ──
        return summarizeAndPersist(paper, cacheKey);
    }

    /**
     * Generate summary using abstract text directly + persist to DB.
     */
    @Transactional
    public String summarizeAbstract(UUID paperId, String abstractText) {
        String cacheKey = "summary:" + paperId.toString();
        CacheEntry<String> cached = summaryCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) { return cached.data; }
        if (cached != null) { summaryCache.remove(cacheKey); }

        // Check DB first
        ResearchPaper paper = researchPaperRepository.findById(paperId).orElse(null);
        if (paper != null && !isBlank(paper.getAiSummary())) {
            summaryCache.put(cacheKey, new CacheEntry<>(paper.getAiSummary()));
            return paper.getAiSummary();
        }

        if (isBlank(abstractText)) { return null; }

        return summarizeAndPersist(paperId, abstractText, cacheKey);
    }

    /** Generate summary via AI and persist to RESEARCH_PAPER. */
    private String summarizeAndPersist(ResearchPaper paper, String cacheKey) {
        String result = callAiWithFallback(
                buildSummarizePrompt(truncateAbstract(paper.getAbstractText())),
                SUMMARY_MAX_TOKENS, SUMMARY_TEMPERATURE, cacheKey, summaryCache,
                "summarization", paper.getPaperId().toString());
        if (result != null) {
            persistSummary(paper.getPaperId(), result);
        }
        return result;
    }

    /** Generate summary via AI (no DB entity available) and persist. */
    private String summarizeAndPersist(UUID paperId, String abstractText, String cacheKey) {
        String result = callAiWithFallback(
                buildSummarizePrompt(truncateAbstract(abstractText)),
                SUMMARY_MAX_TOKENS, SUMMARY_TEMPERATURE, cacheKey, summaryCache,
                "summarization", paperId.toString());
        if (result != null) {
            persistSummary(paperId, result);
        }
        return result;
    }

    /** Persist AI summary to RESEARCH_PAPER table. */
    private void persistSummary(UUID paperId, String summary) {
        try {
            researchPaperRepository.findById(paperId).ifPresent(paper -> {
                paper.setAiSummary(summary);
                researchPaperRepository.save(paper);
                log.info("DB PERSIST: summary for paper {}", paperId);
            });
        } catch (Exception e) {
            log.warn("Failed to persist summary for paper {}: {}", paperId, e.getMessage());
        }
    }

    /**
     * Parse a raw AI summary into structured sections for UI rendering.
     *
     * @param rawSummary the raw AI-generated summary text
     * @return list of sections with heading + content, or empty list if unparseable
     */
    public List<AiSummarySection> parseSummarySections(String rawSummary) {
        if (rawSummary == null || rawSummary.isBlank()) {
            return List.of();
        }

        List<AiSummarySection> sections = new ArrayList<>();
        String[] expectedHeadings = {
                "Context & Problem", "Approach", "Results", "Contribution"
        };

        for (String heading : expectedHeadings) {
            String content = extractSection(rawSummary, heading);
            sections.add(AiSummarySection.builder()
                    .heading(heading)
                    .content(content)
                    .build());
        }

        return sections;
    }

    /**
     * Extract the content under a specific heading from the AI summary.
     */
    private String extractSection(String text, String heading) {
        // Match patterns like "1. Context & Problem: ..." or "Context & Problem: ..."
        String escaped = java.util.regex.Pattern.quote(heading);
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?:\\d+\\.?\\s*)?" + escaped + "\\s*[:\\-\\n]\\s*(.*?)(?=(?:\\d+\\.?\\s*)?(?:Context & Problem|Approach|Results|Contribution)\\s*[:\\-\\n]|$)",
                java.util.regex.Pattern.DOTALL | java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(text);
        if (m.find()) {
            String content = m.group(1).trim();
            // Clean trailing whitespace/newlines
            content = content.replaceAll("\\s+", " ").trim();
            return content.isBlank() ? null : content;
        }
        return null;
    }

    /**
     * Extract the research methodology from a paper's abstract.
     * Checks L1 cache → L2 DB → AI generation → persist to DB.
     *
     * @param paperId the paper to analyze
     * @return methodology category (e.g. "RCT", "case study"), or null if unavailable
     */
    public String extractMethodology(UUID paperId) {
        String cacheKey = "methodology:" + paperId.toString();

        // ── L1: In-memory cache ──
        CacheEntry<String> cached = methodologyCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.info("CACHE HIT: methodology for paper {}", paperId);
            return cached.data;
        }
        if (cached != null) {
            methodologyCache.remove(cacheKey);
        }

        // ── L2: Check DB ──
        ResearchPaper paper = researchPaperRepository.findById(paperId).orElse(null);
        if (paper == null) {
            log.debug("Skipping methodology: paper {} not found in DB", paperId);
            return null;
        }
        if (!isBlank(paper.getMethodology())) {
            log.info("DB HIT: methodology for paper {}", paperId);
            methodologyCache.put(cacheKey, new CacheEntry<>(paper.getMethodology()));
            return paper.getMethodology();
        }
        if (isBlank(paper.getAbstractText())) {
            log.debug("Skipping methodology for paper {}: no abstract", paperId);
            return null;
        }
        return extractMethodologyAndPersist(paper, cacheKey);
    }

    /**
     * Extract methodology using abstract text directly + persist to DB.
     */
    @Transactional
    public String extractMethodology(UUID paperId, String abstractText) {
        String cacheKey = "methodology:" + paperId.toString();
        CacheEntry<String> cached = methodologyCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) { return cached.data; }
        if (cached != null) { methodologyCache.remove(cacheKey); }

        // Check DB first
        ResearchPaper paper = researchPaperRepository.findById(paperId).orElse(null);
        if (paper != null && !isBlank(paper.getMethodology())) {
            methodologyCache.put(cacheKey, new CacheEntry<>(paper.getMethodology()));
            return paper.getMethodology();
        }

        if (isBlank(abstractText)) { return null; }

        String text = truncateAbstract(abstractText);
        String result = callAiWithFallback(
                buildMethodologyPrompt(text),
                METHODOLOGY_MAX_TOKENS, METHODOLOGY_TEMPERATURE, cacheKey, methodologyCache,
                "methodology", paperId.toString());

        // Clean up: extract only the label
        if (result != null) {
            result = result.trim().replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\-() ]", "");
            if (result.length() > 80) {
                result = result.substring(0, 80);
            }
        }
        String cleaned = isBlank(result) ? null : result;

        // Persist to DB
        if (cleaned != null) {
            persistMethodology(paperId, cleaned);
        }
        return cleaned;
    }

    /** Generate methodology via AI and persist to DB. */
    private String extractMethodologyAndPersist(ResearchPaper paper, String cacheKey) {
        String text = truncateAbstract(paper.getAbstractText());
        String result = callAiWithFallback(
                buildMethodologyPrompt(text),
                METHODOLOGY_MAX_TOKENS, METHODOLOGY_TEMPERATURE, cacheKey, methodologyCache,
                "methodology", paper.getPaperId().toString());
        if (result != null) {
            result = result.trim().replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\-() ]", "");
            if (result.length() > 80) result = result.substring(0, 80);
        }
        String cleaned = isBlank(result) ? null : result;
        if (cleaned != null) {
            persistMethodology(paper.getPaperId(), cleaned);
        }
        return cleaned;
    }

    /** Persist methodology to RESEARCH_PAPER table. */
    private void persistMethodology(UUID paperId, String methodology) {
        try {
            researchPaperRepository.findById(paperId).ifPresent(paper -> {
                paper.setMethodology(methodology);
                researchPaperRepository.save(paper);
                log.info("DB PERSIST: methodology for paper {}", paperId);
            });
        } catch (Exception e) {
            log.warn("Failed to persist methodology for paper {}: {}", paperId, e.getMessage());
        }
    }

    /**
     * Analyze a batch of papers: summarize each and provide comparative insights.
     *
     * @param paperIds list of paper IDs (max 10)
     * @return batch analysis result with per-paper summaries and cross-paper insight
     */
    public BatchAnalysisResult batchAnalyze(List<UUID> paperIds) {
        if (paperIds == null || paperIds.isEmpty()) {
            return BatchAnalysisResult.builder()
                    .paperSummaries(Map.of())
                    .comparativeInsight(null)
                    .build();
        }

        // Limit to max papers
        List<UUID> safeIds = paperIds.stream()
                .distinct()
                .limit(MAX_BATCH_PAPERS)
                .collect(Collectors.toList());

        String cacheKey = "batch:" + safeIds.stream()
                .sorted()
                .map(UUID::toString)
                .collect(Collectors.joining(","));

        // Cache check
        CacheEntry<BatchAnalysisResult> cached = batchCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.info("CACHE HIT: batch analysis for {} papers", safeIds.size());
            return cached.data;
        }
        if (cached != null) {
            batchCache.remove(cacheKey);
        }

        // Fetch papers
        List<ResearchPaper> papers = safeIds.stream()
                .map(id -> researchPaperRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (papers.isEmpty()) {
            return BatchAnalysisResult.builder()
                    .paperSummaries(Map.of())
                    .comparativeInsight(null)
                    .build();
        }

        try {
            String prompt = buildBatchPrompt(papers);
            String rawResponse = aiClient.call(prompt, BATCH_MAX_TOKENS, BATCH_TEMPERATURE);
            BatchAnalysisResult result = parseBatchResponse(rawResponse, papers);

            // Cache result
            batchCache.put(cacheKey, new CacheEntry<>(result));
            log.info("CACHE STORE: batch analysis {} papers (TTL=1h)", papers.size());
            return result;
        } catch (Exception e) {
            log.warn("AI batch analysis failed: {}", e.getMessage());
            return buildEmptyBatchResult(papers);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  AI CALL WITH FALLBACK
    // ═══════════════════════════════════════════════════════════════

    /**
     * Call AI provider with graceful fallback. Returns null on any failure.
     */
    private <T> T callAiWithFallback(String prompt, int maxTokens, double temperature,
                                      String cacheKey,
                                      ConcurrentHashMap<String, CacheEntry<T>> cache,
                                      String featureName, String logId) {
        try {
            String rawResult = aiClient.call(prompt, maxTokens, temperature);
            if (isBlank(rawResult)) {
                return null;
            }
            @SuppressWarnings("unchecked")
            T result = (T) rawResult.trim();
            cache.put(cacheKey, new CacheEntry<>(result));
            log.info("CACHE STORE: {} for {} (TTL=1h)", featureName, logId);
            return result;
        } catch (Exception e) {
            log.warn("AI {} failed for {}: {}", featureName, logId, e.getMessage());
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  PROMPT BUILDERS
    // ═══════════════════════════════════════════════════════════════

    private String buildSummarizePrompt(String abstractText) {
        return String.format(
                "You are an academic research assistant. " +
                "Read the following research paper abstract and summarize it using EXACTLY these 4 headings. " +
                "Write 1-2 concise sentences under each heading.\n\n" +
                "1. Context & Problem\n" +
                "2. Approach\n" +
                "3. Results\n" +
                "4. Contribution\n\n" +
                "Rules:\n" +
                "- ONLY use the 4 headings above. Do NOT add, remove, or rename any heading.\n" +
                "- Write 1-2 sentences per heading. Be concise.\n" +
                "- Return ONLY the summary. No introduction, no commentary.\n" +
                "- Output language: English.\n\n" +
                "Abstract: \"%s\"",
                abstractText);
    }

    private String buildMethodologyPrompt(String abstractText) {
        return String.format(
                "You are an academic research assistant. Read the following research paper abstract " +
                "and identify the research methodology used. " +
                "Respond with ONLY one of these exact categories:\n" +
                "- quantitative survey\n" +
                "- qualitative interview\n" +
                "- randomized controlled trial (RCT)\n" +
                "- systematic review\n" +
                "- meta-analysis\n" +
                "- case study\n" +
                "- experimental design\n" +
                "- mixed methods\n" +
                "- computational modeling\n" +
                "- theoretical analysis\n" +
                "- literature review\n" +
                "- unknown\n\n" +
                "If the methodology is not clearly identifiable, respond with \"unknown\".\n" +
                "Return ONLY the category label, no additional text, no explanation, no punctuation.\n\n" +
                "Abstract: \"%s\"",
                abstractText);
    }

    private String buildBatchPrompt(List<ResearchPaper> papers) {
        StringBuilder papersSection = new StringBuilder();
        for (int i = 0; i < papers.size(); i++) {
            ResearchPaper paper = papers.get(i);
            String abstractText = truncateAbstract(
                    paper.getAbstractText() != null ? paper.getAbstractText() : "(no abstract available)");
            papersSection.append(String.format(
                    "Paper %d (Title: \"%s\"):\nAbstract: \"%s\"\n\n",
                    i + 1, paper.getTitle(), abstractText));
        }

        return String.format(
                "You are an academic research assistant. Analyze these %d research papers:\n\n" +
                "1. Summarize EACH paper using EXACTLY these 4 headings (1-2 sentences each):\n" +
                "   1. Context & Problem\n" +
                "   2. Approach\n" +
                "   3. Results\n" +
                "   4. Contribution\n\n" +
                "2. Add a \"COMPARATIVE INSIGHT:\" section identifying common themes, " +
                "contradictions, or research gaps across papers.\n\n" +
                "Papers:\n%s\n" +
                "Format EXACTLY as (output in English):\n" +
                "Paper 1:\n" +
                "1. Context & Problem: ...\n" +
                "2. Approach: ...\n" +
                "3. Results: ...\n" +
                "4. Contribution: ...\n\n" +
                "Paper 2:\n" +
                "1. Context & Problem: ...\n" +
                "...\n\n" +
                "COMPARATIVE INSIGHT: [cross-paper analysis]",
                papers.size(), papersSection.toString());
    }

    // ═══════════════════════════════════════════════════════════════
    //  RESPONSE PARSING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Parse the AI's batch response into structured BatchAnalysisResult.
     * Uses regex to extract per-paper summaries and comparative insight.
     * If parsing fails, returns summaries as best-effort extraction.
     */
    private BatchAnalysisResult parseBatchResponse(String rawResponse, List<ResearchPaper> papers) {
        Map<UUID, String> paperSummaries = new LinkedHashMap<>();
        String comparativeInsight = null;

        try {
            // Split by "Paper N:" pattern
            Pattern paperPattern = Pattern.compile(
                    "Paper\\s+(\\d+)\\s*:\\s*(.*?)(?=Paper\\s+\\d+\\s*:|COMPARATIVE\\s+INSIGHT\\s*:|$)",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher paperMatcher = paperPattern.matcher(rawResponse);

            while (paperMatcher.find()) {
                int paperNum;
                try {
                    paperNum = Integer.parseInt(paperMatcher.group(1));
                } catch (NumberFormatException e) {
                    continue;
                }

                String summary = paperMatcher.group(2).trim();
                // Remove trailing labels
                summary = summary.replaceAll("COMPARATIVE\\s+INSICHT.*$", "").trim();

                if (paperNum >= 1 && paperNum <= papers.size()) {
                    ResearchPaper paper = papers.get(paperNum - 1);
                    paperSummaries.put(paper.getPaperId(), isBlank(summary) ? null : summary);
                }
            }

            // Extract comparative insight
            Pattern insightPattern = Pattern.compile(
                    "COMPARATIVE\\s+INSIGHT\\s*:\\s*(.*?)$",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher insightMatcher = insightPattern.matcher(rawResponse);
            if (insightMatcher.find()) {
                String insight = insightMatcher.group(1).trim();
                // Remove any trailing "Paper N:" fragments
                insight = insight.replaceAll("Paper\\s+\\d+\\s*:.*$", "").trim();
                comparativeInsight = isBlank(insight) ? null : insight;
            }

            // Fill in any papers that were not found in the response
            for (ResearchPaper paper : papers) {
                paperSummaries.putIfAbsent(paper.getPaperId(), null);
            }
        } catch (Exception e) {
            log.warn("Failed to parse batch response: {}. Returning best-effort result.", e.getMessage());
            // Best effort: return empty summaries
            for (ResearchPaper paper : papers) {
                paperSummaries.putIfAbsent(paper.getPaperId(), null);
            }
        }

        return BatchAnalysisResult.builder()
                .paperSummaries(paperSummaries)
                .comparativeInsight(comparativeInsight)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Truncate abstract to a safe length for AI input token limits.
     */
    private String truncateAbstract(String abstractText) {
        if (abstractText == null || abstractText.isBlank()) {
            return "";
        }
        String cleaned = abstractText.replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= MAX_ABSTRACT_CHARS) {
            return cleaned;
        }
        return cleaned.substring(0, MAX_ABSTRACT_CHARS) + "...";
    }

    /**
     * Build an empty batch result with all paper IDs mapped to null summaries.
     */
    private BatchAnalysisResult buildEmptyBatchResult(List<ResearchPaper> papers) {
        Map<UUID, String> summaries = new LinkedHashMap<>();
        for (ResearchPaper paper : papers) {
            summaries.put(paper.getPaperId(), null);
        }
        return BatchAnalysisResult.builder()
                .paperSummaries(summaries)
                .comparativeInsight(null)
                .build();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
