package com.sra.journal_tracking.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

/**
 * Kích hoạt Spring Cache với 2 CacheManager riêng biệt:
 * - defaultCacheManager (1 giờ TTL) — dùng cho các cache ngắn hạn
 * - searchCacheManager (7 ngày TTL) — dùng cho các API /api/search/...
 *   để tránh query lại dữ liệu tốn thời gian trong vòng 1 tuần.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Cache manager mặc định — TTL 1 giờ, tối đa 500 entries.
     * Dùng cho các cache không thuộc search (ví dụ: config, token).
     */
    @Primary
    @Bean("defaultCacheManager")
    public CacheManager defaultCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .maximumSize(500)
                .recordStats());
        cacheManager.setAsyncCacheMode(false);
        return cacheManager;
    }

    /**
     * Cache manager cho search — TTL 7 ngày, tối đa 1000 entries.
     * Dữ liệu search ít thay đổi, cache dài hạn giúp giảm tải DB và OpenAlex API.
     */
    @Bean("searchCacheManager")
    public CacheManager searchCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(7, TimeUnit.DAYS)
                .maximumSize(1000)
                .recordStats());
        cacheManager.setAsyncCacheMode(false);
        return cacheManager;
    }
}
