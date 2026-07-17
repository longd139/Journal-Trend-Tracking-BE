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
     * RestTemplate for DeepSeek API calls (OpenAI-compatible).
     * Longer timeouts than search — AI models can be slow.
     */
    @Bean(name = "deepseekRestTemplate")
    public RestTemplate deepseekRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(120))
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

    /**
     * RestTemplate for downloading PDFs from external sources.
     * Short connect timeout (15s) but longer read timeout (60s) for large PDFs.
     * Sets a browser-like User-Agent to avoid 403 blocks.
     */
    @Bean(name = "pdfRestTemplate")
    public RestTemplate pdfRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(15))
                .readTimeout(Duration.ofSeconds(60))
                .defaultHeader("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                .build();
    }
}
