package com.sra.journal_tracking.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sra.journal_tracking.dto.gap.KeywordMatchResult;
import com.sra.journal_tracking.dto.gap.FuzzyMatchSuggestion;
import com.sra.journal_tracking.dto.gap.KeywordLandscapeItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple MCP-style orchestrator that lets DeepSeek AI call Neo4j tools
 * to answer user questions about research gaps.
 * <p>
 * Flow: User asks → AI decides which tool(s) to call → BE executes →
 * AI gets results → AI answers. Loop ≤ 5 iterations.
 */
@Service
public class McpOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(McpOrchestrator.class);
    private static final int MAX_ITERATIONS = 5;

    // Cache suggest results for 10 min — same idea → instant response
    private final ConcurrentHashMap<String, McpResponse> suggestCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> suggestCacheExpiry = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 10 * 60 * 1000;

    private final AIClient aiClient;
    private final GraphExplorerService graphExplorer;
    private final KeywordMatchingService keywordMatchingService;
    private final ObjectMapper objectMapper;

    public McpOrchestrator(AIClient aiClient, GraphExplorerService graphExplorer,
                            KeywordMatchingService keywordMatchingService,
                            ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.graphExplorer = graphExplorer;
        this.keywordMatchingService = keywordMatchingService;
        this.objectMapper = objectMapper;
    }

    /**
     * Process a user's idea description and suggest keyword pairs for gap analysis.
     * AI can call tools to explore the available data before making suggestions.
     */
    public McpResponse suggestKeywordPairs(String userIdea) {
        // Check cache — same idea → instant response
        String cacheKey = userIdea.trim().toLowerCase();
        Long expiry = suggestCacheExpiry.get(cacheKey);
        if (expiry != null && System.currentTimeMillis() < expiry) {
            log.info("Returning cached suggest result for '{}'", cacheKey);
            return suggestCache.get(cacheKey);
        }

        // Step 1: Deterministic keyword matching FIRST (not via AI)
        KeywordMatchResult matchResult = keywordMatchingService.matchIdea(userIdea);
        log.info("Keyword match for '{}': level={}, matched={}, unmatched={}",
                userIdea.length() > 30 ? userIdea.substring(0, 30) + "..." : userIdea,
                matchResult.getMatchLevel(),
                matchResult.getExactMatches() != null ? matchResult.getExactMatches().size() : 0,
                matchResult.getUnmatchedTerms() != null ? matchResult.getUnmatchedTerms().size() : 0);

        // If no keywords matched at all, still let AI explore with list_keywords()
        // (don't short-circuit — the AI can discover related keywords the user meant)
        if (matchResult.getMatchLevel() == KeywordMatchResult.MatchLevel.NONE
                || matchResult.getMatchLevel() == KeywordMatchResult.MatchLevel.FUZZY_ONLY) {
            // Don't cache — result may change after crawl
            return exploreWithAI(matchResult, userIdea);
        }

        // Step 2: Use AI to suggest gap pairs from matched keywords
        List<Map<String, String>> conversation = new ArrayList<>();

        List<String> matchedTexts = matchResult.getExactMatches() != null
                ? matchResult.getExactMatches().stream()
                    .map(KeywordMatchResult.KeywordExactMatch::getText)
                    .toList()
                : List.of();
        String matchedKeywordsJson = toJson(matchedTexts);
        String systemPrompt = buildSystemPrompt();
        String userMessage = """
                A researcher described their interest:
                "%s"

                Pre-computed keyword matching result (already verified to exist in the database):
                %s

                Based on these matched keywords, suggest up to 3 keyword pairs for gap analysis.
                IMPORTANT: If only 1 keyword matched, call get_cooccurring() on it FIRST
                to discover related keywords to pair with. Then call get_gap_score() for each pair.
                Only suggest pairs using keywords that exist in the database.
                """.formatted(userIdea, matchedKeywordsJson);

        conversation.add(Map.of("role", "system", "content", systemPrompt));
        conversation.add(Map.of("role", "user", "content", userMessage));

        int iteration = 0;
        while (iteration < MAX_ITERATIONS) {
            iteration++;
            log.info("MCP iteration {}/{} — calling AI...", iteration, MAX_ITERATIONS);
            String aiResponse = callAI(conversation);
            log.info("MCP AI response ({} chars): {}",
                    aiResponse.length(),
                    aiResponse.length() > 200 ? aiResponse.substring(0, 200) + "..." : aiResponse);
            McpAction action = parseAction(aiResponse);
            log.info("MCP parsed action: type={}, tool={}",
                    action != null ? action.type : "null",
                    action != null ? action.tool : "—");

            if (action == null || action.type.equals("final_answer")) {
                List<McpSuggestion> suggestions = parseSuggestions(aiResponse);
                log.info("MCP final answer: {} suggestions parsed", suggestions.size());
                McpResponse response = new McpResponse(
                        action != null ? action.content : aiResponse,
                        suggestions
                );
                enrichResponseFromJson(response, action != null ? action.content : aiResponse);
                // Always include match result for fallback UI
                response.matchLevel = matchResult.getMatchLevel().name();
                response.unmatchedTerms = matchResult.getUnmatchedTerms();
                response.fuzzyCandidates = convertFuzzyCandidates(matchResult.getFuzzySuggestions());
                suggestCache.put(cacheKey, response);
                suggestCacheExpiry.put(cacheKey, System.currentTimeMillis() + CACHE_TTL_MS);
                return response;
            }

            // Execute tool
            String toolResult = executeTool(action.tool, action.params);
            log.info("MCP tool {} executed: {} chars result", action.tool,
                    toolResult != null ? toolResult.length() : 0);
            conversation.add(Map.of("role", "assistant", "content", aiResponse));
            conversation.add(Map.of("role", "user", "content",
                    "Tool result for %s: %s".formatted(action.tool, toolResult)));
        }

        McpResponse response = buildDirectMatchResponse(matchResult, userIdea);
        suggestCache.put(cacheKey, response);
        suggestCacheExpiry.put(cacheKey, System.currentTimeMillis() + CACHE_TTL_MS);
        return response;
    }

    @SuppressWarnings("unchecked")
    private void enrichResponseFromJson(McpResponse response, String aiResponse) {
        try {
            String json = aiResponse;
            if (json.contains("FINAL_ANSWER:")) {
                json = json.substring(json.indexOf("FINAL_ANSWER:") + 13).trim();
            }
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }

            Map<String, Object> map = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});

            response.message = (String) map.getOrDefault("message",
                    map.getOrDefault("guidanceMessage", null));
            response.matchLevel = (String) map.getOrDefault("matchLevel", "FULL");

            List<Map<String, Object>> fuzzy = (List<Map<String, Object>>) map.get("fuzzyCandidates");
            response.fuzzyCandidates = fuzzy != null ? fuzzy : List.of();

            List<Map<String, Object>> landscape = (List<Map<String, Object>>) map.get("availableLandscape");
            response.availableLandscape = landscape != null ? landscape : List.of();

            List<String> unmatched = (List<String>) map.get("unmatchedTerms");
            response.unmatchedTerms = unmatched != null ? unmatched : List.of();

            response.canCrawl = Boolean.TRUE.equals(map.get("canCrawl"));
            @SuppressWarnings("unchecked")
            List<String> crawlKw = (List<String>) map.get("crawlKeywords");
            response.crawlKeywords = crawlKw != null ? crawlKw : List.of();

        } catch (Exception e) {
            log.debug("Could not enrich response with extra fields: {}", e.getMessage());
        }
    }

    private String buildSystemPrompt() {
        return """
                You are a research advisor helping a researcher discover research gaps.
                You have access to the following tools:

                1. list_keywords()
                   → Returns ALL available keywords with their text.
                   Use this first to see what's available.

                2. get_cooccurring(keyword: string)
                   → Returns keywords that co-occur with the given keyword + paper counts.

                3. get_gap_score(keywordA: string, keywordB: string)
                   → Returns {aOnly, bOnly, overlap, gapScore} for a keyword pair.
                   gapScore 0-100: higher = bigger gap opportunity.

                IMPORTANT:
                - Call list_keywords() first, then explore with get_cooccurring(), then check gap scores.
                - Only suggest keywords that exist in the system.
                - Suggest 3 keyword pairs with the most interesting research gaps.
                - Always respond in this format:

                TOOL_CALL: {"tool": "list_keywords", "params": {}}

                FINAL_ANSWER: {"suggestions":[{"keywordA":"...","keywordB":"...","reason":"...","gapScore":N}]}
                """;
    }

    private String callAI(List<Map<String, String>> conversation) {
        // Build prompt from conversation
        StringBuilder prompt = new StringBuilder();
        for (Map<String, String> msg : conversation) {
            String role = msg.get("role");
            String content = msg.get("content");
            if ("system".equals(role)) {
                prompt.append(content).append("\n\n");
            } else if ("user".equals(role)) {
                prompt.append("USER: ").append(content).append("\n\n");
            } else if ("assistant".equals(role)) {
                prompt.append("ASSISTANT: ").append(content).append("\n\n");
            }
        }
        prompt.append("ASSISTANT: ");

        try {
            String result = aiClient.call(prompt.toString(), 2048, 0.3);
            log.info("AI call succeeded: {} chars returned", result != null ? result.length() : 0);
            return result;
        } catch (Exception e) {
            log.error("AI call failed in MCP orchestrator: {} ({})", e.getMessage(), e.getClass().getSimpleName());
            return "FINAL_ANSWER: {\"suggestions\":[]}";
        }
    }

    private McpAction parseAction(String aiResponse) {
        if (aiResponse == null) return null;

        String text = aiResponse.trim();

        // Check for TOOL_CALL
        if (text.contains("TOOL_CALL:")) {
            McpAction action = parseToolCallJson(text);
            if (action != null) return action;
        }

        // Check for FINAL_ANSWER
        if (text.contains("FINAL_ANSWER:")) {
            McpAction action = parseFinalAnswerJson(text);
            if (action != null) return action;
        }

        // ── Fallback: AI spoke in natural language ──
        // Try to extract tool call from natural language (e.g. "Let's call get_cooccurring...")
        McpAction nlAction = parseNaturalLanguageToolCall(text);
        if (nlAction != null) {
            log.info("Extracted tool call from natural language: {}", nlAction.tool);
            return nlAction;
        }

        // Treat as final answer
        McpAction action = new McpAction();
        action.type = "final_answer";
        action.content = text;
        return action;
    }

    /** Parse TOOL_CALL: {"tool": "...", "params": {...}} */
    private McpAction parseToolCallJson(String text) {
        String json = text.substring(text.indexOf("TOOL_CALL:") + 10).trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});
            McpAction action = new McpAction();
            action.type = "tool_call";
            action.tool = (String) map.get("tool");
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) map.getOrDefault("params", Map.of());
            action.params = params;
            return action;
        } catch (Exception e) {
            log.warn("Failed to parse TOOL_CALL JSON: {}", e.getMessage());
        }
        return null;
    }

    /** Parse FINAL_ANSWER: {"suggestions": [...]} */
    private McpAction parseFinalAnswerJson(String text) {
        String json = text.substring(text.indexOf("FINAL_ANSWER:") + 13).trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        McpAction action = new McpAction();
        action.type = "final_answer";
        action.content = json;
        return action;
    }

    /**
     * Detect natural-language tool calls like:
     * "Let's call get_cooccurring for \"Internet of Things\""
     * "I'll use list_keywords() to see what's available"
     */
    private McpAction parseNaturalLanguageToolCall(String text) {
        String lower = text.toLowerCase();

        // Match tool names mentioned in natural language
        for (String tool : List.of("list_keywords", "get_cooccurring", "get_gap_score", "match_user_idea")) {
            if (lower.contains(tool)) {
                McpAction action = new McpAction();
                action.type = "tool_call";
                action.tool = tool;
                action.params = new java.util.LinkedHashMap<>();

                // Extract keyword param from quoted text (e.g. "Internet of Things")
                if (tool.equals("get_cooccurring") || tool.equals("get_gap_score")) {
                    // Try to find quoted keyword
                    int q1 = text.indexOf('"');
                    int q2 = text.indexOf('"', q1 + 1);
                    if (q1 >= 0 && q2 > q1) {
                        action.params.put("keyword", text.substring(q1 + 1, q2));
                    }
                    // Try second keyword for get_gap_score
                    if (tool.equals("get_gap_score")) {
                        int q3 = text.indexOf('"', q2 + 1);
                        int q4 = text.indexOf('"', q3 + 1);
                        if (q3 >= 0 && q4 > q3) {
                            action.params.put("keywordA", action.params.get("keyword"));
                            action.params.put("keywordB", text.substring(q3 + 1, q4));
                        }
                    }
                }
                return action;
            }
        }
        return null;
    }

    private String executeTool(String tool, Map<String, Object> params) {
        try {
            return switch (tool) {
                case "list_keywords" -> {
                    List<Map<String, Object>> keywords = graphExplorer.listAllKeywords();
                    yield objectMapper.writeValueAsString(keywords);
                }
                case "get_cooccurring" -> {
                    String kw = (String) params.getOrDefault("keyword", "");
                    List<Map<String, Object>> results = graphExplorer.getCooccurringKeywords(kw, 3);
                    yield objectMapper.writeValueAsString(results);
                }
                case "get_gap_score" -> {
                    String kwA = (String) params.getOrDefault("keywordA", "");
                    String kwB = (String) params.getOrDefault("keywordB", "");
                    Map<String, Object> score = graphExplorer.getGapScore(kwA, kwB);
                    yield objectMapper.writeValueAsString(score);
                }
                case "match_user_idea" -> {
                    String ideaText = (String) params.getOrDefault("ideaText", "");
                    if (ideaText.isBlank()) {
                        yield "{\"error\": \"ideaText is required\"}";
                    }
                    com.sra.journal_tracking.dto.gap.KeywordMatchResult matchResult =
                            keywordMatchingService.matchIdea(ideaText);
                    yield objectMapper.writeValueAsString(matchResult);
                }
                default -> "{\"error\": \"Unknown tool: " + tool + "\"}";
            };
        } catch (Exception e) {
            log.warn("Tool execution failed for {}: {}", tool, e.getMessage());
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    @SuppressWarnings("unchecked")
    private List<McpSuggestion> parseSuggestions(String aiResponse) {
        try {
            String json = aiResponse;
            if (json.contains("FINAL_ANSWER:")) {
                json = json.substring(json.indexOf("FINAL_ANSWER:") + 13).trim();
            }
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }

            Map<String, Object> map = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> suggestions = (List<Map<String, Object>>)
                    map.getOrDefault("suggestions", List.of());

            return suggestions.stream().map(s -> {
                McpSuggestion sug = new McpSuggestion();
                sug.keywordA = (String) s.get("keywordA");
                sug.keywordB = (String) s.get("keywordB");
                sug.reason = (String) s.get("reason");
                Object gapObj = s.get("gapScore");
                sug.gapScore = gapObj instanceof Number ? ((Number) gapObj).intValue() : 0;
                return sug;
            }).toList();
        } catch (Exception e) {
            log.warn("Failed to parse suggestions: {}", e.getMessage());
            return List.of();
        }
    }

    // ── Inner types ──

    public static class McpResponse {
        public String rawText;
        public List<McpSuggestion> suggestions;
        public String message;
        public String matchLevel;
        public List<Map<String, Object>> fuzzyCandidates;
        public List<Map<String, Object>> availableLandscape;
        public List<String> unmatchedTerms;
        public boolean canCrawl;
        public List<String> crawlKeywords;

        public McpResponse() {}

        public McpResponse(String rawText, List<McpSuggestion> suggestions) {
            this.rawText = rawText;
            this.suggestions = suggestions;
        }
    }

    public static class McpSuggestion {
        public String keywordA;
        public String keywordB;
        public String reason;
        public int gapScore;
    }

    private static class McpAction {
        String type;     // "tool_call" or "final_answer"
        String tool;
        Map<String, Object> params;
        String content;
    }

    // ── Direct match helpers (deterministic, bypasses AI for keyword matching) ──

    /**
     * When deterministic keyword matching fails (NONE or FUZZY_ONLY),
     * let the AI explore available keywords and find related ones.
     * The AI calls list_keywords() to discover what exists, then suggests
     * related pairs even if exact terms weren't matched.
     */
    private McpResponse exploreWithAI(KeywordMatchResult matchResult, String userIdea) {
        List<Map<String, String>> conversation = new ArrayList<>();

        String unmatchedInfo = matchResult.getUnmatchedTerms() != null
                && !matchResult.getUnmatchedTerms().isEmpty()
                ? "The user mentioned these terms that don't exactly match: "
                    + String.join(", ", matchResult.getUnmatchedTerms())
                : "No terms from the user's idea matched exactly.";

        String explorePrompt = """
                You are a research advisor helping a researcher discover research gaps.
                You have access to the following tools:

                1. list_keywords()
                   → Returns ALL available keywords with their text and paper counts.
                   Use this FIRST to see what's available.

                2. get_cooccurring(keyword: string)
                   → Returns keywords that co-occur with the given keyword + paper counts.

                3. get_gap_score(keywordA: string, keywordB: string)
                   → Returns {aOnly, bOnly, overlap, gapScore} for a keyword pair.
                   gapScore 0-100: higher = bigger gap opportunity.

                IMPORTANT CONTEXT:
                A researcher is interested in: \"%s\"
                %s
                The researcher's exact terms didn't match any keyword in our system.
                Your job: call list_keywords() first, look for terms that are
                SEMANTICALLY RELATED to what the user wants (not just exact text match),
                then suggest interesting gap pairs.

                Examples of semantic matching:
                - "IOT" → look for "internet of things", "iot devices", "embedded systems"
                - "self driving" → look for "autonomous vehicles", "autonomous driving"
                - "blockchain" → look for "distributed ledger", "smart contracts"

                Always respond in this format:

                TOOL_CALL: {"tool": "list_keywords", "params": {}}

                FINAL_ANSWER: {"suggestions":[{"keywordA":"...","keywordB":"...","reason":"...","gapScore":N}]}
                """.formatted(userIdea, unmatchedInfo);

        conversation.add(Map.of("role", "system", "content", explorePrompt));
        conversation.add(Map.of("role", "user", "content",
                "Explore the available keywords and suggest pairs related to: " + userIdea));

        int iteration = 0;
        while (iteration < MAX_ITERATIONS) {
            iteration++;
            String aiResponse = callAI(conversation);
            McpAction action = parseAction(aiResponse);

            if (action == null || action.type.equals("final_answer")) {
                McpResponse response = new McpResponse(
                        action != null ? action.content : aiResponse,
                        parseSuggestions(aiResponse));
                enrichResponseFromJson(response, action != null ? action.content : aiResponse);
                response.matchLevel = matchResult.getMatchLevel().name();
                response.unmatchedTerms = matchResult.getUnmatchedTerms();
                response.fuzzyCandidates = convertFuzzyCandidates(matchResult.getFuzzySuggestions());
                response.availableLandscape = matchResult.getAvailableLandscape() != null
                        ? matchResult.getAvailableLandscape().stream()
                            .map(al -> {
                                Map<String, Object> m = new java.util.LinkedHashMap<>();
                                m.put("fieldName", al.getFieldName());
                                m.put("topicName", al.getTopicName());
                                m.put("topKeywords", al.getTopKeywords());
                                return m;
                            }).toList()
                        : List.of();
                response.canCrawl = !userIdea.isBlank();
                response.crawlKeywords = (matchResult.getUnmatchedTerms() != null
                        && !matchResult.getUnmatchedTerms().isEmpty())
                        ? matchResult.getUnmatchedTerms()
                        : List.of(userIdea.trim());
                return response;
            }

            // Execute tool
            String toolResult = executeTool(action.tool, action.params);
            log.info("MCP tool {} executed: {} chars result", action.tool,
                    toolResult != null ? toolResult.length() : 0);
            conversation.add(Map.of("role", "assistant", "content", aiResponse));
            conversation.add(Map.of("role", "user", "content",
                    "Tool result for %s: %s".formatted(action.tool, toolResult)));
        }

        // Fallback — return match info with empty suggestions
        return buildDirectMatchResponse(matchResult, userIdea);
    }

    private McpResponse buildDirectMatchResponse(KeywordMatchResult matchResult, String userIdea) {
        McpResponse response = new McpResponse(
                matchResult.getGuidanceMessage() != null ? matchResult.getGuidanceMessage() : "",
                List.of()  // no suggestions yet
        );
        response.matchLevel = matchResult.getMatchLevel().name();
        response.unmatchedTerms = matchResult.getUnmatchedTerms() != null ? matchResult.getUnmatchedTerms() : List.of();
        response.fuzzyCandidates = convertFuzzyCandidates(matchResult.getFuzzySuggestions());
        response.availableLandscape = matchResult.getAvailableLandscape() != null
                ? matchResult.getAvailableLandscape().stream()
                    .map(al -> {
                        Map<String, Object> m = new java.util.LinkedHashMap<>();
                        m.put("fieldName", al.getFieldName());
                        m.put("topicName", al.getTopicName());
                        m.put("topKeywords", al.getTopKeywords());
                        return m;
                    }).toList()
                : List.of();
        response.canCrawl = (matchResult.getUnmatchedTerms() != null && !matchResult.getUnmatchedTerms().isEmpty())
                || !userIdea.isBlank();
        response.crawlKeywords = (matchResult.getUnmatchedTerms() != null && !matchResult.getUnmatchedTerms().isEmpty())
                ? matchResult.getUnmatchedTerms()
                : List.of(userIdea.trim());
        return response;
    }

    private String toJson(List<String> terms) {
        try {
            return objectMapper.writeValueAsString(terms);
        } catch (Exception e) {
            return "[]";
        }
    }

    /** Clear suggest cache for this idea — call after crawl completes */
    public void clearSuggestCache(String ideaText) {
        if (ideaText != null) {
            String key = ideaText.trim().toLowerCase();
            suggestCache.remove(key);
            suggestCacheExpiry.remove(key);
            log.debug("Cleared suggest cache for '{}'", key);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> convertFuzzyCandidates(
            List<FuzzyMatchSuggestion> fuzzySuggestions) {
        if (fuzzySuggestions == null) return List.of();
        return fuzzySuggestions.stream().map(fs -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("originalTerm", fs.getOriginalTerm());
            m.put("candidates", fs.getCandidates() != null ? fs.getCandidates().stream().map(c -> {
                Map<String, Object> cm = new java.util.LinkedHashMap<>();
                cm.put("keywordText", c.getKeywordText());
                cm.put("normalizedText", c.getNormalizedText());
                cm.put("similarity", c.getSimilarity());
                cm.put("paperCount", c.getPaperCount());
                return cm;
            }).toList() : List.of());
            return m;
        }).toList();
    }
}
