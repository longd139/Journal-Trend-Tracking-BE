package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.response.AppResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Admin API để quản lý cache.
 * Cho phép admin xóa toàn bộ cache hoặc một cache cụ thể khi cache gặp vấn đề.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/cache")
@PreAuthorize("hasRole('ADMIN')")
public class CacheManageController {

    private final CacheManager defaultCacheManager;
    private final CacheManager searchCacheManager;

    public CacheManageController(
            CacheManager defaultCacheManager,
            @Qualifier("searchCacheManager") CacheManager searchCacheManager) {
        this.defaultCacheManager = defaultCacheManager;
        this.searchCacheManager = searchCacheManager;
    }

    @Operation(
            summary = "Xóa toàn bộ cache (admin only)",
            description = "Xóa tất cả các cache đang hoạt động trong hệ thống, "
                        + "bao gồm cả cache mặc định (1h TTL) và cache search (7 ngày TTL). "
                        + "Sử dụng khi dữ liệu cache bị sai lệch hoặc cần refresh ngay lập tức."
    )
    @SecurityRequirement(name = "Bearer Authentication")
    @DeleteMapping
    public ResponseEntity<AppResponse<CacheClearResult>> clearAllCaches() {
        log.warn("ADMIN: Clearing ALL caches");

        List<String> cleared = new ArrayList<>();
        clearAllCachesFromManager(defaultCacheManager, cleared);
        clearAllCachesFromManager(searchCacheManager, cleared);

        CacheClearResult result = new CacheClearResult("all", cleared.size(), cleared);
        log.info("ADMIN: Cleared {} cache(s): {}", cleared.size(), cleared);
        return ResponseEntity.ok(AppResponse.success("All caches cleared successfully", result));
    }

    @Operation(
            summary = "Xóa một cache cụ thể theo tên (admin only)",
            description = "Xóa một cache cụ thể bằng tên. Ví dụ: 'search:keywordQuickStats', "
                        + "'search:journalQuickStats', 'search:authorQuickStats', ... "
                        + "Hữu ích khi chỉ một loại dữ liệu bị sai mà không cần xóa toàn bộ."
    )
    @SecurityRequirement(name = "Bearer Authentication")
    @DeleteMapping("/{cacheName}")
    public ResponseEntity<AppResponse<CacheClearResult>> clearCacheByName(
            @PathVariable String cacheName) {
        log.warn("ADMIN: Clearing cache: '{}'", cacheName);

        List<String> cleared = new ArrayList<>();

        // Thử xóa từ cả 2 CacheManager
        clearCacheByNameFromManager(defaultCacheManager, cacheName, cleared);
        clearCacheByNameFromManager(searchCacheManager, cacheName, cleared);

        if (cleared.isEmpty()) {
            return ResponseEntity.ok(AppResponse.success(
                    "Cache '" + cacheName + "' not found or already empty",
                    new CacheClearResult(cacheName, 0, List.of())));
        }

        CacheClearResult result = new CacheClearResult(cacheName, cleared.size(), cleared);
        log.info("ADMIN: Cleared cache '{}' from managers: {}", cacheName, cleared);
        return ResponseEntity.ok(AppResponse.success("Cache '" + cacheName + "' cleared successfully", result));
    }

    private void clearAllCachesFromManager(CacheManager cacheManager, List<String> cleared) {
        cacheManager.getCacheNames().forEach(name -> {
            Objects.requireNonNull(cacheManager.getCache(name)).clear();
            cleared.add(name);
        });
    }

    private void clearCacheByNameFromManager(CacheManager cacheManager, String cacheName, List<String> cleared) {
        Collection<String> cacheNames = cacheManager.getCacheNames();
        if (cacheNames.contains(cacheName)) {
            Objects.requireNonNull(cacheManager.getCache(cacheName)).clear();
            cleared.add(cacheManager.toString());
        }
    }

    /**
     * Kết quả trả về sau khi clear cache.
     */
    public record CacheClearResult(
            String cacheName,
            int clearedCount,
            List<String> clearedCaches
    ) {}
}
