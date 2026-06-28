package com.sra.journal_tracking.dto.journal;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TopJournalDTO {
    private String journalId;
    private String journalName;
    private String publisher;
    private String issn;
    private BigDecimal impactFactor;
    private String quartile;
}
