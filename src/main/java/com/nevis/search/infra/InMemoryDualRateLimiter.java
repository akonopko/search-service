package com.nevis.search.infra;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.SneakyThrows;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryDualRateLimiter implements RateLimiter {

    private final ConcurrentHashMap<String, Bucket> rpmBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> tpmBuckets = new ConcurrentHashMap<>();

    private final int rpmLimit;
    private final int tpmLimit;

    public InMemoryDualRateLimiter(int rpmLimit, int tpmLimit) {
        this.rpmLimit = rpmLimit;
        this.tpmLimit = tpmLimit;
    }

    private Bucket createRpmBucket() {
        return Bucket.builder()
            .addLimit(Bandwidth.classic(rpmLimit, Refill.greedy(rpmLimit, Duration.ofMinutes(1))))
            .build();
    }

    private Bucket createTpmBucket() {
        return Bucket.builder()
            .addLimit(Bandwidth.classic(tpmLimit, Refill.greedy(tpmLimit, Duration.ofMinutes(1))))
            .build();
    }

    @Override
    @SneakyThrows
    public void acquire(String key, int tokens) {
        Bucket rpmBucket = rpmBuckets.computeIfAbsent(key, k -> createRpmBucket());
        Bucket tpmBucket = tpmBuckets.computeIfAbsent(key, k -> createTpmBucket());

        rpmBucket.asBlocking().consume(1);
        tpmBucket.asBlocking().consume(tokens);
    }

    @Override
    public void release(String key, int permits) {
    }
}