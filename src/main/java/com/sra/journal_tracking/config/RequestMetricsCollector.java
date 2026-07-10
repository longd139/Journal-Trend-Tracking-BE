package com.sra.journal_tracking.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory ring buffer collecting per-minute HTTP request metrics
 * for the admin dashboard charts (Request Volume, Visitor Traffic).
 *
 * Tracks 1440 slots = 24 hours × 60 minutes. Thread-safe for concurrent
 * requests via synchronized access to each slot's counters.
 */
@Slf4j
@Component
public class RequestMetricsCollector {

    private static final int SLOT_COUNT = 1440; // 24 hours × 60 minutes
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("HH:00");

    private final MinuteBucket[] ringBuffer = new MinuteBucket[SLOT_COUNT];

    public RequestMetricsCollector() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            ringBuffer[i] = new MinuteBucket();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Recording
    // ═══════════════════════════════════════════════════════════════

    /**
     * Record a completed HTTP request.
     * Called from RateLimitInterceptor.afterCompletion().
     *
     * @param request    the HTTP request (for client IP)
     * @param statusCode the HTTP response status code
     */
    public void recordRequest(HttpServletRequest request, int statusCode) {
        long epochMinute = System.currentTimeMillis() / 60_000;
        int slot = (int) (epochMinute % SLOT_COUNT);

        MinuteBucket bucket = ringBuffer[slot];
        bucket.ensureEpoch(epochMinute);

        bucket.totalRequests.incrementAndGet();
        if (statusCode >= 400) {
            bucket.errorCount.incrementAndGet();
        }

        String ip = getClientIp(request);
        if (ip != null && !ip.isBlank()) {
            bucket.uniqueIps.add(ip);
        }
    }

    /**
     * Record a rate-limited (blocked) request. Always counted as an error.
     */
    public void recordRateLimited(HttpServletRequest request) {
        long epochMinute = System.currentTimeMillis() / 60_000;
        int slot = (int) (epochMinute % SLOT_COUNT);

        MinuteBucket bucket = ringBuffer[slot];
        bucket.ensureEpoch(epochMinute);

        bucket.totalRequests.incrementAndGet();
        bucket.errorCount.incrementAndGet();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Query: Request Volume (24h time-series)
    // ═══════════════════════════════════════════════════════════════

    public record TimeSeriesPoint(String time, int requests, int errors) {}

    /**
     * Returns minute-by-minute request/error counts for the last 24 hours.
     */
    public List<TimeSeriesPoint> getRequestVolume() {
        long nowMinute = System.currentTimeMillis() / 60_000;
        long startMinute = nowMinute - SLOT_COUNT + 1;
        List<TimeSeriesPoint> points = new ArrayList<>(SLOT_COUNT);

        for (long m = startMinute; m <= nowMinute; m++) {
            int slot = (int) (m % SLOT_COUNT);
            MinuteBucket bucket = ringBuffer[slot];
            if (bucket.epochMinute == m) {
                String timeLabel = LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(m * 60), ZoneId.systemDefault()
                ).format(HH_MM);
                points.add(new TimeSeriesPoint(
                        timeLabel, bucket.totalRequests.get(), bucket.errorCount.get()));
            } else {
                // No data for this minute → zero-filled
                String timeLabel = LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(m * 60), ZoneId.systemDefault()
                ).format(HH_MM);
                points.add(new TimeSeriesPoint(timeLabel, 0, 0));
            }
        }
        return points;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Query: Visitor Traffic (hourly, today vs yesterday)
    // ═══════════════════════════════════════════════════════════════

    public record VisitorTrafficPoint(String hour, int todayVisitors, int yesterdayVisitors) {}

    /**
     * Returns hourly unique visitor counts for today and yesterday (24 buckets each).
     */
    public List<VisitorTrafficPoint> getVisitorTraffic() {
        long nowMinute = System.currentTimeMillis() / 60_000;
        List<VisitorTrafficPoint> points = new ArrayList<>(24);

        // Aggregate unique IPs per hour for today (last 24h from current minute)
        for (int h = 23; h >= 0; h--) {
            long hourStart = nowMinute - (h + 1) * 60L + 1;
            long hourEnd = nowMinute - h * 60L;

            Set<String> todayIps = new HashSet<>();
            Set<String> yesterdayIps = new HashSet<>();

            for (long m = hourStart; m <= hourEnd; m++) {
                long todayM = m;
                long yesterdayM = m - 1440; // 24 hours earlier

                int todaySlot = (int) (todayM % SLOT_COUNT);
                MinuteBucket todayBucket = ringBuffer[todaySlot];
                if (todayBucket.epochMinute == todayM) {
                    todayIps.addAll(todayBucket.uniqueIps);
                }

                int yesterdaySlot = (int) (yesterdayM % SLOT_COUNT);
                MinuteBucket yesterdayBucket = ringBuffer[yesterdaySlot];
                if (yesterdayBucket.epochMinute == yesterdayM) {
                    yesterdayIps.addAll(yesterdayBucket.uniqueIps);
                }
            }

            String hourLabel = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(hourStart * 60), ZoneId.systemDefault()
            ).format(HOUR_FMT);

            points.add(new VisitorTrafficPoint(
                    hourLabel, todayIps.size(), yesterdayIps.size()));
        }
        return points;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) {
            return xri.trim();
        }
        return request.getRemoteAddr();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Inner: per-minute bucket
    // ═══════════════════════════════════════════════════════════════

    private static class MinuteBucket {
        long epochMinute = -1;
        final AtomicInteger totalRequests = new AtomicInteger(0);
        final AtomicInteger errorCount = new AtomicInteger(0);
        final Set<String> uniqueIps = ConcurrentHashMap.newKeySet();

        synchronized void ensureEpoch(long epoch) {
            if (this.epochMinute != epoch) {
                this.epochMinute = epoch;
                this.totalRequests.set(0);
                this.errorCount.set(0);
                this.uniqueIps.clear();
            }
        }
    }
}
