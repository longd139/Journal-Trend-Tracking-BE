package com.sra.journal_tracking.service;

import java.util.List;
import java.util.UUID;

import com.sra.journal_tracking.dto.follow.FollowRequest;
import com.sra.journal_tracking.dto.follow.FollowResponse;

public interface FollowService {

    FollowResponse addFollow(String email, FollowRequest request);

    List<FollowResponse> getMyFollows(String email);

    FollowResponse updateFollow(String email, UUID followId, boolean notifyEnabled);

    void deleteFollow(String email, UUID followId);
}
