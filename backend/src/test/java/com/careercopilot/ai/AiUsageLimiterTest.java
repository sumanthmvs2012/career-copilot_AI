package com.careercopilot.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AiUsageLimiterTest {

    /** A clock the test can move. */
    private static final class MovableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-16T10:00:00Z"));

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }

        void advanceDays(long days) {
            now.updateAndGet(value -> value.plusSeconds(days * 86_400));
        }
    }

    @Test
    void allowsCallsUpToTheLimitThenThrows() {
        AiUsageLimiter limiter = new AiUsageLimiter(3, new MovableClock());

        limiter.checkAndRecord(1L);
        limiter.checkAndRecord(1L);
        limiter.checkAndRecord(1L);

        assertThatThrownBy(() -> limiter.checkAndRecord(1L))
                .isInstanceOf(AiLimitReachedException.class)
                .hasMessage("Daily AI limit reached.");
    }

    @Test
    void eachUserHasTheirOwnCounter() {
        AiUsageLimiter limiter = new AiUsageLimiter(1, new MovableClock());

        limiter.checkAndRecord(1L);

        assertThatCode(() -> limiter.checkAndRecord(2L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.checkAndRecord(1L)).isInstanceOf(AiLimitReachedException.class);
    }

    @Test
    void theCounterResetsOnTheNextDay() {
        MovableClock clock = new MovableClock();
        AiUsageLimiter limiter = new AiUsageLimiter(1, clock);
        limiter.checkAndRecord(1L);
        assertThatThrownBy(() -> limiter.checkAndRecord(1L)).isInstanceOf(AiLimitReachedException.class);

        clock.advanceDays(1);

        assertThatCode(() -> limiter.checkAndRecord(1L)).doesNotThrowAnyException();
    }

    @Test
    void aLimitBelowOneBlocksEveryCall() {
        AiUsageLimiter limiter = new AiUsageLimiter(0, new MovableClock());

        assertThatThrownBy(() -> limiter.checkAndRecord(1L)).isInstanceOf(AiLimitReachedException.class);
    }

    @Test
    void readsTheLimitFromTheProperties() {
        AiUsageLimiter limiter = new AiUsageLimiter(AiTestSupport.properties());

        for (int i = 0; i < 50; i++) {
            limiter.checkAndRecord(9L);
        }
        assertThatThrownBy(() -> limiter.checkAndRecord(9L)).isInstanceOf(AiLimitReachedException.class);
        assertThat(new AiLimitReachedException().getMessage()).isEqualTo("Daily AI limit reached.");
    }
}
