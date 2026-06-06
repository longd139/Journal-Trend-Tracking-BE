package com.sra.journal_tracking.service;


import com.sra.journal_tracking.dto.GraphLink;
import com.sra.journal_tracking.dto.GraphNode;
import com.sra.journal_tracking.dto.GraphResponse;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GraphService {

    private final Neo4jClient neo4jClient;

    public GraphService(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    // Hàm lấy mạng lưới: Bài báo -> Từ khóa
    public GraphResponse getPaperKeywordGraph(String paperId) {
        String cypherQuery = 
            "MATCH (p:Paper {paperId: $paperId})-[r:HAS_KEYWORD]->(k:Keyword) " +
            "RETURN p.paperId AS pId, p.title AS pTitle, " +
            "k.keywordId AS kId, k.text AS kText";

        // ĐỔI SANG DÙNG MAP ĐỂ TỰ ĐỘNG LỌC TRÙNG ID
        Map<String, GraphNode> nodeMap = new HashMap<>();
        List<GraphLink> links = new ArrayList<>();

        neo4jClient.query(cypherQuery)
            .bind(paperId).to("paperId")
            .fetch()
            .all()
            .forEach(record -> {
                String pId = record.get("pId").toString();
                String pTitle = record.get("pTitle").toString();
                String kId = record.get("kId").toString();
                String kText = record.get("kText").toString();

                // Bỏ vào Map (nếu pId đã tồn tại, nó sẽ chỉ giữ 1 cái duy nhất)
                nodeMap.put(pId, new GraphNode(pId, pTitle, "PAPER"));
                nodeMap.put(kId, new GraphNode(kId, kText, "KEYWORD"));

                links.add(new GraphLink(pId, kId, "HAS_KEYWORD"));
            });

        // Chuyển Map values ngược lại thành List để trả về JSON
        return new GraphResponse(new ArrayList<>(nodeMap.values()), links);
    }
}
