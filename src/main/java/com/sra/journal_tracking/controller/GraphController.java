package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.GraphResponse;
import com.sra.journal_tracking.service.GraphService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/graphs")
// @CrossOrigin("*") // Mở block này nếu Frontend chạy khác port (vd: React port 3000)
public class GraphController {

    private final GraphService graphService;

    public GraphController(GraphService graphService) {
        this.graphService = graphService;
    }

    @GetMapping("/paper/{paperId}")
    public ResponseEntity<GraphResponse> getGraphForPaper(@PathVariable String paperId) {
        GraphResponse response = graphService.getPaperKeywordGraph(paperId);
        return ResponseEntity.ok(response);
    }
}