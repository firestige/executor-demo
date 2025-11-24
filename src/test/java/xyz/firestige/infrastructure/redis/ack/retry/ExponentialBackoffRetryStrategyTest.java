package xyz.firestige.infrastructure.redis.ack.retry;

import org.junit.jupiter.api.Test;
import xyz.firestige.infrastructure.redis.ack.api.AckContext;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ExponentialBackoffRetryStrategyTest {

    @Test
    void increasesDelayUntilMax() {
        ExponentialBackoffRetryStrategy strategy = new ExponentialBackoffRetryStrategy(
            5, Duration.ofMillis(100), 2.0, Duration.ofMillis(500)
        );
        AckContext ctx = new AckContext("t1");
        assertEquals(Duration.ofMillis(100), strategy.nextDelay(1, null, ctx));
        assertEquals(Duration.ofMillis(200), strategy.nextDelay(2, null, ctx));
        assertEquals(Duration.ofMillis(400), strategy.nextDelay(3, null, ctx));
        assertEquals(Duration.ofMillis(500), strategy.nextDelay(4, null, ctx)); // capped
        assertNull(strategy.nextDelay(5, null, ctx)); // stop
    }

    @Test
    void invalidParamsThrow() {
        assertThrows(IllegalArgumentException.class, () -> new ExponentialBackoffRetryStrategy(0, Duration.ofMillis(100), 2.0, null));
        assertThrows(IllegalArgumentException.class, () -> new ExponentialBackoffRetryStrategy(3, Duration.ZERO, 2.0, null));
        assertThrows(IllegalArgumentException.class, () -> new ExponentialBackoffRetryStrategy(3, Duration.ofMillis(100), 0.5, null));
    }
}

