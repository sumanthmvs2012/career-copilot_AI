package com.careercopilot.common.ratelimit;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Fixed-window counter per key (client IP + route). In-memory is enough for one backend instance;
 * move to a shared store (e.g. Redis) if the backend is ever scaled out.
 */
@Component
public class RateLimiter {

    // Bounds memory if many distinct IPs hit the endpoints; stale windows are dropped first.
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final RateLimitProperties properties;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Returns true if the request is allowed, false if the key has used up its window. */
    public boolean tryAcquire(String key) {
        Instant now = clock.instant();
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.values().removeIf(w -> w.isExpired(now));
        }
        Window window = windows.compute(key, (k, existing) ->
                existing == null || existing.isExpired(now)
                        ? new Window(now.plus(properties.window()), 1)
                        : existing.increment());
        return window.count() <= properties.maxRequests();
    }

    /** For tests: start every test with empty counters. */
    public void reset() {
        windows.clear();
    }

    private record Window(Instant resetAt, int count) {
        boolean isExpired(Instant now) {
            return !now.isBefore(resetAt);
        }

        Window increment() {
            return new Window(resetAt, count + 1);
        }
    }
}
