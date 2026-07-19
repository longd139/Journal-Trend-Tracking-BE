package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.GraphLink;
import com.sra.journal_tracking.dto.GraphNode;
import com.sra.journal_tracking.dto.GraphResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Graph queries for the Research Gap Explorer.
 * Provides hierarchy view (Stage 1), filtered view (Stage 2),
 * and focused gap view (Stage 3) data.
 * <p>
 * Uses the same Neo4jClient pattern as {@link GraphService} — raw Cypher
 * queries via {@link Neo4jClient#query(String)} with manual TTL caching.
 */
@Service
public class GraphExplorerService {

    private static final Logger log = LoggerFactory.getLogger(GraphExplorerService.class);
    private static final long CACHE_TTL_MS = 60 * 60 * 1000;

    private final Neo4jClient neo4jClient;
    private final ConcurrentHashMap<String, CacheEntry<Object>> cache = new ConcurrentHashMap<>();

    public GraphExplorerService(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    // ═══════════════════════════════════════════════════
    // STAGE 1: Hierarchy View
    // ═══════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    public GraphResponse getHierarchyGraph(List<Integer> years) {
        String cacheKey = "hierarchy:" + years.toString();
        CacheEntry<Object> cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return (GraphResponse) cached.data;
        }

        Map<String, GraphNode> nodeMap = new LinkedHashMap<>();
        List<GraphLink> links = new ArrayList<>();

        // ── Query A: Year → Paper → Keyword (keyword nodes with paper count as size) ──
        queryKeywordNodes(years, nodeMap, links);

        // ── Query B: Year → Paper → ResearchField (paper + field nodes + hierarchy links) ──
        queryPaperAndFieldNodes(years, nodeMap, links);

        // ── Query C: Topic → ResearchField + Topic → Keyword (Topic layer — optional) ──
        queryTopicNodes(nodeMap, links);

        // ── Query D: Dataset / Metric / Method nodes connected to papers ──
        queryEnrichmentNodes(years, nodeMap, links);

        GraphResponse result = new GraphResponse(new ArrayList<>(nodeMap.values()), links);
        if (!nodeMap.isEmpty()) cache.put(cacheKey, new CacheEntry<>(result));
        log.info("Hierarchy graph: {} nodes, {} links", nodeMap.size(), links.size());
        return result;
    }

    /**
     * Query A: Year nodes + Keyword nodes (size = paper count) + Year→Keyword links.
     */
    private void queryKeywordNodes(List<Integer> years,
                                   Map<String, GraphNode> nodeMap,
                                   List<GraphLink> links) {
        try {
            neo4jClient.query("""
                    MATCH (y:Year) WHERE y.year IN $years
                    MATCH (p:Paper)-[:PUBLISHED_IN]->(y)
                    MATCH (p)-[:HAS_KEYWORD]->(k:Keyword)
                    RETURN y.year AS year, k.keywordId AS keywordId, k.text AS keywordText,
                           COUNT(DISTINCT p) AS paperCount
                    ORDER BY paperCount DESC
                    LIMIT 50
                    """)
                    .bind(years).to("years").fetch().all()
                    .forEach(record -> {
                        Integer year = record.get("year") != null
                                ? ((Number) record.get("year")).intValue() : null;
                        String keywordId = stringVal(record, "keywordId");
                        String keywordText = stringVal(record, "keywordText");
                        int paperCount = record.get("paperCount") != null
                                ? ((Number) record.get("paperCount")).intValue() : 0;

                        if (year != null) {
                            String yId = "year:" + year;
                            nodeMap.putIfAbsent(yId, buildNode(yId, String.valueOf(year), "YEAR", 1));
                        }
                        if (keywordId != null) {
                            String kId = "keyword:" + keywordId;
                            nodeMap.putIfAbsent(kId, buildNode(kId, keywordText, "KEYWORD",
                                    Math.max(paperCount, 1)));
                        }
                        if (year != null && keywordId != null) {
                            links.add(new GraphLink("year:" + year, "keyword:" + keywordId, "HAS_PAPERS"));
                        }
                    });
        } catch (Exception e) {
            log.warn("Keyword node query failed: {}", e.getMessage());
        }
    }

    /**
     * Query B: Paper nodes + ResearchField nodes + Year←Paper links + Paper→Field links.
     */
    private void queryPaperAndFieldNodes(List<Integer> years,
                                         Map<String, GraphNode> nodeMap,
                                         List<GraphLink> links) {
        try {
            neo4jClient.query("""
                    MATCH (y:Year) WHERE y.year IN $years
                    MATCH (p:Paper)-[:PUBLISHED_IN]->(y)
                    OPTIONAL MATCH (p)-[:BELONGS_TO_FIELD]->(f:ResearchField)
                    RETURN y.year AS year, p.paperId AS paperId, p.title AS title,
                           f.fieldId AS fieldId, f.fieldName AS fieldName
                    LIMIT 30
                    """)
                    .bind(years).to("years").fetch().all()
                    .forEach(record -> {
                        Integer year = record.get("year") != null
                                ? ((Number) record.get("year")).intValue() : null;
                        String paperId = stringVal(record, "paperId");
                        String title = stringVal(record, "title");
                        String fieldId = stringVal(record, "fieldId");
                        String fieldName = stringVal(record, "fieldName");

                        // Year node
                        if (year != null) {
                            String yId = "year:" + year;
                            nodeMap.putIfAbsent(yId, buildNode(yId, String.valueOf(year), "YEAR", 1));
                        }
                        // Paper node
                        if (paperId != null) {
                            String pId = "paper:" + paperId;
                            String displayTitle = (title != null && !title.isBlank())
                                    ? title : "Paper " + paperId.substring(0, Math.min(paperId.length(), 8));
                            nodeMap.putIfAbsent(pId, buildNode(pId,
                                    truncateTitle(displayTitle, 60), "PAPER_A", 1));
                            // Year ← Paper link
                            if (year != null) {
                                links.add(new GraphLink(pId, "year:" + year, "PUBLISHED_IN"));
                            }
                            // Paper → Field link
                            if (fieldId != null) {
                                links.add(new GraphLink(pId, "field:" + fieldId, "BELONGS_TO_FIELD"));
                            }
                        }
                        // ResearchField node
                        if (fieldId != null) {
                            String fId = "field:" + fieldId;
                            String displayField = (fieldName != null && !fieldName.isBlank())
                                    ? fieldName : "Field " + fieldId.substring(0, Math.min(fieldId.length(), 8));
                            nodeMap.putIfAbsent(fId, buildNode(fId, displayField, "FIELD", 1));
                        }
                    });
        } catch (Exception e) {
            log.warn("Paper/Field node query failed: {}", e.getMessage());
        }
    }

    /**
     * Query C: Topic nodes + Topic→Field + Topic→Keyword links.
     * Only if Topic nodes exist in Neo4j.
     */
    private void queryTopicNodes(Map<String, GraphNode> nodeMap, List<GraphLink> links) {
        try {
            neo4jClient.query("""
                    MATCH (t:Topic)
                    OPTIONAL MATCH (t)-[:BELONGS_TO]->(f:ResearchField)
                    OPTIONAL MATCH (t)-[c:COVERS]->(k:Keyword)
                    RETURN t.topicId AS topicId, t.topicName AS topicName,
                           t.trendScore AS trendScore, t.isTrending AS isTrending,
                           f.fieldId AS fieldId, k.keywordId AS keywordId,
                           c.weight AS weight
                    """)
                    .fetch().all()
                    .forEach(record -> {
                        String topicId = stringVal(record, "topicId");
                        String topicName = stringVal(record, "topicName");
                        String fieldId = stringVal(record, "fieldId");
                        String keywordId = stringVal(record, "keywordId");

                        if (topicId != null) {
                            String tId = "topic:" + topicId;
                            String displayName = (topicName != null && !topicName.isBlank())
                                    ? topicName : "Topic " + topicId.substring(0, Math.min(topicId.length(), 8));
                            nodeMap.putIfAbsent(tId, buildNode(tId, displayName, "TOPIC", 1));
                            // Topic → Field
                            if (fieldId != null) {
                                links.add(new GraphLink(tId, "field:" + fieldId, "BELONGS_TO"));
                            }
                            // Topic → Keyword
                            if (keywordId != null) {
                                links.add(new GraphLink(tId, "keyword:" + keywordId, "COVERS"));
                            }
                        }
                    });
            if (!nodeMap.isEmpty()) {
                long topicCount = nodeMap.keySet().stream().filter(k -> k.startsWith("topic:")).count();
                if (topicCount > 0) log.info("{} Topic nodes found in Neo4j", topicCount);
            }
        } catch (Exception e) {
            log.debug("Topic node query skipped (no Topic nodes in Neo4j): {}", e.getMessage());
        }
    }

    /**
     * Query D: Dataset, Metric, Method nodes connected to papers in the year range.
     * Includes paper→dimension links (USES_DATASET, USES_METRIC, USES_METHOD).
     */
    private void queryEnrichmentNodes(List<Integer> years,
                                      Map<String, GraphNode> nodeMap,
                                      List<GraphLink> links) {
        // Datasets
        try {
            neo4jClient.query("""
                    MATCH (y:Year) WHERE y.year IN $years
                    MATCH (p:Paper)-[:PUBLISHED_IN]->(y)
                    MATCH (p)-[r:USES_DATASET]->(d:Dataset)
                    RETURN DISTINCT p.paperId AS paperId, d.datasetName AS name, r.context AS context
                    LIMIT 30
                    """)
                    .bind(years).to("years").fetch().all()
                    .forEach(record -> {
                        String paperId = stringVal(record, "paperId");
                        String name = stringVal(record, "name");
                        if (name != null) {
                            String dId = "dataset:" + name.toLowerCase().replaceAll("[^a-z0-9]", "-");
                            nodeMap.putIfAbsent(dId, buildNode(dId, name, "DATASET", 1));
                            if (paperId != null) {
                                links.add(new GraphLink("paper:" + paperId, dId, "USES_DATASET"));
                            }
                        }
                    });
        } catch (Exception e) {
            log.debug("Dataset nodes query: {}", e.getMessage());
        }

        // Metrics
        try {
            neo4jClient.query("""
                    MATCH (y:Year) WHERE y.year IN $years
                    MATCH (p:Paper)-[:PUBLISHED_IN]->(y)
                    MATCH (p)-[r:USES_METRIC]->(m:Metric)
                    RETURN DISTINCT p.paperId AS paperId, m.metricName AS name, r.value AS value
                    LIMIT 30
                    """)
                    .bind(years).to("years").fetch().all()
                    .forEach(record -> {
                        String paperId = stringVal(record, "paperId");
                        String name = stringVal(record, "name");
                        String value = stringVal(record, "value");
                        if (name != null) {
                            String mId = "metric:" + name.toLowerCase().replaceAll("[^a-z0-9]", "-");
                            String label = value != null && !value.isBlank() ? name + " (" + value + ")" : name;
                            nodeMap.putIfAbsent(mId, buildNode(mId, label, "METRIC", 1));
                            if (paperId != null) {
                                links.add(new GraphLink("paper:" + paperId, mId, "USES_METRIC"));
                            }
                        }
                    });
        } catch (Exception e) {
            log.debug("Metric nodes query: {}", e.getMessage());
        }

        // Methods
        try {
            neo4jClient.query("""
                    MATCH (y:Year) WHERE y.year IN $years
                    MATCH (p:Paper)-[:PUBLISHED_IN]->(y)
                    MATCH (p)-[r:USES_METHOD]->(m:Method)
                    RETURN DISTINCT p.paperId AS paperId, m.methodName AS name, r.context AS context
                    LIMIT 30
                    """)
                    .bind(years).to("years").fetch().all()
                    .forEach(record -> {
                        String paperId = stringVal(record, "paperId");
                        String name = stringVal(record, "name");
                        if (name != null) {
                            String mId = "method:" + name.toLowerCase().replaceAll("[^a-z0-9]", "-");
                            nodeMap.putIfAbsent(mId, buildNode(mId, name, "METHOD", 1));
                            if (paperId != null) {
                                links.add(new GraphLink("paper:" + paperId, mId, "USES_METHOD"));
                            }
                        }
                    });
        } catch (Exception e) {
            log.debug("Method nodes query: {}", e.getMessage());
        }
    }

    private String truncateTitle(String title, int maxLen) {
        if (title == null) return "";
        return title.length() > maxLen ? title.substring(0, maxLen) + "…" : title;
    }

    // ═══════════════════════════════════════════════════
    // STAGE 3: Focused Gap View
    // ═══════════════════════════════════════════════════

    public GraphResponse getFocusedGraph(String kw1, String kw2, List<Integer> years) {
        Map<String, GraphNode> nodeMap = new LinkedHashMap<>();
        List<GraphLink> links = new ArrayList<>();
        queryKeywordSide(kw1, kw2, years, nodeMap, links, "A");
        queryKeywordSide(kw2, kw1, years, nodeMap, links, "B");
        querySharedPapers(kw1, kw2, years, nodeMap, links);
        return new GraphResponse(new ArrayList<>(nodeMap.values()), links);
    }

    private void queryKeywordSide(String kw, String otherKw, List<Integer> years,
                                   Map<String, GraphNode> gNodes, List<GraphLink> gLinks, String side) {
        try {
            neo4jClient.query("""
                    MATCH (k:Keyword {normalizedText: $kw})<-[:HAS_KEYWORD]-(p:Paper)-[:PUBLISHED_IN]->(y:Year)
                    WHERE y.year IN $years AND NOT EXISTS {
                      MATCH (p)-[:HAS_KEYWORD]->(ok:Keyword {normalizedText: $otherKw})
                    }
                    RETURN p.paperId AS paperId, p.title AS title, k.keywordId AS kwId, k.text AS kwText
                    LIMIT 100
                    """)
                    .bind(kw).to("kw").bind(otherKw).to("otherKw").bind(years).to("years")
                    .fetch().all()
                    .forEach(record -> {
                        String pId = stringVal(record, "paperId");
                        String title = stringVal(record, "title");
                        String kId = stringVal(record, "kwId");
                        String kText = stringVal(record, "kwText");
                        if (pId != null) {
                            gNodes.putIfAbsent("paper:" + pId, buildNode("paper:" + pId,
                                    title != null ? title : "Paper", "PAPER_" + side, 1));
                        }
                        if (kId != null && pId != null) {
                            gNodes.putIfAbsent("keyword:" + kId, buildNode("keyword:" + kId, kText, "KEYWORD", 1));
                            gLinks.add(new GraphLink("paper:" + pId, "keyword:" + kId, "HAS_KEYWORD"));
                        }
                    });
        } catch (Exception e) { log.warn("Side query failed for {}: {}", kw, e.getMessage()); }
    }

    private void querySharedPapers(String kw1, String kw2, List<Integer> years,
                                    Map<String, GraphNode> gNodes, List<GraphLink> gLinks) {
        try {
            neo4jClient.query("""
                    MATCH (k1:Keyword {normalizedText: $kw1})<-[:HAS_KEYWORD]-(p:Paper)-[:HAS_KEYWORD]->(k2:Keyword {normalizedText: $kw2})
                    WHERE EXISTS { MATCH (p)-[:PUBLISHED_IN]->(y:Year) WHERE y.year IN $years }
                    RETURN p.paperId AS paperId, p.title AS title
                    LIMIT 50
                    """)
                    .bind(kw1).to("kw1").bind(kw2).to("kw2").bind(years).to("years")
                    .fetch().all()
                    .forEach(record -> {
                        String pId = stringVal(record, "paperId");
                        String title = stringVal(record, "title");
                        if (pId != null) {
                            gNodes.putIfAbsent("paper:" + pId, buildNode("paper:" + pId,
                                    title != null ? title : "Paper", "PAPER_SHARED", 1));
                        }
                    });
        } catch (Exception e) { log.warn("Shared papers query failed: {}", e.getMessage()); }
    }

    // ═══════════════════════════════════════════════════
    // GAP ANALYSIS
    // ═══════════════════════════════════════════════════

    public Map<String, Object> getGapAnalysis(String kw1, String kw2, List<Integer> years) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("keywordA", kw1);
        result.put("keywordB", kw2);
        result.put("kwACount", countPapers(kw1, years));
        result.put("kwBCount", countPapers(kw2, years));
        result.put("overlapCount", countOverlap(kw1, kw2, years));
        result.put("kwADatasets", topItems(kw1, years, "Dataset", "datasetName"));
        result.put("kwBDatasets", topItems(kw2, years, "Dataset", "datasetName"));
        result.put("kwAMetrics", topItems(kw1, years, "Metric", "metricName"));
        result.put("kwBMetrics", topItems(kw2, years, "Metric", "metricName"));
        result.put("kwAMethods", topItems(kw1, years, "Method", "methodName"));
        result.put("kwBMethods", topItems(kw2, years, "Method", "methodName"));
        result.put("topAuthors", topAuthors(kw1, kw2, years, 10));
        return result;
    }

    private long countPapers(String kw, List<Integer> years) {
        try {
            return neo4jClient.query("""
                    MATCH (k:Keyword {normalizedText: $kw})<-[:HAS_KEYWORD]-(p:Paper)-[:PUBLISHED_IN]->(y:Year)
                    WHERE y.year IN $years RETURN COUNT(DISTINCT p) AS cnt
                    """).bind(kw).to("kw").bind(years).to("years").fetch().one()
                    .map(r -> ((Number) r.get("cnt")).longValue()).orElse(0L);
        } catch (Exception e) { return 0L; }
    }

    private long countOverlap(String kw1, String kw2, List<Integer> years) {
        try {
            return neo4jClient.query("""
                    MATCH (k1:Keyword {normalizedText: $kw1})<-[:HAS_KEYWORD]-(p:Paper)-[:HAS_KEYWORD]->(k2:Keyword {normalizedText: $kw2})
                    WHERE EXISTS { MATCH (p)-[:PUBLISHED_IN]->(y:Year) WHERE y.year IN $years }
                    RETURN COUNT(DISTINCT p) AS cnt
                    """).bind(kw1).to("kw1").bind(kw2).to("kw2").bind(years).to("years").fetch().one()
                    .map(r -> ((Number) r.get("cnt")).longValue()).orElse(0L);
        } catch (Exception e) { return 0L; }
    }

    private List<Map<String, Object>> topItems(String kw, List<Integer> years, String label, String nameField) {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            String cypher = """
                    MATCH (k:Keyword {normalizedText: $kw})<-[:HAS_KEYWORD]-(p:Paper)-[:PUBLISHED_IN]->(y:Year)
                    WHERE y.year IN $years
                    MATCH (p)-[:USES_""" + label.toUpperCase() + "]->(d:" + label + """
                    )
                    RETURN d.""" + nameField + " AS name, COUNT(DISTINCT p) AS cnt ORDER BY cnt DESC LIMIT 10";
            neo4jClient.query(cypher).bind(kw).to("kw").bind(years).to("years")
                    .fetch().all().forEach(record -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("name", stringVal(record, "name"));
                        row.put("count", ((Number) record.get("cnt")).longValue());
                        results.add(row);
                    });
        } catch (Exception e) { /* return empty */ }
        return results;
    }

    private List<Map<String, Object>> topAuthors(String kw1, String kw2, List<Integer> years, int limit) {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            neo4jClient.query("""
                    MATCH (a:Author)-[:AUTHORED]->(p:Paper)-[:PUBLISHED_IN]->(y:Year)
                    WHERE y.year IN $years AND (
                      EXISTS { MATCH (p)-[:HAS_KEYWORD]->(:Keyword {normalizedText: $kw1}) }
                      OR EXISTS { MATCH (p)-[:HAS_KEYWORD]->(:Keyword {normalizedText: $kw2}) }
                    )
                    RETURN a.fullName AS name, a.hIndex AS hIndex, a.country AS country,
                           COUNT(DISTINCT p) AS paperCount
                    ORDER BY paperCount DESC LIMIT $limit
                    """).bind(kw1).to("kw1").bind(kw2).to("kw2").bind(years).to("years").bind(limit).to("limit")
                    .fetch().all().forEach(record -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("name", stringVal(record, "name"));
                        row.put("hIndex", record.get("hIndex"));
                        row.put("country", stringVal(record, "country"));
                        row.put("paperCount", ((Number) record.get("paperCount")).longValue());
                        results.add(row);
                    });
        } catch (Exception e) { /* return empty */ }
        return results;
    }

    // ═══════════════════════════════════════════════════
    // MCP TOOLS
    // ═══════════════════════════════════════════════════

    public List<Map<String, Object>> listAllKeywords() {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            neo4jClient.query("MATCH (k:Keyword) RETURN k.text AS text, k.normalizedText AS normalizedText ORDER BY k.text")
                    .fetch().all().forEach(record -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("text", stringVal(record, "text"));
                        row.put("normalizedText", stringVal(record, "normalizedText"));
                        results.add(row);
                    });
        } catch (Exception e) { /* return empty */ }
        return results;
    }

    public List<Map<String, Object>> getCooccurringKeywords(String kw, int yearsBack) {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            int startYear = java.time.Year.now().getValue() - yearsBack;
            neo4jClient.query("""
                    MATCH (k:Keyword {normalizedText: $kw})<-[:HAS_KEYWORD]-(p:Paper)-[:PUBLISHED_IN]->(y:Year)
                    WHERE y.year >= $startYear
                    MATCH (p)-[:HAS_KEYWORD]->(other:Keyword) WHERE other.normalizedText <> $kw
                    RETURN other.text AS text, other.normalizedText AS nText, COUNT(DISTINCT p) AS cnt
                    ORDER BY cnt DESC LIMIT 20
                    """).bind(kw).to("kw").bind(startYear).to("startYear").fetch().all()
                    .forEach(record -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("text", stringVal(record, "text"));
                        row.put("normalizedText", stringVal(record, "nText"));
                        row.put("paperCount", ((Number) record.get("cnt")).longValue());
                        results.add(row);
                    });
        } catch (Exception e) { /* return empty */ }
        return results;
    }

    public Map<String, Object> getGapScore(String kw1, String kw2) {
        int year = java.time.Year.now().getValue();
        List<Integer> years = List.of(year - 2, year - 1, year);
        long aOnly = countPapers(kw1, years) - countOverlap(kw1, kw2, years);
        long bOnly = countPapers(kw2, years) - countOverlap(kw1, kw2, years);
        long overlap = countOverlap(kw1, kw2, years);
        long total = aOnly + bOnly + overlap;
        int gapScore = total > 0 ? (int) ((aOnly + bOnly) * 100 / total) : 0;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("keywordA", kw1);
        result.put("keywordB", kw2);
        result.put("aOnly", aOnly);
        result.put("bOnly", bOnly);
        result.put("overlap", overlap);
        result.put("gapScore", gapScore);
        return result;
    }

    // ═══════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════

    private GraphNode buildNode(String id, String label, String group, int size) {
        return GraphNode.builder().id(id).label(label).group(group).size(size).build();
    }

    private static String stringVal(Map<String, Object> record, String key) {
        Object val = record.get(key);
        return val != null ? val.toString() : null;
    }

    private static class CacheEntry<T> {
        final T data;
        final long expiryTime;
        CacheEntry(T data) { this.data = data; this.expiryTime = System.currentTimeMillis() + CACHE_TTL_MS; }
        boolean isExpired() { return System.currentTimeMillis() > expiryTime; }
    }
}
