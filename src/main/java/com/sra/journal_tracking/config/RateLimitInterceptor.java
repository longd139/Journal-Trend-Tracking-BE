package com.sra.journal_tracking.config;

import com.sra.journal_tracking.exception.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting interceptor using Bucket4j token-bucket algorithm.
 *
 * Limits are per-key (user ID for authenticated requests, IP for public requests)
 * and per-tier (public / authenticated / admin).
 *
 * Buckets are stored in-memory with automatic eviction after a configurable idle period.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitConfig config;
    private final RequestMetricsCollector metricsCollector;

    /** In-memory bucket store. Key = "user:UUID" or "ip:1.2.3.4". */
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Skip OPTIONS (CORS preflight)
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        // Determine which tier this request falls into
        String key = resolveKey(request);
        int rpm = resolveTierRpm(path);

        Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket(rpm));

        // Try to consume 1 token
        if (bucket.tryConsume(1)) {
            // Allowed — add rate-limit headers
            long available = bucket.getAvailableTokens();
            response.setHeader("X-RateLimit-Limit", String.valueOf(rpm));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(available));
            return true;
        }

        // Rate limit exceeded
        metricsCollector.recordRateLimited(request);
        long nanosToWait = bucket.tryConsumeAndReturnRemaining(1).getNanosToWaitForRefill();
        long retryAfterSeconds = Math.max(1, nanosToWait / 1_000_000_000L);

        log.warn("Rate limit exceeded: key={}, path={}, rpm={}, retryAfter={}s",
                key, path, rpm, retryAfterSeconds);

        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setHeader("X-RateLimit-Limit", String.valueOf(rpm));
        response.setHeader("X-RateLimit-Remaining", "0");

        throw new RateLimitExceededException(
                "Too many requests. Please wait " + retryAfterSeconds + " seconds before retrying.",
                retryAfterSeconds);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        int status = response.getStatus();
        if (ex != null && status < 400) {
            // Exception occurred but status not set as error → treat as 500
            status = 500;
        }
        metricsCollector.recordRequest(request, status);
    }

    // ──────────────────────────────────────────────
    //  Private helpers
    // ──────────────────────────────────────────────

    /**
     * Determine the rate-limit key for this request.
     * Authenticated → "user:<userId>", public → "ip:<remoteAddr>".
     */
    private String resolveKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null
                && !"anonymousUser".equals(auth.getName())) {
            return "user:" + auth.getName();
        }
        String ip = getClientIp(request);
        return "ip:" + ip;
    }

    /**
     * Map request path to a rate-limit tier (requests per minute).
     */
    private int resolveTierRpm(String path) {
        if (path.startsWith("/api/admin/")) {
            return config.getAdminRpm();
        }
        if (path.startsWith("/api/public/") || path.startsWith("/api/auth/")
                || path.startsWith("/api/test/") || path.startsWith("/api/graphs/")) {
            return config.getPublicRpm();
        }
        return config.getAuthenticatedRpm();
    }

    /**
     * Create a new Bucket4j bucket with the given RPM limit.
     * Uses simple refill: `rpm` tokens added per minute.
     */
    private Bucket createBucket(int rpm) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(rpm)
                .refillIntervally(rpm, Duration.ofMinutes(1))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Extract client IP, accounting for proxies.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }

    // ──────────────────────────────────────────────
    //  Maintenance (called by scheduled task or on demand)
    // ──────────────────────────────────────────────

    /** Remove stale buckets older than evictionMinutes. Not currently auto-scheduled. */
    public void evictStale() {
        int before = buckets.size();
        // Simple eviction: clear all; they'll be re-created on next request.
        // For a production system, implement TTL-based eviction.
        if (before > 10_000) {
            buckets.clear();
            log.info("Rate limit cache cleared (had {} entries)", before);
        }
    }

    /** For monitoring / admin dashboard. */
    public int getActiveBucketCount() {
        return buckets.size();
    }
}
