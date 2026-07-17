package com.sra.journal_tracking.dto.idea;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LiteratureReviewDTO {
    private String text;
    private List<Reference> references;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Reference {
        private Integer number;
        private String paperTitle;
        private String authors;
        private Integer year;
        private String journal;
        private String doi;
    }
}
