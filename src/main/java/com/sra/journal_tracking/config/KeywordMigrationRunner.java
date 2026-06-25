package com.sra.journal_tracking.config;

import com.sra.journal_tracking.service.DataSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * One-time runner to re-extract keywords for all existing papers on startup.
 * Runs asynchronously so the app starts immediately.
 * Remove this file after the first successful run.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeywordMigrationRunner implements CommandLineRunner {

    private final DataSyncService dataSyncService;

    @Override
    public void run(String... args) {
        Thread migrationThread = new Thread(() -> {
            try {
                Thread.sleep(5000); // wait for app to fully start
                log.info("=== Starting keyword migration for all existing papers ===");
                var result = dataSyncService.reExtractKeywords();
                log.info("=== Keyword migration done: {} ===", result);
            } catch (Exception e) {
                log.error("Keyword migration failed: {}", e.getMessage(), e);
            }
        }, "keyword-migration");
        migrationThread.setDaemon(true);
        migrationThread.start();
    }
}
