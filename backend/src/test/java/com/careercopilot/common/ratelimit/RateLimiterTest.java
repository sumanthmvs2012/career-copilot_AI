package com.careercopilot.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-15T10:00:00Z"));

    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };

    private final RateLimiter limiter = new RateLimiter(new RateLimitProperties(3, Duration.ofMinutes(1)), clock);

    @Test
    void allowsUpToTheLimitThenRefuses() {
        assertThat(limiter.tryAcquire("ip|/auth/login")).isTrue();
        assertThat(limiter.tryAcquire("ip|/auth/login")).isTrue();
        assertThat(limiter.tryAcquire("ip|/auth/login")).isTrue();
        assertThat(limiter.tryAcquire("ip|/auth/login")).isFalse();
    }

    @Test
    void keysAreCountedSeparately() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("a|/auth/login");
        }
        assertThat(limiter.tryAcquire("a|/auth/login")).isFalse();
        assertThat(limiter.tryAcquire("b|/auth/login")).isTrue();
        assertThat(limiter.tryAcquire("a|/auth/signup")).isTrue();
    }

    @Test
    void allowsAgainOnceTheWindowHasPassed() {
        for (int i = 0; i < 4; i++) {
            limiter.tryAcquire("ip|/auth/login");
        }
        now.set(now.get().plus(Duration.ofMinutes(1)));
        assertThat(limiter.tryAcquire("ip|/auth/login")).isTrue();
    }
}
