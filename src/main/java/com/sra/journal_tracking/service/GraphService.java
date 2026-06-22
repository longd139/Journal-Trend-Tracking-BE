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
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
                WHERE k.normalizedText = $keyword
                   OR k.normalizedText STARTS WITH $keyword + ' '
                   OR k.normalizedText ENDS WITH ' ' + $keyword
                   OR k.normalizedText CONTAINS ' ' + $keyword + ' '
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
    //  KEYWORD GRAPH: Keyword → Papers + Keywords (with sizing)
    // ============================================

    /**
     * Build a graph visualization for a keyword search.
     * Returns Paper and Keyword nodes related to the searched keyword.
     * Keyword node sizes = number of connected Paper nodes (global frequency).
     * Paper node sizes = 1 (default).
     *
     * @param keyword the search keyword
     * @return GraphResponse with sized nodes and HAS_KEYWORD links
     */
    public GraphResponse getKeywordGraph(String keyword) {
        String normalizedKeyword = keyword.toLowerCase().trim();

        String cypherQuery = """
                MATCH (p:Paper)-[:HAS_KEYWORD]->(k:Keyword)
                WHERE k.normalizedText = $keyword
                   OR k.normalizedText STARTS WITH $keyword + ' '
                   OR k.normalizedText ENDS WITH ' ' + $keyword
                   OR k.normalizedText CONTAINS ' ' + $keyword + ' '
                WITH p, k
                OPTIONAL MATCH (k)<-[:HAS_KEYWORD]-(otherP:Paper)
                WITH p, k, COUNT(DISTINCT otherP) AS keywordSize
                RETURN p.paperId AS paperId, p.title AS paperTitle,
                       k.keywordId AS keywordId, k.text AS keywordText,
                       keywordSize
                LIMIT 200
                """;

        Map<String, GraphNode> nodeMap = new HashMap<>();
        List<GraphLink> links = new ArrayList<>();

        try {
            neo4jClient.query(cypherQuery)
                    .bind(normalizedKeyword).to("keyword")
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

        return new GraphResponse(new ArrayList<>(nodeMap.values()), links);
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
