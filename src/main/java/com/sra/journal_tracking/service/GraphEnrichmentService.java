package com.sra.journal_tracking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Saves paper enrichment data (metrics, datasets, methods) to Neo4j
 * as separate nodes with relationships to Paper nodes.
 */
@Service
public class GraphEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(GraphEnrichmentService.class);

    private final Neo4jClient neo4jClient;

    public GraphEnrichmentService(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    /**
     * Save enriched data (metrics, datasets, methods) to Neo4j for a paper.
     * Uses MERGE to avoid duplicates — safe to re-run.
     */
    public void saveEnrichmentData(String paperId,
                                    PaperEnrichmentService.EnrichedData data) {
        if (data == null || data.isEmpty()) {
            log.debug("No enrichment data to save for paper {}", paperId);
            return;
        }

        saveDatasets(paperId, data.datasets);
        saveMetrics(paperId, data.metrics);
        saveMethods(paperId, data.methods);

        log.info("Neo4j enrichment saved for paper {}: {} datasets, {} metrics, {} methods",
                paperId, data.datasets.size(), data.metrics.size(), data.methods.size());
    }

    private void saveDatasets(String paperId, List<PaperEnrichmentService.ExtractedItem> datasets) {
        String cypher = """
                MATCH (p:Paper {paperId: $paperId})
                UNWIND $items AS item
                MERGE (d:Dataset {normalizedName: item.normalizedName})
                ON CREATE SET d.datasetName = item.name
                MERGE (p)-[:USES_DATASET {context: item.context}]->(d)
                """;
        executeSave(paperId, cypher, datasets);
    }

    private void saveMetrics(String paperId, List<PaperEnrichmentService.ExtractedItem> metrics) {
        String cypher = """
                MATCH (p:Paper {paperId: $paperId})
                UNWIND $items AS item
                MERGE (m:Metric {normalizedName: item.normalizedName})
                ON CREATE SET m.metricName = item.name,
                              m.category = item.category
                MERGE (p)-[:USES_METRIC {value: item.value, context: item.context}]->(m)
                """;
        executeSave(paperId, cypher, metrics);
    }

    private void saveMethods(String paperId, List<PaperEnrichmentService.ExtractedItem> methods) {
        String cypher = """
                MATCH (p:Paper {paperId: $paperId})
                UNWIND $items AS item
                MERGE (m:Method {normalizedName: item.normalizedName})
                ON CREATE SET m.methodName = item.name,
                              m.category = item.category
                MERGE (p)-[:USES_METHOD {context: item.context}]->(m)
                """;
        executeSave(paperId, cypher, methods);
    }

    private void executeSave(String paperId, String cypher,
                              List<PaperEnrichmentService.ExtractedItem> items) {
        if (items.isEmpty()) return;

        List<Map<String, String>> itemMaps = items.stream().map(i -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("name", i.name);
            m.put("normalizedName", i.name.toLowerCase().trim());
            m.put("value", i.value != null ? i.value : "");
            m.put("context", i.context != null ? i.context : "");
            m.put("category", inferCategory(i));
            return m;
        }).toList();

        try {
            neo4jClient.query(cypher)
                    .bind(paperId).to("paperId")
                    .bind(itemMaps).to("items")
                    .run();
        } catch (Exception e) {
            log.warn("Failed to save enrichment data for paper {}: {}", paperId, e.getMessage());
        }
    }

    /**
     * Infer category from item name for basic classification.
     */
    private String inferCategory(PaperEnrichmentService.ExtractedItem item) {
        String name = item.name.toLowerCase();
        // Metrics categories
        if (name.contains("accurac") || name.contains("precision") || name.contains("recall")
                || name.contains("f1") || name.contains("auc") || name.contains("roc")) {
            return "performance";
        }
        if (name.contains("mse") || name.contains("rmse") || name.contains("mae")
                || name.contains("error") || name.contains("loss")) {
            return "error";
        }
        if (name.contains("time") || name.contains("latency") || name.contains("throughput")
                || name.contains("speed") || name.contains("memory") || name.contains("power")) {
            return "efficiency";
        }
        // Method categories
        if (name.contains("cnn") || name.contains("lstm") || name.contains("transformer")
                || name.contains("resnet") || name.contains("bert") || name.contains("gan")
                || name.contains("rnn") || name.contains("dnn")) {
            return "architecture";
        }
        if (name.contains("learning") || name.contains("training") || name.contains("optimization")
                || name.contains("gradient") || name.contains("backprop")) {
            return "technique";
        }
        return "other";
    }
}
