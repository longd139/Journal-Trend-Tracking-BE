package com.sra.journal_tracking.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sra.journal_tracking.entity.jpa.Follow;

@Repository
public interface FollowRepository extends JpaRepository<Follow, UUID> {

    List<Follow> findByUser_UserId(UUID userId);

    long countByUser_UserId(UUID userId);

    Optional<Follow> findByUser_UserIdAndJournal_JournalId(UUID userId, UUID journalId);

    Optional<Follow> findByUser_UserIdAndTopic_TopicId(UUID userId, UUID topicId);

    Optional<Follow> findByUser_UserIdAndKeyword_KeywordId(UUID userId, UUID keywordId);
}
