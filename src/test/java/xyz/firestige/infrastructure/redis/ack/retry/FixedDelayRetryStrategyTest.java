package xyz.firestige.infrastructure.redis.ack.retry;

import org.junit.jupiter.api.Test;
import xyz.firestige.infrastructure.redis.ack.api.AckContext;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class FixedDelayRetryStrategyTest {

    @Test
    void nextDelay_withinMaxAttempts_returnsDelay() {
        FixedDelayRetryStrategy strategy = new FixedDelayRetryStrategy(3, Duration.ofSeconds(2));
        AckContext context = new AckContext("test");

        assertEquals(Duration.ofSeconds(2), strategy.nextDelay(1, null, context));
        assertEquals(Duration.ofSeconds(2), strategy.nextDelay(2, null, context));
    }

    @Test
    void nextDelay_exceededMaxAttempts_returnsNull() {
        FixedDelayRetryStrategy strategy = new FixedDelayRetryStrategy(3, Duration.ofSeconds(2));
        AckContext context = new AckContext("test");

        assertNull(strategy.nextDelay(3, null, context));
        assertNull(strategy.nextDelay(4, null, context));
    }

    @Test
    void constructor_invalidMaxAttempts_throwsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new FixedDelayRetryStrategy(0, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
            () -> new FixedDelayRetryStrategy(-1, Duration.ofSeconds(1)));
    }

    @Test
    void constructor_invalidDelay_throwsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new FixedDelayRetryStrategy(3, null));
        assertThrows(IllegalArgumentException.class,
            () -> new FixedDelayRetryStrategy(3, Duration.ofSeconds(-1)));
    }
}

