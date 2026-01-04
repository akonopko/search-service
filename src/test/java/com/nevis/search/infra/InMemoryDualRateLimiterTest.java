package com.nevis.search.infra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class InMemoryDualRateLimiterTest {

    private InMemoryDualRateLimiter limiter;
    private static final int RPM_LIMIT = 3;
    private static final int TPM_LIMIT = 100;

    @BeforeEach
    void setUp() {
        limiter = new InMemoryDualRateLimiter(RPM_LIMIT, TPM_LIMIT);
    }

    @Test
    void shouldEnforceRpmLimit() throws InterruptedException {
        String key = "user-1";
        AtomicInteger counter = new AtomicInteger(0);

        for (int i = 0; i < RPM_LIMIT; i++) {
            limiter.execute(key, 1, counter::incrementAndGet);
        }

        assertThat(counter.get()).isEqualTo(RPM_LIMIT);

        CompletableFuture<Void> blockedTask = CompletableFuture.runAsync(() ->
            limiter.execute(key, 1, counter::incrementAndGet)
        );

        assertThrows(TimeoutException.class, () -> blockedTask.get(200, TimeUnit.MILLISECONDS));
        assertThat(counter.get()).isEqualTo(RPM_LIMIT);
    }

    @Test
    void shouldEnforceTpmLimit() throws InterruptedException {
        String key = "user-2";

        limiter.execute(key, TPM_LIMIT, () -> "full");

        CompletableFuture<String> blockedTask = CompletableFuture.supplyAsync(() ->
            limiter.execute(key, 1, () -> "denied")
        );

        assertThrows(TimeoutException.class, () -> blockedTask.get(200, TimeUnit.MILLISECONDS));
    }

    @Test
    void shouldIsolateLimitsByKey() throws InterruptedException {
        String keyA = "user-a";
        String keyB = "user-b";

        for (int i = 0; i < RPM_LIMIT; i++) {
            limiter.acquire(keyA, 1);
        }

        CompletableFuture<String> independentTask = CompletableFuture.supplyAsync(() ->
            limiter.execute(keyB, 1, () -> "success")
        );

        assertThat(independentTask.join()).isEqualTo("success");
    }

    @Test
    void shouldHandleConcurrentAccess() {
        String key = "concurrent-user";
        int taskCount = 10;
        AtomicInteger completedTasks = new AtomicInteger(0);

        CompletableFuture<?>[] futures = new CompletableFuture[taskCount];
        for (int i = 0; i < taskCount; i++) {
            futures[i] = CompletableFuture.runAsync(() ->
                limiter.execute(key, 5, completedTasks::incrementAndGet)
            );
        }

        long finishedCount = java.util.Arrays.stream(futures)
            .filter(CompletableFuture::isDone)
            .count();

        assertThat(finishedCount).isLessThanOrEqualTo(RPM_LIMIT);
    }
}