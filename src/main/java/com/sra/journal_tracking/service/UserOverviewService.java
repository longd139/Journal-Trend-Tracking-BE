package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.overview.UserOverviewResponse;

public interface UserOverviewService {
    UserOverviewResponse getUserOverview(String userEmail);
}
