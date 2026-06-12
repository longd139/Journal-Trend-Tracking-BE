package com.sra.journal_tracking.service.impl;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sra.journal_tracking.dto.follow.FollowRequest;
import com.sra.journal_tracking.dto.follow.FollowResponse;
import com.sra.journal_tracking.entity.jpa.Follow;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.repository.jpa.FollowRepository;
import com.sra.journal_tracking.repository.jpa.JournalRepository;
import com.sra.journal_tracking.repository.jpa.KeywordRepository;
import com.sra.journal_tracking.repository.jpa.ResearchTopicRepository;
import com.sra.journal_tracking.repository.jpa.SystemConfigRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import com.sra.journal_tracking.service.FollowService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final JournalRepository journalRepository;
    private final ResearchTopicRepository researchTopicRepository;
    private final KeywordRepository keywordRepository;
    private final SystemConfigRepository systemConfigRepository;

    @Override
    @Transactional
    public FollowResponse addFollow(String email, FollowRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Validate XOR: exactly one target must be set
        int targetCount = 0;
        if (request.getJournalId() != null) targetCount++;
        if (request.getTopicId() != null) targetCount++;
        if (request.getKeywordId() != null) targetCount++;
        if (targetCount != 1) {
            throw new AppException(ErrorCode.FOLLOW_INVALID_TARGET);
        }

        // Validate target exists and check duplicate
        if (request.getJournalId() != null) {
            if (!journalRepository.existsById(request.getJournalId())) {
                throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            if (followRepository.findByUser_UserIdAndJournal_JournalId(user.getUserId(), request.getJournalId()).isPresent()) {
                throw new AppException(ErrorCode.FOLLOW_ALREADY_EXISTS);
            }
        } else if (request.getTopicId() != null) {
            if (!researchTopicRepository.existsById(request.getTopicId())) {
                throw new AppException(ErrorCode.TOPIC_NOT_FOUND);
            }
            if (followRepository.findByUser_UserIdAndTopic_TopicId(user.getUserId(), request.getTopicId()).isPresent()) {
                throw new AppException(ErrorCode.FOLLOW_ALREADY_EXISTS);
            }
        } else {
            if (!keywordRepository.existsById(request.getKeywordId())) {
                throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            if (followRepository.findByUser_UserIdAndKeyword_KeywordId(user.getUserId(), request.getKeywordId()).isPresent()) {
                throw new AppException(ErrorCode.FOLLOW_ALREADY_EXISTS);
            }
        }

        // Check limit for Academic Users
        checkFollowLimit(user);

        Follow follow = Follow.builder()
                .user(user)
                .journal(request.getJournalId() != null ? journalRepository.getReferenceById(request.getJournalId()) : null)
                .topic(request.getTopicId() != null ? researchTopicRepository.getReferenceById(request.getTopicId()) : null)
                .keyword(request.getKeywordId() != null ? keywordRepository.getReferenceById(request.getKeywordId()) : null)
                .notifyEnabled(request.getNotifyEnabled() != null ? request.getNotifyEnabled() : true)
                .build();

        follow = followRepository.save(follow);
        return mapToResponse(follow);
    }

    @Override
    public List<FollowResponse> getMyFollows(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        return followRepository.findByUser_UserId(user.getUserId())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public FollowResponse updateFollow(String email, UUID followId, boolean notifyEnabled) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Follow follow = followRepository.findById(followId)
                .orElseThrow(() -> new AppException(ErrorCode.FOLLOW_NOT_FOUND));

        // Verify ownership
        if (!follow.getUser().getUserId().equals(user.getUserId())) {
            throw new AppException(ErrorCode.FOLLOW_NOT_FOUND);
        }

        follow.setNotifyEnabled(notifyEnabled);
        follow = followRepository.save(follow);
        return mapToResponse(follow);
    }

    @Override
    @Transactional
    public void deleteFollow(String email, UUID followId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Follow follow = followRepository.findById(followId)
                .orElseThrow(() -> new AppException(ErrorCode.FOLLOW_NOT_FOUND));

        // Verify ownership
        if (!follow.getUser().getUserId().equals(user.getUserId())) {
            throw new AppException(ErrorCode.FOLLOW_NOT_FOUND);
        }

        followRepository.delete(follow);
    }

    private void checkFollowLimit(User user) {
        // Researchers have unlimited follows
        if (user.getRole() != null && "researcher".equalsIgnoreCase(user.getRole().getRoleName())) {
            return;
        }

        long count = followRepository.countByUser_UserId(user.getUserId());
        int limit = systemConfigRepository.findByConfigKey("academic_max_follows")
                .map(config -> {
                    try {
                        return Integer.parseInt(config.getConfigValue());
                    } catch (NumberFormatException e) {
                        return 20;
                    }
                })
                .orElse(20);

        if (count >= limit) {
            throw new AppException(ErrorCode.FOLLOW_LIMIT_EXCEEDED);
        }
    }

    private FollowResponse mapToResponse(Follow follow) {
        return FollowResponse.builder()
                .followId(follow.getFollowId())
                .journalId(follow.getJournal() != null ? follow.getJournal().getJournalId() : null)
                .journalName(follow.getJournal() != null ? follow.getJournal().getJournalName() : null)
                .topicId(follow.getTopic() != null ? follow.getTopic().getTopicId() : null)
                .topicName(follow.getTopic() != null ? follow.getTopic().getTopicName() : null)
                .keywordId(follow.getKeyword() != null ? follow.getKeyword().getKeywordId() : null)
                .keywordText(follow.getKeyword() != null ? follow.getKeyword().getKeywordText() : null)
                .notifyEnabled(follow.getNotifyEnabled())
                .createdAt(follow.getCreatedAt())
                .build();
    }
}
