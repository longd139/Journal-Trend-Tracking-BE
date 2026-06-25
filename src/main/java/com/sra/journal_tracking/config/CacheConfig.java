package com.sra.journal_tracking.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Kích hoạt Spring Cache.
 * Cấu hình Caffeine cache (TTL, max size) được đặt trong application.properties
 * để tận dụng Spring Boot auto-configuration.
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
