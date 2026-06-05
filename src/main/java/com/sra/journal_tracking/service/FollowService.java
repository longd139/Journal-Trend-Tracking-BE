package com.sra.journal_tracking.service;

import java.util.List;
import java.util.UUID;

import com.sra.journal_tracking.dto.follow.FollowRequest;
import com.sra.journal_tracking.entity.jpa.Follow;

public interface FollowService {

    Follow createFollow(FollowRequest request);

    List<Follow> getFollows();

    void deleteFollow(UUID followId);
}