package com.sra.journal_tracking.config;

import java.time.Duration;
import java.util.concurrent.Executor;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableAsync
@EnableScheduling
public class AppConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Fast RestTemplate for Gemini API calls.
     * Short timeouts ensure we fall back to local expansion quickly
     * instead of hanging the graph search.
     */
    @Bean(name = "geminiRestTemplate")
    public RestTemplate geminiRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("async-sync-");
        executor.initialize();
        return executor;
    }

    /**
     * Separate executor for user-facing async operations (graph search).
     * Keeps user requests responsive even during bulk sync.
     */
    @Bean(name = "userTaskExecutor")
    public Executor userTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("async-user-");
        executor.initialize();
        return executor;
    }
}
