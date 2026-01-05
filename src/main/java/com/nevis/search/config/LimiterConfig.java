package com.nevis.search.config;

import com.nevis.search.infra.InMemoryRpmRateLimiter;
import com.nevis.search.infra.RateLimiter;
import com.nevis.search.infra.InMemoryDualRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LimiterConfig {

    @Bean("chatLimiter")
    public RateLimiter chatLimiter() {
        return new InMemoryRpmRateLimiter(12);
    }

    @Bean("embeddingLimiter")
    public RateLimiter embeddingLimiter() {
        return new InMemoryDualRateLimiter(12, 500_000);
    }
}