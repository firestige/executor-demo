package xyz.firestige.infrastructure.redis.renewal.strategy;

import org.junit.jupiter.api.Test;
import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class FixedTtlStrategyTest {

    @Test
    void calculateTtl_returnsFixedValue() {
        Duration ttl = Duration.ofMinutes(5);
        FixedTtlStrategy strategy = new FixedTtlStrategy(ttl);
        RenewalContext ctx = new RenewalContext("test");

        assertEquals(ttl, strategy.calculateTtl(ctx));
        ctx.incrementRenewalCount();
        assertEquals(ttl, strategy.calculateTtl(ctx));
    }

    @Test
    void shouldContinue_alwaysTrue() {
        FixedTtlStrategy strategy = new FixedTtlStrategy(Duration.ofSeconds(10));
        RenewalContext ctx = new RenewalContext("test");

        assertTrue(strategy.shouldContinue(ctx));
        ctx.incrementRenewalCount();
        assertTrue(strategy.shouldContinue(ctx));
    }
}

