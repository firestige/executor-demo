package xyz.firestige.infrastructure.redis.renewal.interval;

import org.junit.jupiter.api.Test;
import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class FixedIntervalStrategyTest {

    @Test
    void calculateInterval_returnsFixedValue() {
        Duration interval = Duration.ofMinutes(2);
        FixedIntervalStrategy strategy = new FixedIntervalStrategy(interval);

        RenewalContext ctx = new RenewalContext("test");
        assertEquals(interval, strategy.calculateInterval(ctx));

        ctx.incrementRenewalCount();
        assertEquals(interval, strategy.calculateInterval(ctx));
    }
}

