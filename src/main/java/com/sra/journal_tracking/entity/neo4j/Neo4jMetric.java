package com.sra.journal_tracking.entity.neo4j;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import lombok.Getter;
import lombok.Setter;

@Node("Metric")
@Getter
@Setter
public class Neo4jMetric {

    @Id
    private String metricId; // UUID deterministic từ normalizedName

    private String metricName;

    private String normalizedName;

    private String category; // performance, efficiency, quality, statistical, other
}
