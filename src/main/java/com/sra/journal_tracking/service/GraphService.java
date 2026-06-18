package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.GraphLink;
import com.sra.journal_tracking.dto.GraphNode;
import com.sra.journal_tracking.dto.GraphResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GraphService {

    private static final Logger log = LoggerFactory.getLogger(GraphService.class);

    private final Neo4jClient neo4jClient;

    public GraphService(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    // ============================================
    //  REVERSE SEARCH: Keyword → Papers
    // ============================================

    /**
     * Tìm paper IDs từ Neo4j dựa trên keyword text.
     * Dùng CONTAINS để match gần đúng (không cần chính xác tuyệt đối).
     *
     * @param keyword từ khóa người dùng nhập
     * @return danh sách paperId (UUID string) tìm thấy trong Neo4j
     */
    public List<String> searchPapersByKeyword(String keyword) {
        String normalizedKeyword = keyword.toLowerCase().trim();

        String cypherQuery = """
                MATCH (p:Paper)-[:HAS_KEYWORD]->(k:Keyword)
                WHERE k.normalizedText CONTAINS $keyword
                RETURN DISTINCT p.paperId AS paperId, p.title AS title
                LIMIT 50
                """;

        List<String> paperIds = new ArrayList<>();

        try {
            neo4jClient.query(cypherQuery)
                    .bind(normalizedKeyword).to("keyword")
                    .fetch()
                    .all()
                    .forEach(record -> {
                        paperIds.add(record.get("paperId").toString());
                    });

            log.info("Neo4j search for '{}': found {} papers", keyword, paperIds.size());
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            log.warn("Neo4j search failed for keyword '{}': {}. Root cause: {}", keyword, e.getMessage(), root.toString());
            log.warn("Full stack trace:", e);
            // Không throw exception — để fallback xuống OpenAlex
        }

        return paperIds;
    }

    // ============================================
    //  SAVE: Paper + Keywords + Relationships
    // ============================================

    /**
     * Lưu Paper node và Keyword nodes vào Neo4j, tạo relationship HAS_KEYWORD.
     * Dùng MERGE để tránh duplicate nodes.
     *
     * @param paperId  UUID của paper (từ SQL)
     * @param title    tiêu đề bài báo
     * @param doi      DOI để deduplicate
     * @param pubYear  năm xuất bản
     * @param keywords danh sách keyword text
     */
    public void savePaperWithKeywords(String paperId, String title, String doi,
                                       Integer pubYear, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            // Vẫn lưu paper node dù không có keyword
            mergePaperOnly(paperId, title, doi, pubYear);
            return;
        }

        // MERGE từng keyword và tạo relationship
        for (String kw : keywords) {
            if (kw == null || kw.isBlank()) continue;

            String normalized = kw.toLowerCase().trim();
            String keywordId = generateKeywordId(normalized);

            String cypherQuery = """
                    MERGE (p:Paper {paperId: $paperId})
                    ON CREATE SET p.title = $title, p.doi = $doi, p.pubYear = $pubYear
                    ON MATCH SET p.title = $title, p.doi = $doi, p.pubYear = $pubYear
                    MERGE (k:Keyword {keywordId: $keywordId})
                    ON CREATE SET k.text = $keywordText, k.normalizedText = $normalizedText
                    ON MATCH SET k.text = $keywordText
                    MERGE (p)-[:HAS_KEYWORD]->(k)
                    """;

            try {
                neo4jClient.query(cypherQuery)
                        .bind(paperId).to("paperId")
                        .bind(title).to("title")
                        .bind(doi != null ? doi : "").to("doi")
                        .bind(pubYear != null ? pubYear : 0).to("pubYear")
                        .bind(keywordId).to("keywordId")
                        .bind(kw.trim()).to("keywordText")
                        .bind(normalized).to("normalizedText")
                        .run();

                log.debug("Neo4j: merged Paper({}) -[:HAS_KEYWORD]-> Keyword({})", paperId, normalized);
            } catch (Exception e) {
                log.error("Neo4j save failed for paper {} keyword '{}': {}", paperId, kw, e.getMessage());
                log.error("Full stack trace:", e);
            }
        }

        log.info("Neo4j: saved paper '{}' with {} keywords", title, keywords.size());
    }

    // ============================================
    //  FORWARD: Paper → Keywords (with sizing)
    // ============================================

    public GraphResponse getPaperKeywordGraph(String paperId) {
        String cypherQuery =
                "MATCH (p:Paper {paperId: $paperId})-[r:HAS_KEYWORD]->(k:Keyword) " +
                "OPTIONAL MATCH (k)<-[:HAS_KEYWORD]-(otherP:Paper) " +
                "RETURN p.paperId AS pId, p.title AS pTitle, " +
                "k.keywordId AS kId, k.text AS kText, " +
                "COUNT(DISTINCT otherP) AS keywordSize";

        Map<String, GraphNode> nodeMap = new HashMap<>();
        List<GraphLink> links = new ArrayList<>();

        neo4jClient.query(cypherQuery)
                .bind(paperId).to("paperId")
                .fetch()
                .all()
                .forEach(record -> {
                    String pId = record.get("pId").toString();
                    String pTitle = record.get("pTitle") != null ? record.get("pTitle").toString() : "Untitled";
                    String kId = record.get("kId").toString();
                    String kText = record.get("kText") != null ? record.get("kText").toString() : "";
                    int kwSize = record.get("keywordSize") != null ? ((Number) record.get("keywordSize")).intValue() : 0;

                    nodeMap.put(pId, GraphNode.builder()
                            .id(pId).label(pTitle).group("PAPER").size(1).build());
                    nodeMap.put(kId, GraphNode.builder()
                            .id(kId).label(kText).group("KEYWORD").size(kwSize).build());

                    links.add(new GraphLink(pId, kId, ""));
                });

        return new GraphResponse(new ArrayList<>(nodeMap.values()), links);
    }

    // ============================================
    //  CO-OCCURRENCE GRAPH: Keyword → Related Keywords
    // ============================================

    /**
     * Build a co-occurrence graph for a keyword.
     * Finds other keywords that frequently appear in the same papers
     * as the searched keyword. This reveals semantic relationships:
     *   "car" → "vehicle", "engine", "tire", "automobile"
     *
     * Graph structure:
     *   - Center: searched keyword node
     *   - Related keyword nodes (sized by co-occurrence count)
     *   - Paper nodes that connect them
     *   - HAS_KEYWORD links
     *
     * @param keyword the search keyword
     * @return GraphResponse with nodes and links
     */
    public GraphResponse getKeywordCooccurrenceGraph(String keyword) {
        String normalizedKeyword = keyword.toLowerCase().trim();

        String cypherQuery = """
                // Step 1: Find the main keyword
                MATCH (mainK:Keyword)
                WHERE mainK.normalizedText CONTAINS $keyword
                WITH mainK
                ORDER BY size(mainK.text) ASC    // prefer shorter (exact-ish) match
                LIMIT 1

                // Step 2: Find co-occurring keywords through shared papers
                MATCH (mainK)<-[:HAS_KEYWORD]-(p:Paper)-[:HAS_KEYWORD]->(relatedK:Keyword)
                WHERE relatedK <> mainK
                WITH mainK, relatedK, COUNT(DISTINCT p) AS cooccurCount
                ORDER BY cooccurCount DESC
                LIMIT 15

                // Step 3: Get papers and global keyword sizing
                MATCH (mainK)<-[:HAS_KEYWORD]-(p:Paper)-[:HAS_KEYWORD]->(relatedK)
                OPTIONAL MATCH (relatedK)<-[:HAS_KEYWORD]-(globalP:Paper)
                WITH mainK, relatedK, p, cooccurCount,
                     COUNT(DISTINCT globalP) AS globalSize
                RETURN mainK.keywordId AS mainKId, mainK.text AS mainKText,
                       relatedK.keywordId AS relatedKId, relatedK.text AS relatedKText,
                       p.paperId AS paperId, p.title AS paperTitle,
                       cooccurCount, globalSize
                LIMIT 300
                """;

        Map<String, GraphNode> nodeMap = new LinkedHashMap<>();
        List<GraphLink> links = new ArrayList<>();

        try {
            neo4jClient.query(cypherQuery)
                    .bind(normalizedKeyword).to("keyword")
                    .fetch()
                    .all()
                    .forEach(record -> {
                        String mainKId = record.get("mainKId").toString();
                        String mainKText = record.get("mainKText") != null ? record.get("mainKText").toString() : "";
                        String relatedKId = record.get("relatedKId").toString();
                        String relatedKText = record.get("relatedKText") != null ? record.get("relatedKText").toString() : "";
                        String pId = record.get("paperId").toString();
                        String pTitle = record.get("paperTitle") != null ? record.get("paperTitle").toString() : "Untitled";
                        int cooccurCount = record.get("cooccurCount") != null ? ((Number) record.get("cooccurCount")).intValue() : 0;
                        int globalSize = record.get("globalSize") != null ? ((Number) record.get("globalSize")).intValue() : 0;

                        // Main keyword node (center, biggest)
                        nodeMap.putIfAbsent(mainKId, GraphNode.builder()
                                .id(mainKId).label(mainKText).group("MAIN_KEYWORD").size(cooccurCount + 10).build());

                        // Related keyword node (sized by global frequency)
                        if (!nodeMap.containsKey(relatedKId)) {
                            nodeMap.put(relatedKId, GraphNode.builder()
                                    .id(relatedKId).label(relatedKText).group("RELATED_KEYWORD").size(Math.max(1, globalSize)).build());
                        }

                        // Paper node
                        nodeMap.putIfAbsent(pId, GraphNode.builder()
                                .id(pId).label(pTitle).group("PAPER").size(1).build());

                        // Links: Paper → Main Keyword, Paper → Related Keyword
                        links.add(new GraphLink(pId, mainKId, "HAS_KEYWORD"));
                        links.add(new GraphLink(pId, relatedKId, "HAS_KEYWORD"));
                    });

            log.info("Co-occurrence graph for '{}': {} nodes, {} links", keyword, nodeMap.size(), links.size());
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            log.warn("Co-occurrence graph failed for '{}': {}. Root cause: {}", keyword, e.getMessage(), root.toString());
            log.warn("Full stack trace:", e);
        }

        // Deduplicate links
        List<GraphLink> uniqueLinks = links.stream().distinct().collect(java.util.stream.Collectors.toList());

        return new GraphResponse(new ArrayList<>(nodeMap.values()), uniqueLinks);
    }

    /**
     * Build a keyword-only co-occurrence graph (NO paper nodes).
     * Keywords sized by paper frequency. Links = co-occurrence weight.
     *
     * Example: search "car" →
     *   "car" (50 papers) ──15── "vehicle" (30 papers)
     *   "car" (50 papers) ──10── "engine"  (25 papers)
     */
    public GraphResponse getKeywordGraph(String keyword) {
        String normalizedKeyword = keyword.toLowerCase().trim();

        String cypherQuery = """
                // Step 1: Find the main keyword (prefer exact/short match)
                MATCH (mainK:Keyword)
                WHERE mainK.normalizedText CONTAINS $keyword
                WITH mainK
                ORDER BY size(mainK.text) ASC
                LIMIT 1

                // Step 2: Find co-occurring keywords through shared papers
                MATCH (mainK)<-[:HAS_KEYWORD]-(p:Paper)-[:HAS_KEYWORD]->(relatedK:Keyword)
                WHERE relatedK <> mainK
                WITH mainK, relatedK, COUNT(DISTINCT p) AS cooccurCount

                // Step 3: Calculate global frequency for node sizing
                OPTIONAL MATCH (mainK)<-[:HAS_KEYWORD]-(mp:Paper)
                OPTIONAL MATCH (relatedK)<-[:HAS_KEYWORD]-(rp:Paper)
                WITH mainK, relatedK, cooccurCount,
                     COUNT(DISTINCT mp) AS mainSize,
                     COUNT(DISTINCT rp) AS relatedSize
                ORDER BY cooccurCount DESC
                LIMIT 50

                RETURN mainK.keywordId AS sourceId, mainK.text AS sourceText, mainSize AS sourceSize,
                       relatedK.keywordId AS targetId, relatedK.text AS targetText, relatedSize AS targetSize,
                       cooccurCount AS weight
                """;

        Map<String, GraphNode> nodeMap = new LinkedHashMap<>();
        List<GraphLink> links = new ArrayList<>();

        try {
            neo4jClient.query(cypherQuery)
                    .bind(normalizedKeyword).to("keyword")
                    .fetch()
                    .all()
                    .forEach(record -> {
                        String sourceId = record.get("sourceId").toString();
                        String sourceText = record.get("sourceText") != null ? record.get("sourceText").toString() : keyword;
                        int sourceSize = record.get("sourceSize") != null ? ((Number) record.get("sourceSize")).intValue() : 0;
                        String targetId = record.get("targetId").toString();
                        String targetText = record.get("targetText") != null ? record.get("targetText").toString() : "";
                        int targetSize = record.get("targetSize") != null ? ((Number) record.get("targetSize")).intValue() : 0;
                        int weight = record.get("weight") != null ? ((Number) record.get("weight")).intValue() : 0;

                        // Main keyword (center, biggest)
                        nodeMap.putIfAbsent(sourceId, GraphNode.builder()
                                .id(sourceId).label(sourceText).group("MAIN_KEYWORD")
                                .size(Math.max(sourceSize, targetSize + 5)).build());

                        // Related keyword (sized by its own paper frequency)
                        nodeMap.putIfAbsent(targetId, GraphNode.builder()
                                .id(targetId).label(targetText).group("KEYWORD")
                                .size(targetSize).build());

                        // Link weight = number of papers where both keywords appear
                        links.add(new GraphLink(sourceId, targetId, String.valueOf(weight)));
                    });

            log.info("Keyword-only graph for '{}': {} nodes (keywords), {} links", keyword, nodeMap.size(), links.size());
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            log.warn("Keyword graph failed for '{}': {}. Root cause: {}", keyword, e.getMessage(), root.toString());
        }

        return new GraphResponse(new ArrayList<>(nodeMap.values()), links);
    }

    // ============================================
    //  KEYWORD-ONLY GRAPH: AI-expanded + Neo4j
    // ============================================

    /**
     * Build a keyword-only graph (NO paper nodes) with multi-level expansion.
     *
     * Level 1: AI-expanded keywords from Groq (or the original keyword if AI unavailable)
     * Level 2+: Top N co-occurring keywords from Neo4j for each keyword at the previous level
     *
     * Designed for the Neo4j visualization tab.
     *
     * @param originalKeyword  the keyword the user searched
     * @param expandedKeywords list from Groq/AI (level 1)
     * @param maxDepth         maximum graph depth (1 = only Groq keywords, 2-3 = Neo4j co-occurrence)
     * @return GraphResponse with keyword nodes and co-occurrence links
     */
    public GraphResponse getKeywordOnlyGraph(String originalKeyword, List<String> expandedKeywords, int maxDepth) {
        Map<String, GraphNode> nodeMap = new LinkedHashMap<>();
        List<GraphLink> links = new ArrayList<>();

        // Track visited keywords to avoid cycles
        Set<String> visitedNormalized = new HashSet<>();
        // Track which keywords to expand at each level (only those with paperCount > 0)
        Set<String> currentLevel = new LinkedHashSet<>();

        String mainNormalized = originalKeyword.toLowerCase().trim();
        String mainKeywordId = generateKeywordId(mainNormalized);

        // ── Add center node ──
        int mainPaperCount = getKeywordPaperCount(mainNormalized);
        nodeMap.put(mainKeywordId, GraphNode.builder()
                .id(mainKeywordId)
                .label(originalKeyword.trim())
                .group("MAIN_KEYWORD")
                .size(Math.max(1, mainPaperCount))
                .paperCount(mainPaperCount)
                .tier(0)
                .build());
        visitedNormalized.add(mainNormalized);

        // ── Tier 1: Merge AI keywords + Neo4j co-occurrence ──
        // Part A: Add AI keywords (shown as AI_SUGGESTION if not in Neo4j)
        for (String kw : expandedKeywords) {
            if (kw == null || kw.isBlank()) continue;
            String normalizedKw = kw.toLowerCase().trim();
            if (visitedNormalized.contains(normalizedKw)) continue;

            String keywordId = generateKeywordId(normalizedKw);
            int paperCount = getKeywordPaperCount(normalizedKw);

            if (paperCount > 0) {
                nodeMap.putIfAbsent(keywordId, GraphNode.builder()
                        .id(keywordId).label(kw.trim()).group("KEYWORD")
                        .size(Math.max(1, paperCount)).paperCount(paperCount).tier(1).build());
                links.add(new GraphLink(mainKeywordId, keywordId,
                        String.valueOf(getCooccurrenceCount(mainNormalized, normalizedKw))));
                currentLevel.add(normalizedKw);
            } else {
                nodeMap.putIfAbsent(keywordId, GraphNode.builder()
                        .id(keywordId).label(kw.trim()).group("AI_SUGGESTION")
                        .size(1).paperCount(0).tier(1).build());
                links.add(new GraphLink(mainKeywordId, keywordId, "suggested"));
            }
            visitedNormalized.add(normalizedKw);
        }

        // Part B: Always ALSO add Neo4j co-occurring keywords for the main keyword
        // (these are keywords that actually share papers with the main keyword)
        List<KeywordInfo> neo4jTier1 = getCooccurringKeywords(mainNormalized, 10);
        for (KeywordInfo info : neo4jTier1) {
            String childNormalized = info.text().toLowerCase().trim();
            if (visitedNormalized.contains(childNormalized)) continue;

            String childId = info.id();
            if (childId.isBlank()) childId = generateKeywordId(childNormalized);

            nodeMap.putIfAbsent(childId, GraphNode.builder()
                    .id(childId).label(info.text())
                    .group("KEYWORD")
                    .size(Math.max(1, info.paperCount()))
                    .paperCount(info.paperCount())
                    .tier(1)
                    .build());

            links.add(new GraphLink(mainKeywordId, childId, String.valueOf(info.cooccurCount())));
            visitedNormalized.add(childNormalized);
            currentLevel.add(childNormalized);
        }

        log.info("Tier 1: {} keywords queued for further expansion (from {} AI + Neo4j co-occurrence)",
                currentLevel.size(), expandedKeywords.size());

        // ── Tier 2+: Neo4j co-occurrence traversal ──
        int cooccurrenceLimit = 5; // top 5 per keyword per level
        Set<String> nextLevel = new LinkedHashSet<>();

        for (int depth = 2; depth <= maxDepth; depth++) {
            if (currentLevel.isEmpty()) {
                log.info("No keywords to expand at depth {}, stopping", depth);
                break;
            }

            for (String parentKw : currentLevel) {
                String parentId = generateKeywordId(parentKw);

                List<KeywordInfo> cooccurring = getCooccurringKeywords(parentKw, cooccurrenceLimit);
                for (KeywordInfo info : cooccurring) {
                    String childNormalized = info.text().toLowerCase().trim();
                    if (visitedNormalized.contains(childNormalized)) {
                        // Already in graph — just add link if missing
                        String childId = generateKeywordId(childNormalized);
                        List<GraphLink> existingLinks = links.stream()
                                .filter(l -> (l.getSource().equals(parentId) && l.getTarget().equals(childId))
                                        || (l.getSource().equals(childId) && l.getTarget().equals(parentId)))
                                .toList();
                        if (existingLinks.isEmpty()) {
                            links.add(new GraphLink(parentId, childId, String.valueOf(info.cooccurCount())));
                        }
                        continue;
                    }

                    String childId = info.id();
                    if (childId.isBlank()) childId = generateKeywordId(childNormalized);

                    nodeMap.putIfAbsent(childId, GraphNode.builder()
                            .id(childId).label(info.text())
                            .group("KEYWORD")
                            .size(Math.max(1, info.paperCount()))
                            .paperCount(info.paperCount())
                            .tier(depth)
                            .build());

                    links.add(new GraphLink(parentId, childId, String.valueOf(info.cooccurCount())));
                    visitedNormalized.add(childNormalized);
                    nextLevel.add(childNormalized);
                }
            }

            currentLevel = new LinkedHashSet<>(nextLevel);
            nextLevel.clear();
        }

        // Deduplicate links
        List<GraphLink> uniqueLinks = links.stream().distinct().collect(Collectors.toList());

        log.info("Keyword-only graph (depth={}) for '{}': {} nodes, {} links",
                maxDepth, originalKeyword, nodeMap.size(), uniqueLinks.size());

        return new GraphResponse(new ArrayList<>(nodeMap.values()), uniqueLinks);
    }

    /**
     * Get the number of papers that have this keyword in Neo4j.
     */
    private int getKeywordPaperCount(String normalizedKeyword) {
        try {
            String cypher = """
                    MATCH (k:Keyword)
                    WHERE k.normalizedText CONTAINS $keyword
                    OPTIONAL MATCH (k)<-[:HAS_KEYWORD]-(p:Paper)
                    RETURN COUNT(DISTINCT p) AS cnt
                    """;

            var result = neo4jClient.query(cypher)
                    .bind(normalizedKeyword).to("keyword")
                    .fetch()
                    .one();

            if (result.isPresent()) {
                Object cnt = result.get().get("cnt");
                return cnt != null ? ((Number) cnt).intValue() : 0;
            }
        } catch (Exception e) {
            log.debug("Failed to get paper count for '{}': {}", normalizedKeyword, e.getMessage());
        }
        return 0;
    }

    /**
     * Get co-occurrence count between two keywords (papers that have both).
     */
    private int getCooccurrenceCount(String kw1, String kw2) {
        try {
            String cypher = """
                    MATCH (k1:Keyword)<-[:HAS_KEYWORD]-(p:Paper)-[:HAS_KEYWORD]->(k2:Keyword)
                    WHERE k1.normalizedText CONTAINS $kw1
                      AND k2.normalizedText CONTAINS $kw2
                      AND k1 <> k2
                    RETURN COUNT(DISTINCT p) AS cnt
                    """;

            var result = neo4jClient.query(cypher)
                    .bind(kw1).to("kw1")
                    .bind(kw2).to("kw2")
                    .fetch()
                    .one();

            if (result.isPresent()) {
                Object cnt = result.get().get("cnt");
                return cnt != null ? ((Number) cnt).intValue() : 0;
            }
        } catch (Exception e) {
            log.debug("Failed to get co-occurrence for '{}'/'{}': {}", kw1, kw2, e.getMessage());
        }
        return 0;
    }

    /**
     * Get top N keywords that co-occur with the given keyword.
     * Used for multi-level graph expansion.
     */
    private List<KeywordInfo> getCooccurringKeywords(String normalizedKeyword, int limit) {
        List<KeywordInfo> result = new ArrayList<>();
        try {
            String cypher = """
                    MATCH (k1:Keyword)<-[:HAS_KEYWORD]-(p:Paper)-[:HAS_KEYWORD]->(k2:Keyword)
                    WHERE k1.normalizedText CONTAINS $keyword
                      AND k2 <> k1
                    WITH k2, COUNT(DISTINCT p) AS cooccurCount
                    OPTIONAL MATCH (k2)<-[:HAS_KEYWORD]-(allP:Paper)
                    WITH k2, cooccurCount, COUNT(DISTINCT allP) AS paperCount
                    ORDER BY cooccurCount DESC
                    LIMIT $limit
                    RETURN k2.keywordId AS kId, k2.text AS kText,
                           cooccurCount, paperCount
                    """;

            neo4jClient.query(cypher)
                    .bind(normalizedKeyword).to("keyword")
                    .bind(limit).to("limit")
                    .fetch()
                    .all()
                    .forEach(record -> {
                        String kId = record.get("kId") != null ? record.get("kId").toString() : "";
                        String kText = record.get("kText") != null ? record.get("kText").toString() : "";
                        int cooccur = record.get("cooccurCount") != null
                                ? ((Number) record.get("cooccurCount")).intValue() : 0;
                        int papers = record.get("paperCount") != null
                                ? ((Number) record.get("paperCount")).intValue() : 0;
                        if (!kId.isBlank()) {
                            result.add(new KeywordInfo(kId, kText, cooccur, papers));
                        }
                    });
        } catch (Exception e) {
            log.debug("Failed to get co-occurring keywords for '{}': {}", normalizedKeyword, e.getMessage());
        }
        return result;
    }

    // Simple inner record for keyword metadata
    private record KeywordInfo(String id, String text, int cooccurCount, int paperCount) {}

    // ============================================
    //  ENHANCED: Gemini + Neo4j Keyword Graph
    // ============================================

    /**
     * Build an enhanced keyword graph using Gemini-expanded keywords.
     *
     * Flow:
     * 1. Create a MAIN_KEYWORD center node from the user's original keyword
     * 2. For each Gemini-generated keyword, search Neo4j for matches
     * 3. Keywords found in Neo4j → GEMINI_KEYWORD nodes with paper connections
     * 4. Keywords NOT in Neo4j → SUGGESTED_KEYWORD nodes (disconnected, show as AI suggestions)
     * 5. Paper nodes connect GEMINI_KEYWORD nodes via HAS_KEYWORD links
     * 6. MAIN_KEYWORD connects to GEMINI_KEYWORD nodes via RELATED_TO links
     *
     * @param originalKeyword  the keyword the user originally searched for
     * @param expandedKeywords list of related keywords from Gemini
     * @return GraphResponse with enriched nodes and links
     */
    public GraphResponse getEnhancedKeywordGraph(String originalKeyword, List<String> expandedKeywords) {
        Map<String, GraphNode> nodeMap = new LinkedHashMap<>();
        List<GraphLink> links = new ArrayList<>();

        String mainKeywordId = generateKeywordId(originalKeyword.toLowerCase().trim());

        // Add the center node (user's original keyword)
        nodeMap.put(mainKeywordId, GraphNode.builder()
                .id(mainKeywordId)
                .label(originalKeyword.trim())
                .group("MAIN_KEYWORD")
                .size(expandedKeywords.size() + 5)
                .build());

        int totalMatches = 0;

        for (String kw : expandedKeywords) {
            if (kw == null || kw.isBlank()) continue;

            String normalizedKw = kw.toLowerCase().trim();
            String geminiKeywordId = generateKeywordId(normalizedKw);

            // Skip if it's the same as the original keyword
            if (normalizedKw.equals(originalKeyword.toLowerCase().trim())) continue;

            try {
                String cypherQuery = """
                        MATCH (k:Keyword)
                        WHERE k.normalizedText CONTAINS $keyword
                        OPTIONAL MATCH (k)<-[:HAS_KEYWORD]-(p:Paper)
                        WITH k, COUNT(DISTINCT p) AS paperCount,
                           COLLECT(DISTINCT {paperId: p.paperId, title: p.title}) AS papers
                        RETURN k.keywordId AS kId, k.text AS kText,
                               paperCount, papers
                        LIMIT 1
                        """;

                var results = neo4jClient.query(cypherQuery)
                        .bind(normalizedKw).to("keyword")
                        .fetch()
                        .all();

                if (results.isEmpty()) {
                    // Gemini keyword NOT found in Neo4j → show as suggestion
                    nodeMap.putIfAbsent(geminiKeywordId, GraphNode.builder()
                            .id(geminiKeywordId)
                            .label(kw.trim())
                            .group("SUGGESTED_KEYWORD")
                            .size(1)
                            .build());

                    // Conceptual link from main keyword to this suggestion
                    links.add(new GraphLink(mainKeywordId, geminiKeywordId, "SUGGESTED"));
                    log.debug("Gemini keyword '{}' not in Neo4j — shown as SUGGESTED_KEYWORD", kw);
                } else {
                    var record = results.iterator().next();
                    String kId = record.get("kId") != null ? record.get("kId").toString() : geminiKeywordId;
                    String kText = record.get("kText") != null ? record.get("kText").toString() : kw.trim();
                    int paperCount = record.get("paperCount") != null
                            ? ((Number) record.get("paperCount")).intValue() : 0;

                    // Add Gemini keyword node (found in Neo4j)
                    nodeMap.putIfAbsent(kId, GraphNode.builder()
                            .id(kId)
                            .label(kText)
                            .group("GEMINI_KEYWORD")
                            .size(Math.max(1, paperCount))
                            .build());

                    // Conceptual link from main keyword to this Gemini keyword
                    links.add(new GraphLink(mainKeywordId, kId, "RELATED_TO"));

                    // Add paper nodes and HAS_KEYWORD links
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> papers = (List<Map<String, Object>>) record.get("papers");
                    if (papers != null) {
                        for (Map<String, Object> paper : papers) {
                            String paperId = paper.get("paperId") != null ? paper.get("paperId").toString() : null;
                            String paperTitle = paper.get("title") != null ? paper.get("title").toString() : "Untitled";

                            if (paperId != null) {
                                nodeMap.putIfAbsent(paperId, GraphNode.builder()
                                        .id(paperId)
                                        .label(paperTitle)
                                        .group("PAPER")
                                        .size(1)
                                        .build());

                                links.add(new GraphLink(paperId, kId, "HAS_KEYWORD"));
                            }
                        }
                    }

                    totalMatches++;
                }
            } catch (Exception e) {
                log.warn("Neo4j query failed for Gemini keyword '{}': {}", kw, e.getMessage());
                // Still add as suggestion on query failure
                nodeMap.putIfAbsent(geminiKeywordId, GraphNode.builder()
                        .id(geminiKeywordId)
                        .label(kw.trim())
                        .group("SUGGESTED_KEYWORD")
                        .size(1)
                        .build());
            }
        }

        // Deduplicate links
        List<GraphLink> uniqueLinks = links.stream().distinct().collect(java.util.stream.Collectors.toList());

        log.info("Enhanced graph for '{}': {} nodes, {} links ({} Gemini keywords matched in Neo4j)",
                originalKeyword, nodeMap.size(), uniqueLinks.size(), totalMatches);

        return new GraphResponse(new ArrayList<>(nodeMap.values()), uniqueLinks);
    }

    // ============================================
    //  CLEANUP: Xóa stale Paper nodes
    // ============================================

    /**
     * Xóa Paper nodes trong Neo4j không còn tồn tại trong SQL.
     * Gọi khi phát hiện Neo4j có paper IDs nhưng SQL không tìm thấy.
     *
     * @param stalePaperIds danh sách paperId không tìm thấy trong SQL
     */
    public void deleteStalePapers(List<String> stalePaperIds) {
        if (stalePaperIds == null || stalePaperIds.isEmpty()) return;

        String cypherQuery = """
                MATCH (p:Paper)
                WHERE p.paperId IN $paperIds
                DETACH DELETE p
                """;

        try {
            neo4jClient.query(cypherQuery)
                    .bind(stalePaperIds).to("paperIds")
                    .run();
            log.info("Neo4j: deleted {} stale paper nodes", stalePaperIds.size());
        } catch (Exception e) {
            log.warn("Failed to delete stale Neo4j papers: {}", e.getMessage());
        }
    }

    /**
     * Xóa tất cả nodes và relationships trong Neo4j.
     * Dùng để reset hoàn toàn graph database.
     */
    public void clearAll() {
        try {
            neo4jClient.query("MATCH (n) DETACH DELETE n").run();
            log.info("Neo4j: cleared all nodes and relationships");
        } catch (Exception e) {
            log.warn("Failed to clear Neo4j: {}", e.getMessage());
        }
    }

    // ============================================
    //  PRIVATE HELPERS
    // ============================================

    private void mergePaperOnly(String paperId, String title, String doi, Integer pubYear) {
        String cypherQuery = """
                MERGE (p:Paper {paperId: $paperId})
                ON CREATE SET p.title = $title, p.doi = $doi, p.pubYear = $pubYear
                ON MATCH SET p.title = $title, p.doi = $doi, p.pubYear = $pubYear
                """;

        try {
            neo4jClient.query(cypherQuery)
                    .bind(paperId).to("paperId")
                    .bind(title).to("title")
                    .bind(doi != null ? doi : "").to("doi")
                    .bind(pubYear != null ? pubYear : 0).to("pubYear")
                    .run();
        } catch (Exception e) {
            log.error("Neo4j merge paper failed for {}: {}", paperId, e.getMessage());
        }
    }

    /**
     * Tạo keywordId ổn định từ normalized text (dùng UUID v3-style).
     * Đảm bảo cùng 1 keyword text luôn ra cùng 1 ID.
     */
    private String generateKeywordId(String normalizedText) {
        return UUID.nameUUIDFromBytes(("keyword:" + normalizedText).getBytes()).toString();
    }
}
