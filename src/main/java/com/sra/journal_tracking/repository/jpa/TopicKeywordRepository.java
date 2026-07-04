package com.sra.journal_tracking.repository.jpa;

import com.sra.journal_tracking.entity.jpa.TopicKeyword;
import com.sra.journal_tracking.entity.jpa.TopicKeywordId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TopicKeywordRepository extends JpaRepository<TopicKeyword, TopicKeywordId> {
}
