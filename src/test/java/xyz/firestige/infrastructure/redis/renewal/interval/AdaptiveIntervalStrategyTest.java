package xyz.firestige.infrastructure.redis.renewal.interval;

import org.junit.jupiter.api.Test;
import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveIntervalStrategyTest {

    @Test
    void calculateInterval_withLastTtl_returnsRatioOfTtl() {
        AdaptiveIntervalStrategy strategy = new AdaptiveIntervalStrategy(0.5);

        RenewalContext ctx = new RenewalContext("test");
        ctx.setLastCalculatedTtl(Duration.ofSeconds(100));

        Duration result = strategy.calculateInterval(ctx);
        assertEquals(50_000, result.toMillis());
    }

    @Test
    void calculateInterval_noLastTtl_returnsFallback() {
        Duration fallback = Duration.ofSeconds(15);
        AdaptiveIntervalStrategy strategy = new AdaptiveIntervalStrategy(0.5, fallback);

        RenewalContext ctx = new RenewalContext("test");
        assertEquals(fallback, strategy.calculateInterval(ctx));
    }
}

