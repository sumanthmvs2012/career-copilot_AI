package com.careercopilot.ai;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Per-user cap on AI provider calls per UTC day, kept in memory (it resets on restart and is per instance).
 * A limit below 1 blocks every call.
 */
@Component
public class AiUsageLimiter {

    private record Usage(LocalDate day, int calls) {
    }

    private final int limit;
    private final Clock clock;
    private final ConcurrentHashMap<Long, Usage> usage = new ConcurrentHashMap<>();
    private volatile LocalDate lastCleanup;

    @Autowired
    public AiUsageLimiter(AiProperties properties) {
        this(properties.dailyLimitPerUser(), Clock.systemUTC());
    }

    AiUsageLimiter(int limit, Clock clock) {
        this.limit = limit;
        this.clock = clock;
        this.lastCleanup = LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }

    /** Counts one call for the user, or throws AiLimitReachedException when today's cap is used up. */
    public void checkAndRecord(Long userId) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        cleanup(today);
        AtomicBoolean allowed = new AtomicBoolean();
        usage.compute(userId, (id, current) -> {
            int calls = current == null || !current.day().equals(today) ? 0 : current.calls();
            if (calls >= limit) {
                return new Usage(today, calls);
            }
            allowed.set(true);
            return new Usage(today, calls + 1);
        });
        if (!allowed.get()) {
            throw new AiLimitReachedException();
        }
    }

    /** Drops yesterday's counters once a day so the map does not grow forever. */
    private void cleanup(LocalDate today) {
        if (!today.equals(lastCleanup)) {
            lastCleanup = today;
            usage.values().removeIf(entry -> !entry.day().equals(today));
        }
    }
}
