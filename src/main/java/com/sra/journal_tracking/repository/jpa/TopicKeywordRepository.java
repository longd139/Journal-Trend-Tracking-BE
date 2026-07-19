package com.sra.journal_tracking.repository.jpa;

import com.sra.journal_tracking.entity.jpa.TopicKeyword;
import com.sra.journal_tracking.entity.jpa.TopicKeywordId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TopicKeywordRepository extends JpaRepository<TopicKeyword, TopicKeywordId> {

    /**
     * Eagerly fetch topic-keyword mappings with scalar values to avoid LazyInitializationException.
     * Returns: [topicId (UUID), keywordText (String), weight (BigDecimal)]
     */
    @Query("SELECT tk.topic.topicId, tk.keyword.keywordText, tk.weight FROM TopicKeyword tk")
    List<Object[]> findAllTopicKeywordMappings();
}
