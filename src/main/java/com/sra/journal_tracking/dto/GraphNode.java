package com.sra.journal_tracking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GraphNode {
    private String id;    // ID duy nhất (VD: DOI bài báo, tên tác giả)
    private String label; // Chữ hiển thị trên bong bóng (VD: Tên bài báo)
    private String group; // Dùng để Frontend tô màu (VD: "PAPER", "AUTHOR", "KEYWORD")
    private Integer size; // Kích thước node dựa trên frequency (nullable — chỉ có khi compute được)
}