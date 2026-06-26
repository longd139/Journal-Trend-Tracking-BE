package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.GraphLink;
import com.sra.journal_tracking.dto.GraphNode;
import com.sra.journal_tracking.dto.GraphResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GraphService {

    private static final Logger log = LoggerFactory.getLogger(GraphService.class);

    // ── Manual in-memory cache: 1 hour TTL ──
    private static final long CACHE_TTL_MS = 60 * 60 * 1000; // 1 giờ

    private static class CacheEntry<T> {
        final T data;
        final long expiryTime;
        CacheEntry(T data) { this.data = data; this.expiryTime = System.currentTimeMillis() + CACHE_TTL_MS; }
        boolean isExpired() { return System.currentTimeMillis() > expiryTime; }
    }

    private final ConcurrentHashMap<String, CacheEntry<GraphResponse>> keywordGraphCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry<List<String>>> paperSearchCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry<Boolean>> keywordExistsCache = new ConcurrentHashMap<>();

    private final Neo4jClient neo4jClient;

    public GraphService(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    // ── Neo4j index creation ──

    @PostConstruct
    public void createIndexes() {
        try {
            neo4jClient.query(
                    "CREATE INDEX keyword_norm_text IF NOT EXISTS FOR (k:Keyword) ON (k.normalizedText)"
            ).run();
            log.info("Neo4j index on Keyword.normalizedText ensured");
        } catch (Exception e) {
            log.warn("Could not create Neo4j index: {}", e.getMessage());
        }
    }

    // ============================================
    //  KEYWORD EXISTS: Lightweight check (cached)
    // ============================================

    /**
     * Lightweight check whether a keyword exists in Neo4j.
     * Uses a simple COUNT instead of building a full graph response.
     * Result cached for 1 hour.
     */
    public boolean keywordExists(String keyword) {
        String cacheKey = keyword.toLowerCase().trim();

        // ── Cache hit? ──
        CacheEntry<Boolean> cached = keywordExistsCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.info("CACHE HIT: keywordExists '{}' → {} (from cache)", keyword, cached.data);
            return cached.data;
        }
        if (cached != null) {
            keywordExistsCache.remove(cacheKey);
        }

        String normalizedKeyword = keyword.toLowerCase().trim();
        String cypherQuery = """
                MATCH (k:Keyword)
                WHERE k.normalizedText = $keyword
                   OR k.normalizedText CONTAINS $keyword
                RETURN COUNT(k) AS cnt
                LIMIT 1
                """;

        boolean exists = false;
        try {
            Long count = neo4jClient.query(cypherQuery)
                    .bind(keyword).to("keyword")
                    .fetch()
                    .one()
                    .map(record -> {
                        Object cnt = record.get("cnt");
                        return cnt instanceof Number ? ((Number) cnt).longValue() : 0L;
                    })
                    .orElse(0L);
            exists = count > 0;
            log.info("keywordExists '{}': {}", keyword, exists);
        } catch (Exception e) {
            log.warn("keywordExists failed for '{}': {}", keyword, e.getMessage());
        }

        // ── Store in cache ──
        keywordExistsCache.put(cacheKey, new CacheEntry<>(exists));
        log.info("CACHE STORE: keywordExists '{}' → {} (TTL=1h)", keyword, exists);

        return exists;
    }

    // ============================================
    //  REVERSE SEARCH: Keyword → Papers
    // ============================================

    /**
     * Tìm paper IDs từ Neo4j dựa trên keyword text.
     * Dùng CONTAINS để match gần đúng (không cần chính xác tuyệt đối).
     * Kết quả được cache 1 giờ — tìm lại cùng keyword sẽ trả về ngay lập tức.
     *
     * @param keyword từ khóa người dùng nhập
     * @return danh sách paperId (UUID string) tìm thấy trong Neo4j
     */
    public List<String> searchPapersByKeyword(String keyword) {
        String cacheKey = keyword.toLowerCase().trim();

        // ── Cache hit? ──
        CacheEntry<List<String>> cached = paperSearchCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.info("CACHE HIT: paperSearch '{}' → {} papers (from cache)", keyword, cached.data.size());
            return cached.data;
        }
        if (cached != null) {
            paperSearchCache.remove(cacheKey); // expired → remove
        }

        // ── Cache miss: query Neo4j ──
        String normalizedKeyword = keyword.toLowerCase().trim();

        // Phase 1: Exact match (fast, uses index)
        List<String> paperIds = queryPaperIds(normalizedKeyword, true);
        if (!paperIds.isEmpty()) {
            log.info("Neo4j exact-match for '{}': {} papers", keyword, paperIds.size());
            return paperIds;
        }

        // Phase 2: Fallback to pattern match for multi-word or partial matches
        paperIds = queryPaperIds(normalizedKeyword, false);
        log.info("Neo4j pattern-match for '{}': {} papers", keyword, paperIds.size());
        return paperIds;
    }

    private List<String> queryPaperIds(String keyword, boolean exactOnly) {
        String cacheKey = keyword.toLowerCase().trim();
        String cypherQuery;
        if (exactOnly) {
            cypherQuery = """
                    MATCH (p:Paper)-[:HAS_KEYWORD]->(k:Keyword)
                    WHERE k.normalizedText = $keyword
                    RETURN DISTINCT p.paperId AS paperId
                    LIMIT 50
                    """;
        } else {
            cypherQuery = """
                    MATCH (p:Paper)-[:HAS_KEYWORD]->(k:Keyword)
                    WHERE k.normalizedText = $keyword
                       OR k.normalizedText STARTS WITH $keyword + ' '
                       OR k.normalizedText ENDS WITH ' ' + $keyword
                       OR k.normalizedText CONTAINS ' ' + $keyword + ' '
                    RETURN DISTINCT p.paperId AS paperId
                    LIMIT 50
                    """;
        }

        List<String> paperIds = new ArrayList<>();
        try {
            neo4jClient.query(cypherQuery)
                    .bind(keyword).to("keyword")
                    .fetch()
                    .all()
                    .forEach(record -> paperIds.add(record.get("paperId").toString()));
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            log.warn("Neo4j search failed for keyword '{}': {}. Root cause: {}", keyword, e.getMessage(), root.toString());
        }

        // ── Store in cache (chỉ cache khi có kết quả) ──
        if (!paperIds.isEmpty()) {
            paperSearchCache.put(cacheKey, new CacheEntry<>(paperIds));
            log.info("CACHE STORE: paperSearch '{}' → {} papers (TTL=1h)", keyword, paperIds.size());
        } else {
            log.info("CACHE SKIP: paperSearch '{}' empty — not cached (will retry Neo4j next time)", keyword);
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

        // Invalidate cache cho các keyword được lưu
        for (String kw : keywords) {
            if (kw == null || kw.isBlank()) continue;
            String cacheKey = kw.toLowerCase().trim();
            keywordGraphCache.remove(cacheKey);
            paperSearchCache.remove(cacheKey);
            keywordExistsCache.remove(cacheKey);
        }

        // Build keyword list for UNWIND (deduplicated by normalizedText)
        Map<String, Map<String, String>> uniqueKws = new LinkedHashMap<>();
        for (String kw : keywords) {
            if (kw == null || kw.isBlank()) continue;
            String normalized = kw.toLowerCase().trim();
            String keywordId = generateKeywordId(normalized);
            uniqueKws.putIfAbsent(normalized, Map.of(
                    "keywordId", keywordId,
                    "keywordText", kw.trim(),
                    "normalizedText", normalized
            ));
        }

        if (uniqueKws.isEmpty()) {
            mergePaperOnly(paperId, title, doi, pubYear);
            return;
        }

        List<Map<String, String>> kwList = new ArrayList<>(uniqueKws.values());

        // Single Cypher query with UNWIND — batches all keywords for one paper
        String cypherQuery = """
                MERGE (p:Paper {paperId: $paperId})
                ON CREATE SET p.title = $title, p.doi = $doi, p.pubYear = $pubYear
                ON MATCH SET p.title = $title, p.doi = $doi, p.pubYear = $pubYear
                WITH p
                UNWIND $keywords AS kw
                MERGE (k:Keyword {keywordId: kw.keywordId})
                ON CREATE SET k.text = kw.keywordText, k.normalizedText = kw.normalizedText
                ON MATCH SET k.text = kw.keywordText
                MERGE (p)-[:HAS_KEYWORD]->(k)
                """;

        try {
            neo4jClient.query(cypherQuery)
                    .bind(paperId).to("paperId")
                    .bind(title).to("title")
                    .bind(doi != null ? doi : "").to("doi")
                    .bind(pubYear != null ? pubYear : 0).to("pubYear")
                    .bind(kwList).to("keywords")
                    .run();

            log.info("Neo4j: saved paper '{}' with {} keywords (batched UNWIND)", title, kwList.size());
        } catch (Exception e) {
            log.error("Neo4j batch save failed for paper {}: {}", paperId, e.getMessage());
            log.error("Full stack trace:", e);
        }
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
    //  KEYWORD GRAPH: Keyword → Papers + Keywords (with sizing)
    // ============================================

    /**
     * Build a graph visualization for a keyword search.
     * Returns Paper and Keyword nodes related to the searched keyword.
     * Keyword node sizes = number of connected Paper nodes (global frequency).
     * Paper node sizes = 1 (default).
     * Kết quả được cache 1 giờ — tìm lại cùng keyword sẽ trả về ngay lập tức.
     *
     * @param keyword the search keyword
     * @return GraphResponse with sized nodes and HAS_KEYWORD links
     */
    public GraphResponse getKeywordGraph(String keyword) {
        String cacheKey = keyword.toLowerCase().trim();

        // ── Cache hit? ──
        CacheEntry<GraphResponse> cached = keywordGraphCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            GraphResponse resp = cached.data;
            log.info("CACHE HIT: keywordGraph '{}' → {} nodes, {} links (from cache)",
                    keyword,
                    resp.getNodes() != null ? resp.getNodes().size() : 0,
                    resp.getLinks() != null ? resp.getLinks().size() : 0);
            return resp;
        }
        if (cached != null) {
            keywordGraphCache.remove(cacheKey); // expired → remove
        }

        // ── Cache miss: query Neo4j ──
        String normalizedKeyword = keyword.toLowerCase().trim();

        String cypherQuery = """
                MATCH (p:Paper)-[:HAS_KEYWORD]->(k:Keyword)
                WHERE k.normalizedText = $keyword
                   OR k.normalizedText STARTS WITH $keyword + ' '
                   OR k.normalizedText ENDS WITH ' ' + $keyword
                   OR k.normalizedText CONTAINS ' ' + $keyword + ' '
                WITH DISTINCT p, k
                LIMIT 200
                MATCH (k)<-[:HAS_KEYWORD]-(otherP:Paper)
                WITH p, k, COUNT(otherP) AS keywordSize
                RETURN p.paperId AS paperId, p.title AS paperTitle,
                       k.keywordId AS keywordId, k.text AS keywordText,
                       keywordSize
                """;

        Map<String, GraphNode> nodeMap = new HashMap<>();
        List<GraphLink> links = new ArrayList<>();

        try {
            neo4jClient.query(cypherQuery)
                    .bind(keyword).to("keyword")
                    .fetch()
                    .all()
                    .forEach(record -> {
                        String pId = record.get("paperId").toString();
                        String pTitle = record.get("paperTitle") != null ? record.get("paperTitle").toString() : "Untitled";
                        String kId = record.get("keywordId").toString();
                        String kText = record.get("keywordText") != null ? record.get("keywordText").toString() : "";
                        int kwSize = record.get("keywordSize") != null ? ((Number) record.get("keywordSize")).intValue() : 0;

                        // Paper nodes: default size 1 (could later use citationCount)
                        nodeMap.putIfAbsent(pId, GraphNode.builder()
                                .id(pId).label(pTitle).group("PAPER").size(1).build());

                        // Keyword nodes: size = global paper count
                        if (!nodeMap.containsKey(kId)) {
                            nodeMap.put(kId, GraphNode.builder()
                                    .id(kId).label(kText).group("KEYWORD").size(kwSize).build());
                        }

                        links.add(new GraphLink(pId, kId, ""));
                    });

            log.info("Keyword graph for '{}': {} nodes, {} links", keyword, nodeMap.size(), links.size());
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            log.warn("Keyword graph search failed for '{}': {}. Root cause: {}", keyword, e.getMessage(), root.toString());
            log.warn("Full stack trace:", e);
            // Return empty graph on failure — don't throw
        }

        GraphResponse result = new GraphResponse(new ArrayList<>(nodeMap.values()), links);

        // ── Store in cache (chỉ cache khi có nodes — đừng cache empty!) ──
        if (!nodeMap.isEmpty()) {
            keywordGraphCache.put(cacheKey, new CacheEntry<>(result));
            log.info("CACHE STORE: keywordGraph '{}' → {} nodes, {} links (TTL=1h)",
                    keyword, nodeMap.size(), links.size());
        } else {
            log.info("CACHE SKIP: keywordGraph '{}' empty — not cached (will retry Neo4j next time)", keyword);
        }

        return result;
    }

    // ============================================
    //  QUICK STATS: Aggregation queries for keyword stats
    // ============================================

    /**
     * Count the number of Paper nodes connected to a keyword in Neo4j.
     * Uses exact match + prefix match (index-friendly) for performance.
     *
     * @param keyword the search keyword
     * @return total number of matching Paper nodes
     */
    public long countPapersByKeyword(String keyword) {
        String normalizedKeyword = keyword.toLowerCase().trim();

        // Use index-friendly exact match first, fall back to prefix match only
        // Avoid CONTAINS which forces a full scan on large graphs
        String cypherQuery = """
                MATCH (p:Paper)-[:HAS_KEYWORD]->(k:Keyword)
                WHERE k.normalizedText = $keyword
                RETURN COUNT(DISTINCT p) AS cnt
                """;

        try {
            Long count = neo4jClient.query(cypherQuery)
                    .bind(keyword).to("keyword")
                    .fetch()
                    .one()
                    .map(record -> ((Number) record.get("cnt")).longValue())
                    .orElse(0L);
            log.info("Neo4j count for '{}': {} papers (exact match)", keyword, count);
            return count;
        } catch (Exception e) {
            log.warn("Neo4j count failed for keyword '{}': {}", keyword, e.getMessage());
            return 0L;
        }
    }

    /**
     * Get ALL paper IDs for a keyword (up to 300) using index-friendly exact match.
     *
     * @param keyword the search keyword
     * @return list of matching paper UUID strings (max 300)
     */
    public List<String> getAllPaperIdsByKeyword(String keyword) {
        String normalizedKeyword = keyword.toLowerCase().trim();

        // Exact match only — uses Neo4j index on normalizedText for fast lookup
        String cypherQuery = """
                MATCH (p:Paper)-[:HAS_KEYWORD]->(k:Keyword)
                WHERE k.normalizedText = $keyword
                RETURN DISTINCT p.paperId AS paperId
                LIMIT 300
                """;

        List<String> paperIds = new ArrayList<>();

        try {
            neo4jClient.query(cypherQuery)
                    .bind(keyword).to("keyword")
                    .fetch()
                    .all()
                    .forEach(record -> paperIds.add(record.get("paperId").toString()));
            log.info("Neo4j getAll for '{}': {} papers (exact match)", keyword, paperIds.size());
        } catch (Exception e) {
            log.warn("Neo4j getAll failed for keyword '{}': {}", keyword, e.getMessage());
        }

        return paperIds;
    }

    // ============================================
    //  RELATED TRENDS: Keyword Co-occurrence
    // ============================================

    /**
     * Find keywords that frequently co-occur with the given keyword
     * in recent papers (past N years). Uses Neo4j graph traversal
     * to discover "satellite keywords" for research niche discovery.
     *
     * @param keyword   the search keyword (normalized)
     * @param startYear only consider papers from this year onward
     * @param limit     max number of related keywords to return
     * @return list of [normalizedKeyword, originalText, totalCount, thisYearCount, lastYearCount]
     */
    public List<java.util.Map<String, Object>> getCooccurringKeywords(
            String keyword, int startYear, short thisYear, short lastYear, int limit) {

        // Direct traversal: keyword → papers (recent) → all other keywords
        String cypherQuery = """
                MATCH (p:Paper)-[:HAS_KEYWORD]->(k:Keyword)
                WHERE k.normalizedText = $keyword AND p.pubYear >= $startYear
                MATCH (p)-[:HAS_KEYWORD]->(other:Keyword)
                WHERE other.normalizedText <> $keyword
                WITH other.normalizedText AS normKw, other.text AS origKw,
                     COLLECT(DISTINCT p) AS papers
                WITH normKw, origKw,
                     SIZE(papers) AS totalCount,
                     SIZE([x IN papers WHERE x.pubYear = $thisYear]) AS thisYearCount,
                     SIZE([x IN papers WHERE x.pubYear = $lastYear]) AS lastYearCount
                RETURN normKw, origKw, totalCount, thisYearCount, lastYearCount
                ORDER BY totalCount DESC
                LIMIT $limit
                """;

        List<java.util.Map<String, Object>> results = new ArrayList<>();
        try {
            neo4jClient.query(cypherQuery)
                    .bind(keyword).to("keyword")
                    .bind(startYear).to("startYear")
                    .bind(thisYear).to("thisYear")
                    .bind(lastYear).to("lastYear")
                    .bind(limit).to("limit")
                    .fetch()
                    .all()
                    .forEach(record -> {
                        java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                        row.put("normalizedKeyword", record.get("normKw").toString());
                        row.put("originalKeyword", record.get("origKw") != null
                                ? record.get("origKw").toString() : "");
                        row.put("totalCount", ((Number) record.get("totalCount")).longValue());
                        row.put("thisYearCount", ((Number) record.get("thisYearCount")).longValue());
                        row.put("lastYearCount", ((Number) record.get("lastYearCount")).longValue());
                        results.add(row);
                    });
            log.info("Co-occurrence for '{}': {} related keywords found (since {})",
                    keyword, results.size(), startYear);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            log.warn("Co-occurrence query failed for '{}': {}. Root cause: {}",
                    keyword, e.getMessage(), root.toString());
        }
        return results;
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
            // Clear all caches too
            keywordGraphCache.clear();
            paperSearchCache.clear();
            keywordExistsCache.clear();
            log.info("GraphService: cleared all caches");
        } catch (Exception e) {
            log.warn("Failed to clear Neo4j: {}", e.getMessage());
        }
    }

    // ============================================
    //  CATEGORIES DISCOVERY: Field-level keywords
    // ============================================

    /**
     * Find the most-connected keywords — these represent broad research fields
     * suitable for the "Categories Discovery" zero-state pill buttons.
     * <p>
     * Uses a simple graph heuristic: keywords connected to many papers are
     * likely broad research areas (e.g., "Artificial Intelligence", "Biomedical").
     *
     * @param limit max number of categories to return (recommend 6-8)
     * @return list of maps: {keywordText, paperCount}
     */
    public List<Map<String, Object>> getCategoryKeywords(int limit) {
        String cypherQuery = """
                MATCH (k:Keyword)<-[:HAS_KEYWORD]-(p:Paper)
                WITH k, COUNT(DISTINCT p) AS paperCount
                WHERE paperCount >= 5
                RETURN k.text AS keywordText, k.normalizedText AS normalizedText, paperCount
                ORDER BY paperCount DESC
                LIMIT $limit
                """;

        List<Map<String, Object>> results = new ArrayList<>();
        try {
            neo4jClient.query(cypherQuery)
                    .bind(limit).to("limit")
                    .fetch()
                    .all()
                    .forEach(record -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("keywordText", record.get("keywordText") != null
                                ? record.get("keywordText").toString() : "");
                        row.put("normalizedText", record.get("normalizedText") != null
                                ? record.get("normalizedText").toString() : "");
                        row.put("paperCount", record.get("paperCount") != null
                                ? ((Number) record.get("paperCount")).longValue() : 0L);
                        results.add(row);
                    });
            log.info("Category keywords: {} found (limit={})", results.size(), limit);
        } catch (Exception e) {
            log.warn("Category keywords query failed: {}", e.getMessage());
        }
        return results;
    }

    /**
     * Get niche sub-topics within a category field.
     * Finds keywords that co-occur with the given category keyword in recent papers.
     * These are the "ngách" — specific subtopics trending within a broad field.
     *
     * @param categoryKeyword the broad field keyword (normalized)
     * @param startYear       only consider papers from this year onward
     * @param limit           max number of niche topics to return
     * @return list of maps: {keywordText, paperCount, thisYearCount, lastYearCount}
     */
    public List<Map<String, Object>> getNicheKeywords(
            String categoryKeyword, int startYear, short thisYear, short lastYear, int limit) {

        // Co-occurrence traversal: category → papers (recent) → other keywords
        String cypherQuery = """
                MATCH (p:Paper)-[:HAS_KEYWORD]->(cat:Keyword)
                WHERE cat.normalizedText = $categoryKeyword AND p.pubYear >= $startYear
                MATCH (p)-[:HAS_KEYWORD]->(niche:Keyword)
                WHERE niche.normalizedText <> $categoryKeyword
                WITH niche,
                     COLLECT(DISTINCT p) AS papers
                WITH niche.normalizedText AS normKw,
                     niche.text AS origKw,
                     SIZE(papers) AS totalCount,
                     SIZE([x IN papers WHERE x.pubYear = $thisYear]) AS thisYearCount,
                     SIZE([x IN papers WHERE x.pubYear = $lastYear]) AS lastYearCount
                WHERE totalCount >= 2
                RETURN normKw, origKw, totalCount, thisYearCount, lastYearCount
                ORDER BY totalCount DESC
                LIMIT $limit
                """;

        List<Map<String, Object>> results = new ArrayList<>();
        try {
            neo4jClient.query(cypherQuery)
                    .bind(categoryKeyword).to("categoryKeyword")
                    .bind(startYear).to("startYear")
                    .bind(thisYear).to("thisYear")
                    .bind(lastYear).to("lastYear")
                    .bind(limit).to("limit")
                    .fetch()
                    .all()
                    .forEach(record -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("keywordText", record.get("origKw") != null
                                ? record.get("origKw").toString() : "");
                        row.put("normalizedText", record.get("normKw") != null
                                ? record.get("normKw").toString() : "");
                        row.put("paperCount", ((Number) record.get("totalCount")).longValue());
                        row.put("thisYearCount", ((Number) record.get("thisYearCount")).longValue());
                        row.put("lastYearCount", ((Number) record.get("lastYearCount")).longValue());
                        results.add(row);
                    });
            log.info("Niche keywords for '{}': {} found (since {})",
                    categoryKeyword, results.size(), startYear);
        } catch (Exception e) {
            log.warn("Niche keywords query failed for '{}': {}", categoryKeyword, e.getMessage());
        }
        return results;
    }

    /**
     * Get Neo4j graph statistics.
     */
    public java.util.Map<String, Long> getStats() {
        java.util.Map<String, Long> stats = new java.util.LinkedHashMap<>();
        try {
            Long papers = neo4jClient.query("MATCH (p:Paper) RETURN COUNT(p) AS cnt")
                    .fetch().one().map(r -> (Long) r.get("cnt")).orElse(0L);
            Long keywords = neo4jClient.query("MATCH (k:Keyword) RETURN COUNT(k) AS cnt")
                    .fetch().one().map(r -> (Long) r.get("cnt")).orElse(0L);
            Long rels = neo4jClient.query("MATCH ()-[r:HAS_KEYWORD]->() RETURN COUNT(r) AS cnt")
                    .fetch().one().map(r -> (Long) r.get("cnt")).orElse(0L);
            stats.put("paperNodes", papers);
            stats.put("keywordNodes", keywords);
            stats.put("relationships", rels);
        } catch (Exception e) {
            log.debug("Neo4j stats unavailable: {}", e.getMessage());
            stats.putIfAbsent("paperNodes", 0L);
            stats.putIfAbsent("keywordNodes", 0L);
            stats.putIfAbsent("relationships", 0L);
        }
        return stats;
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
