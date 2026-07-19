package com.sra.journal_tracking.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

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
        List<Map<String, String>> conversation = new ArrayList<>();

        String systemPrompt = buildSystemPrompt();
        String userMessage = """
                A researcher described their interest:
                "%s"

                Use match_user_idea FIRST to check which keywords exist in the system.
                Then follow the fallback flow based on the matchLevel.
                Suggest up to 3 keyword pairs for gap analysis if possible.
                Only suggest keywords that exist in the system.
                """.formatted(userIdea);

        conversation.add(Map.of("role", "system", "content", systemPrompt));
        conversation.add(Map.of("role", "user", "content", userMessage));

        int iteration = 0;
        while (iteration < MAX_ITERATIONS) {
            iteration++;
            String aiResponse = callAI(conversation);
            McpAction action = parseAction(aiResponse);

            if (action == null || action.type.equals("final_answer")) {
                McpResponse response = new McpResponse(
                        action != null ? action.content : aiResponse,
                        parseSuggestions(aiResponse)
                );
                enrichResponseFromJson(response, action != null ? action.content : aiResponse);
                return response;
            }

            // Execute tool
            String toolResult = executeTool(action.tool, action.params);
            conversation.add(Map.of("role", "assistant", "content", aiResponse));
            conversation.add(Map.of("role", "user", "content",
                    "Tool result for %s: %s".formatted(action.tool, toolResult)));
        }

        return new McpResponse("Unable to generate suggestions. Please try again.", List.of());
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
            return aiClient.call(prompt.toString(), 2048, 0.3);
        } catch (Exception e) {
            log.error("AI call failed in MCP orchestrator: {}", e.getMessage());
            return "FINAL_ANSWER: {\"suggestions\":[]}";
        }
    }

    private McpAction parseAction(String aiResponse) {
        if (aiResponse == null) return null;

        String text = aiResponse.trim();

        // Check for TOOL_CALL
        if (text.contains("TOOL_CALL:")) {
            String json = text.substring(text.indexOf("TOOL_CALL:") + 10).trim();
            // Extract JSON object
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
                log.warn("Failed to parse TOOL_CALL: {}", e.getMessage());
            }
        }

        // Check for FINAL_ANSWER
        if (text.contains("FINAL_ANSWER:")) {
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

        // Treat as final answer
        McpAction action = new McpAction();
        action.type = "final_answer";
        action.content = text;
        return action;
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
}
