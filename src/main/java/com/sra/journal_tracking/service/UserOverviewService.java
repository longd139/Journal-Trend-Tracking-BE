package com.sra.journal_tracking.service;

import java.util.List;
import java.util.UUID;

import com.sra.journal_tracking.dto.follow.FollowResponse;
import com.sra.journal_tracking.dto.overview.UserOverviewResponse;

public interface UserOverviewService {
    UserOverviewResponse getUserOverview(String userEmail, UUID authorId);

    List<FollowResponse> getFollowedAuthors(String userEmail);
}
