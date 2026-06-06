package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.response.DashboardResponse;
import com.sra.journal_tracking.service.impl.TrendDashboardService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trends")
public class TrendController {

    private final TrendDashboardService trendDashboardService;

    public TrendController(
            TrendDashboardService trendDashboardService) {

        this.trendDashboardService = trendDashboardService;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {

        return trendDashboardService.getDashboard();
    }
}