package com.sra.journal_tracking.entity.jpa;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicKeywordId implements Serializable {

    @Column(name = "TopicID", nullable = false)
    private UUID topicId;

    @Column(name = "KeywordID", nullable = false)
    private UUID keywordId;
}
