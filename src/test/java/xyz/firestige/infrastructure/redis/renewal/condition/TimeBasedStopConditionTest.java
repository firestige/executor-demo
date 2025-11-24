package xyz.firestige.infrastructure.redis.renewal.condition;

import org.junit.jupiter.api.Test;
import xyz.firestige.infrastructure.redis.renewal.api.RenewalContext;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TimeBasedStopConditionTest {

    @Test
    void shouldStop_beforeEndTime_false() {
        Instant endTime = Instant.now().plusSeconds(10);
        TimeBasedStopCondition condition = new TimeBasedStopCondition(endTime);

        RenewalContext ctx = new RenewalContext("test");
        assertFalse(condition.shouldStop(ctx));
    }

    @Test
    void shouldStop_afterEndTime_true() throws Exception {
        Instant endTime = Instant.now().plusMillis(100);
        TimeBasedStopCondition condition = new TimeBasedStopCondition(endTime);

        RenewalContext ctx = new RenewalContext("test");
        Thread.sleep(150);
        assertTrue(condition.shouldStop(ctx));
    }
}

