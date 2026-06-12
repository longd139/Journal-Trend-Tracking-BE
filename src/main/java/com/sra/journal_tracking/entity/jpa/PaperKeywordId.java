package com.sra.journal_tracking.entity.jpa;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperKeywordId implements Serializable {

    private UUID paperId;
    private UUID keywordId;
}
