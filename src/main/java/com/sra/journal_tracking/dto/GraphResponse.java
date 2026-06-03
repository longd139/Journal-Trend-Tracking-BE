package com.sra.journal_tracking.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class GraphResponse {
    private List<GraphNode> nodes;
    private List<GraphLink> links;
}