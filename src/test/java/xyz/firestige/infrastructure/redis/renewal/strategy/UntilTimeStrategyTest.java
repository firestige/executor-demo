package xyz.firestige.infrastructure.redis.renewal.strategy;

import org.junit.jupiter.api.Test;
import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class UntilTimeStrategyTest {

    @Test
    void calculateTtl_beforeEndTime_returnsBaseTtl() {
        Instant endTime = Instant.now().plusSeconds(300);
        Duration baseTtl = Duration.ofMinutes(2);
        UntilTimeStrategy strategy = new UntilTimeStrategy(endTime, baseTtl);

        RenewalContext ctx = new RenewalContext("test");
        Duration result = strategy.calculateTtl(ctx);

        assertTrue(result.getSeconds() > 0);
        assertTrue(result.getSeconds() <= baseTtl.getSeconds());
    }

    @Test
    void shouldContinue_beforeEndTime_true() {
        Instant endTime = Instant.now().plusSeconds(10);
        UntilTimeStrategy strategy = new UntilTimeStrategy(endTime, Duration.ofMinutes(1));

        RenewalContext ctx = new RenewalContext("test");
        assertTrue(strategy.shouldContinue(ctx));
    }

    @Test
    void shouldContinue_afterEndTime_false() throws Exception {
        Instant endTime = Instant.now().plusMillis(100);
        UntilTimeStrategy strategy = new UntilTimeStrategy(endTime, Duration.ofMinutes(1));

        RenewalContext ctx = new RenewalContext("test");
        Thread.sleep(150);
        assertFalse(strategy.shouldContinue(ctx));
    }
}

