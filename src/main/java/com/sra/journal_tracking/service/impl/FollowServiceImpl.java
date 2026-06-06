package com.sra.journal_tracking.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.sra.journal_tracking.dto.follow.FollowRequest;
import com.sra.journal_tracking.entity.jpa.Follow;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.repository.jpa.FollowRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import com.sra.journal_tracking.service.FollowService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private static final int MAX_FOLLOW = 20;

    private final FollowRepository followRepository;
    private final UserRepository userRepository;

    private User getCurrentUser() {

        String email = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new AppException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    public Follow createFollow(FollowRequest request) {

        User user = getCurrentUser();

        if (user.getRole() != null &&
            "ACADEMIC_USER".equalsIgnoreCase(
                    user.getRole().getRoleName())) {

            long count =
                    followRepository.countByUser(user);

            if (count >= MAX_FOLLOW) {
                throw new AppException(
                        ErrorCode.FOLLOW_LIMIT_EXCEEDED);
            }
        }

        Follow follow = Follow.builder()
                .user(user)
                .type(request.getType())
                .targetId(request.getTargetId())
                .build();

        return followRepository.save(follow);
    }

    @Override
    public List<Follow> getFollows() {
        return followRepository.findByUser(getCurrentUser());
    }

    @Override
    public void deleteFollow(UUID followId) {

        Follow follow =
                followRepository.findById(followId)
                        .orElseThrow(() ->
                                new AppException(
                                        ErrorCode.FOLLOW_NOT_FOUND));

        followRepository.delete(follow);
    }
}