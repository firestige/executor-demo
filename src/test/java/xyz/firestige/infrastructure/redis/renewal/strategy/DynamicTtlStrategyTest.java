package xyz.firestige.infrastructure.redis.renewal.strategy;

import org.junit.jupiter.api.Test;
import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class DynamicTtlStrategyTest {

    @Test
    void calculateTtl_usesProvidedFunction() {
        DynamicTtlStrategy strategy = new DynamicTtlStrategy(ctx ->
            Duration.ofSeconds(10 + ctx.getRenewalCount() * 5)
        );

        RenewalContext ctx = new RenewalContext("test");
        assertEquals(10, strategy.calculateTtl(ctx).getSeconds());

        ctx.incrementRenewalCount();
        assertEquals(15, strategy.calculateTtl(ctx).getSeconds());
    }

    @Test
    void shouldContinue_alwaysTrue() {
        DynamicTtlStrategy strategy = new DynamicTtlStrategy(ctx -> Duration.ofSeconds(10));
        assertTrue(strategy.shouldContinue(new RenewalContext("test")));
    }
}

