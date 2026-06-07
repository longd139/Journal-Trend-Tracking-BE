package com.sra.journal_tracking.dto.paper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaperDetailResponseDTO {
    private UUID paperId;
    private String title;
    private String abstractText;
    private String doi;
    private Short pubYear;
    private LocalDate pubDate;
    private Integer citationCount;
    private Boolean isOpenAccess;
    private String journalName;
    private UUID journalId;
    private String fieldName;
    private UUID fieldId;
    private List<AuthorDTO> authors;
    private List<KeywordDTO> keywords;
    private String sourceUrl;
    private Boolean pdfAvailable;
    private String downloadUrl;
    private Double rating;
    private Integer downloadCount;
    private Integer commentCount;
    private LocalDateTime createdAt;
}
