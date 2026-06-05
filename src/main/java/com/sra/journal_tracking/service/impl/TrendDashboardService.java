package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.dto.response.DashboardResponse;
import org.springframework.stereotype.Service;

@Service
public class TrendDashboardService {

    public DashboardResponse getDashboard() {

        return new DashboardResponse(
                2847,
                52100000,
                48,
                "Deep Learning"
        );
    }
}