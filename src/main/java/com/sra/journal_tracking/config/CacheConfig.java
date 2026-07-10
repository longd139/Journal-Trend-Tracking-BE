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
 * Kích hoạt Spring Cache với 3 CacheManager riêng biệt:
 * - defaultCacheManager (1 giờ TTL) — dùng cho các cache ngắn hạn
 * - searchCacheManager (7 ngày TTL) — dùng cho các API /api/search/...
 *   để tránh query lại dữ liệu tốn thời gian trong vòng 1 tuần.
 * - recommendationCacheManager (30 phút TTL) — dùng cho recommendation API
 *   để cân bằng giữa freshness và performance.
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

    /**
     * Cache manager cho recommendations — TTL 30 phút, tối đa 200 entries.
     * User interests change gradually; 30-min TTL balances freshness with performance.
     */
    @Bean("recommendationCacheManager")
    public CacheManager recommendationCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .maximumSize(200)
                .recordStats());
        cacheManager.setAsyncCacheMode(false);
        return cacheManager;
    }

    /**
     * Cache manager cho user overview — TTL 5 phút, tối đa 500 entries.
     * Short TTL keeps dashboard stats reasonably fresh while still reducing DB load
     * from repeated dashboard loads.
     */
    @Bean("overviewCacheManager")
    public CacheManager overviewCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(500)
                .recordStats());
        cacheManager.setAsyncCacheMode(false);
        return cacheManager;
    }
}
