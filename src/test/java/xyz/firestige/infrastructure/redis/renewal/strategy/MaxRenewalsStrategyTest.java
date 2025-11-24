package xyz.firestige.infrastructure.redis.renewal.strategy;

import org.junit.jupiter.api.Test;
import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class MaxRenewalsStrategyTest {

    @Test
    void calculateTtl_returnsFixedTtl() {
        Duration ttl = Duration.ofMinutes(3);
        MaxRenewalsStrategy strategy = new MaxRenewalsStrategy(ttl, 5);

        RenewalContext ctx = new RenewalContext("test");
        assertEquals(ttl, strategy.calculateTtl(ctx));
    }

    @Test
    void shouldContinue_belowMax_true() {
        MaxRenewalsStrategy strategy = new MaxRenewalsStrategy(Duration.ofSeconds(10), 3);
        RenewalContext ctx = new RenewalContext("test");

        assertTrue(strategy.shouldContinue(ctx));
        ctx.incrementRenewalCount();
        ctx.incrementRenewalCount();
        assertTrue(strategy.shouldContinue(ctx));
    }

    @Test
    void shouldContinue_reachedMax_false() {
        MaxRenewalsStrategy strategy = new MaxRenewalsStrategy(Duration.ofSeconds(10), 3);
        RenewalContext ctx = new RenewalContext("test");

        ctx.incrementRenewalCount();
        ctx.incrementRenewalCount();
        ctx.incrementRenewalCount();
        assertFalse(strategy.shouldContinue(ctx));
    }
}

