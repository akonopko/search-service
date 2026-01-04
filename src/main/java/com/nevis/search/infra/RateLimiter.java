package com.nevis.search.infra;

public interface RateLimiter {

    void acquire(String key, int permits);
    void release(String key, int permits);

    default <T> T execute(String key, int permits, java.util.function.Supplier<T> task) {
        try {
            acquire(key, permits);
            return task.get();
        } finally {
            release(key, permits);
        }
    }
}