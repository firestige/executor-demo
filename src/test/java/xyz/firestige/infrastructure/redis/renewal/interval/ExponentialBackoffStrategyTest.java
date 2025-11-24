package xyz.firestige.infrastructure.redis.renewal.interval;

import org.junit.jupiter.api.Test;
import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ExponentialBackoffStrategyTest {

    @Test
    void calculateInterval_increasesExponentially() {
        Duration initial = Duration.ofSeconds(1);
        Duration max = Duration.ofSeconds(60);
        ExponentialBackoffStrategy strategy = new ExponentialBackoffStrategy(initial, max, 2.0);

        RenewalContext ctx = new RenewalContext("test");
        assertEquals(1000, strategy.calculateInterval(ctx).toMillis());

        ctx.incrementRenewalCount();
        assertEquals(2000, strategy.calculateInterval(ctx).toMillis());

        ctx.incrementRenewalCount();
        assertEquals(4000, strategy.calculateInterval(ctx).toMillis());
    }

    @Test
    void calculateInterval_capsAtMax() {
        Duration initial = Duration.ofSeconds(10);
        Duration max = Duration.ofSeconds(30);
        ExponentialBackoffStrategy strategy = new ExponentialBackoffStrategy(initial, max, 2.0);

        RenewalContext ctx = new RenewalContext("test");
        for (int i = 0; i < 10; i++) ctx.incrementRenewalCount();

        long result = strategy.calculateInterval(ctx).toMillis();
        assertTrue(result <= max.toMillis());
    }
}

