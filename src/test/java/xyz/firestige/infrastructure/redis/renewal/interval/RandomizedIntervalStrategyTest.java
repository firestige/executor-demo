package xyz.firestige.infrastructure.redis.renewal.interval;

import org.junit.jupiter.api.Test;
import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RandomizedIntervalStrategyTest {

    @Test
    void calculateInterval_withinJitterRange() {
        Duration base = Duration.ofSeconds(10);
        Duration jitter = Duration.ofSeconds(2);
        RandomizedIntervalStrategy strategy = new RandomizedIntervalStrategy(base, jitter);

        RenewalContext ctx = new RenewalContext("test");

        for (int i = 0; i < 10; i++) {
            Duration result = strategy.calculateInterval(ctx);
            assertTrue(result.toMillis() >= 8000 - 2000);
            assertTrue(result.toMillis() <= 12000);
        }
    }

    @Test
    void calculateInterval_neverBelowMinimum() {
        Duration base = Duration.ofMillis(5);
        Duration jitter = Duration.ofMillis(20);
        RandomizedIntervalStrategy strategy = new RandomizedIntervalStrategy(base, jitter);

        RenewalContext ctx = new RenewalContext("test");
        Duration result = strategy.calculateInterval(ctx);
        assertTrue(result.toMillis() >= 10);
    }
}

