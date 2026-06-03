package com.sra.journal_tracking.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GraphLink {
    private String source; // ID của Node bắt đầu
    private String target; // ID của Node kết thúc
    private String label;  // Tên mối quan hệ (VD: "HAS_KEYWORD", "WROTE")
}