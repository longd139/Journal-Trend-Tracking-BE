package com.sra.journal_tracking.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GraphNode {
    private String id;     // ID duy nhất (VD: DOI bài báo, tên tác giả)
    private String label;  // Chữ hiển thị trên bong bóng (VD: Tên bài báo)
    private String group;  // Dùng để Frontend tô màu (VD: "PAPER", "AUTHOR", "KEYWORD")
}