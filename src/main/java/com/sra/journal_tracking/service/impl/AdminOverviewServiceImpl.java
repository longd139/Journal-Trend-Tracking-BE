package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.dto.admin.AdminOverviewResponse;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import com.sra.journal_tracking.service.AdminOverviewService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

/**
 * Reads live system metrics from Micrometer {@link MeterRegistry}
 * (auto-configured by spring-boot-starter-actuator) and combines them
 * with database queries to populate the admin overview dashboard.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminOverviewServiceImpl implements AdminOverviewService {

    private final UserRepository userRepository;
    private final MeterRegistry meterRegistry;
    private final DataSource dataSource;

    private static final String METRIC_HTTP_REQUESTS = "http.server.requests";

    @Override
    public AdminOverviewResponse getOverview() {
        // ── Card 1: Active Users ──
        long activeUsers = userRepository.countByIsActiveTrue();

        // ── Cards 2-4: Total Requests, Avg Latency, Error Rate ──
        Timer timer = meterRegistry.find(METRIC_HTTP_REQUESTS).timer();
        long totalRequests = 0;
        double avgLatencyMs = 0.0;
        long errorCount = 0;

        if (timer != null) {
            totalRequests = timer.count();
            double avgLatencySeconds = timer.mean(TimeUnit.SECONDS);
            avgLatencyMs = avgLatencySeconds * 1000.0;
        }

        // Count errors separately — micrometer tags 4xx/5xx responses with "outcome" tag
        errorCount = countErrors();
        double errorRate = totalRequests > 0
                ? (double) errorCount / totalRequests * 100.0
                : 0.0;

        // ── Card 5: DB Size ──
        long dbSizeMb = getDatabaseSizeMb();

        // ── Auxiliary: Uptime ──
        String uptime = getUptime();

        // ── Auxiliary: Storage ──
        long totalStorageMb = dbSizeMb; // can add Neo4j size later if needed

        // ── Auxiliary: Requests last hour (approximation from total) ──
        long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
        double hoursUp = uptimeMillis / (1000.0 * 60 * 60);
        long requestsLastHour = hoursUp > 0
                ? Math.round(totalRequests / hoursUp)
                : 0;

        AdminOverviewResponse response = AdminOverviewResponse.builder()
                .activeUsers(activeUsers)
                .totalRequests(totalRequests)
                .avgLatencyMs(round2(avgLatencyMs))
                .errorRate(round2(errorRate))
                .dbSizeMb(dbSizeMb)
                .uptime(uptime)
                .totalStorageMb(totalStorageMb)
                .errorCount(errorCount)
                .requestsLastHour(requestsLastHour)
                .build();

        log.info("Admin overview: activeUsers={}, totalRequests={}, avgLatencyMs={}, errorRate={}%, dbSizeMb={}",
                activeUsers, totalRequests, round2(avgLatencyMs), round2(errorRate), dbSizeMb);

        return response;
    }

    // ──────────────────────────────────────────────
    //  Private helpers
    // ──────────────────────────────────────────────

    /**
     * Count errors by summing all non-SUCCESS outcomes from Micrometer.
     * Micrometer tags HTTP responses with outcome: SUCCESS, CLIENT_ERROR, SERVER_ERROR.
     */
    private long countErrors() {
        try {
            // Total count
            Timer totalTimer = meterRegistry.find(METRIC_HTTP_REQUESTS).timer();
            if (totalTimer == null) return 0;

            // Count CLIENT_ERROR (4xx)
            Timer clientErrors = meterRegistry.find(METRIC_HTTP_REQUESTS)
                    .tag("outcome", "CLIENT_ERROR")
                    .timer();
            long clientErrorCount = clientErrors != null ? clientErrors.count() : 0;

            // Count SERVER_ERROR (5xx)
            Timer serverErrors = meterRegistry.find(METRIC_HTTP_REQUESTS)
                    .tag("outcome", "SERVER_ERROR")
                    .timer();
            long serverErrorCount = serverErrors != null ? serverErrors.count() : 0;

            return clientErrorCount + serverErrorCount;
        } catch (Exception e) {
            log.debug("Could not read error metrics from MeterRegistry: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Query SQL Server for total database file size in megabytes.
     */
    private long getDatabaseSizeMb() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT SUM(CAST(size AS BIGINT)) * 8 / 1024 FROM sys.database_files")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            log.warn("Failed to query DB size: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * Human-readable uptime string (e.g. "3 days 5 hours 12 minutes").
     */
    private String getUptime() {
        long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
        long seconds = uptimeMillis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%d days %d hours %d minutes", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format("%d hours %d minutes", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%d minutes %d seconds", minutes, seconds % 60);
        } else {
            return String.format("%d seconds", seconds);
        }
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
