package com.sra.journal_tracking.entity.jpa;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "TOPIC_KEYWORD")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"topic", "keyword"})
@ToString(exclude = {"topic", "keyword"})
public class TopicKeyword {

    @EmbeddedId
    private TopicKeywordId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("topicId")
    @JoinColumn(name = "TopicID", nullable = false)
    private ResearchTopic topic;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("keywordId")
    @JoinColumn(name = "KeywordID", nullable = false)
    private Keyword keyword;

    @Column(name = "Weight", nullable = false, precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal weight = new BigDecimal("1.0");
}
