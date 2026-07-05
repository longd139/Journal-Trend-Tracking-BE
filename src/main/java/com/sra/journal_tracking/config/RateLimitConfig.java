package com.sra.journal_tracking.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Rate limiting tiers — configurable via application.properties.
 * Each tier defines how many requests are allowed per minute.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitConfig {

    /** Requests per minute for public endpoints (auth, health, etc.). */
    private int publicRpm = 30;

    /** Requests per minute for authenticated user endpoints (search, bookmarks, etc.). */
    private int authenticatedRpm = 60;

    /** Requests per minute for admin endpoints. */
    private int adminRpm = 120;

    /** How many minutes before an idle bucket is evicted from memory. */
    private int evictionMinutes = 30;
}
