package com.sra.journal_tracking.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sra.journal_tracking.entity.jpa.Follow;

@Repository
public interface FollowRepository extends JpaRepository<Follow, UUID> {

    List<Follow> findByUser_UserId(UUID userId);

    long countByUser_UserId(UUID userId);

    Optional<Follow> findByUser_UserIdAndJournal_JournalId(UUID userId, UUID journalId);

    Optional<Follow> findByUser_UserIdAndTopic_TopicId(UUID userId, UUID topicId);

    Optional<Follow> findByUser_UserIdAndKeyword_KeywordId(UUID userId, UUID keywordId);

    /** Tìm tất cả user đang follow 1 journal và bật notification. */
    @Query("SELECT f FROM Follow f JOIN FETCH f.user WHERE f.journal.journalId = :journalId AND f.notifyEnabled = true")
    List<Follow> findByJournal_JournalIdAndNotifyEnabledTrue(@Param("journalId") UUID journalId);

    /** Tìm tất cả user đang follow 1 keyword và bật notification. */
    @Query("SELECT f FROM Follow f JOIN FETCH f.user WHERE f.keyword.keywordId = :keywordId AND f.notifyEnabled = true")
    List<Follow> findByKeyword_KeywordIdAndNotifyEnabledTrue(@Param("keywordId") UUID keywordId);
}
