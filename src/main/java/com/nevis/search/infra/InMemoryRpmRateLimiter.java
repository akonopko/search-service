package com.nevis.search.infra;

public class InMemoryRpmRateLimiter implements RateLimiter {

    private final InMemoryDualRateLimiter dualLimiter;

    public InMemoryRpmRateLimiter(int rpmLimit) {
        this.dualLimiter = new InMemoryDualRateLimiter(rpmLimit, Integer.MAX_VALUE);
    }

    @Override
    public void acquire(String key, int permits) {
        this.dualLimiter.acquire(key, 1);
    }

    @Override
    public void release(String key, int permits) {

    }
}